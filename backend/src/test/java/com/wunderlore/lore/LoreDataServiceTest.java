package com.wunderlore.lore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;

@ExtendWith(MockitoExtension.class)
class LoreDataServiceTest {

        private static final int PAGE_SIZE = 100;
        private static final Duration COOLDOWN = Duration.ofSeconds(30);
        private static final int MAX_PAGES = 3;

        private static final ExternalCharacter FRODO = new ExternalCharacter("frodo", "Frodo Baggins");
        private static final ExternalCharacter SAM = new ExternalCharacter("sam", "Samwise Gamgee");
        private static final ExternalCharacter GANDALF = new ExternalCharacter("gandalf", "Gandalf");

        @Mock
        private TheOneApiClient theOneApiClient;

        private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));

        @Test
        void shouldLoadEveryPageInUpstreamOrder() {
                ExternalQuote q1 = quote("q1", "Po-tay-toes!", "sam");
                ExternalQuote q2 = quote("q2", "Fly, you fools!", "gandalf");
                ExternalQuote q3 = quote("q3", "I will take the Ring to Mordor.", "frodo");

                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(List.of(q1), 1, 3));
                when(theOneApiClient.getQuotes(2, PAGE_SIZE)).thenReturn(quotePage(List.of(q2), 2, 3));
                when(theOneApiClient.getQuotes(3, PAGE_SIZE)).thenReturn(quotePage(List.of(q3), 3, 3));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(FRODO, SAM), 1, 2));
                when(theOneApiClient.getCharacters(2, PAGE_SIZE)).thenReturn(characterPage(List.of(GANDALF), 2, 2));

                LoreData data = newService().getData();

                assertThat(data.quotes()).containsExactly(q1, q2, q3);
                assertThat(data.charactersById()).containsOnlyKeys("frodo", "sam", "gandalf");

                verify(theOneApiClient).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient).getQuotes(2, PAGE_SIZE);
                verify(theOneApiClient).getQuotes(3, PAGE_SIZE);
                verify(theOneApiClient).getCharacters(1, PAGE_SIZE);
                verify(theOneApiClient).getCharacters(2, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldIndexValidCharactersAndKeepAllQuotesWhileFilteringAttributedOnes() {
                ExternalQuote valid = quote("q8", "I will take the Ring to Mordor.", "frodo");
                List<ExternalQuote> quotes = List.of(
                                quote("q1", "", "frodo"),
                                quote("q2", "   ", "frodo"),
                                quote("q3", null, "frodo"),
                                quote("q4", "No author", null),
                                quote("q5", "Blank author", " "),
                                quote("q6", "Unknown author", "sauron"),
                                quote("q7", "Nameless author", "nameless"),
                                valid);

                stubSinglePage(quotes, List.of(
                                FRODO, SAM,
                                new ExternalCharacter("nameless", " "),
                                new ExternalCharacter("unnamed", null),
                                new ExternalCharacter(null, "No id"),
                                new ExternalCharacter("", "Blank id")));

                LoreData data = newService().getData();

                assertThat(data.quotes()).containsExactlyElementsOf(quotes);
                assertThat(data.charactersById()).containsOnlyKeys("frodo", "sam");
                assertThat(data.charactersById().get("frodo")).isEqualTo(FRODO);
                assertThat(data.attributedQuotes()).containsExactly(valid);
        }

        @Test
        void shouldReturnImmutableCollections() {
                stubSinglePage(
                                List.of(quote("q1", "Po-tay-toes!", "sam")),
                                List.of(SAM));

                LoreData data = newService().getData();

                assertThatThrownBy(() -> data.quotes().clear())
                                .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> data.charactersById().clear())
                                .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> data.attributedQuotes().clear())
                                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldReuseCachedDatasetForSubsequentCalls() {
                stubSinglePage(
                                List.of(quote("q1", "Po-tay-toes!", "sam")),
                                List.of(SAM));

                LoreDataService service = newService();

                LoreData first = service.getData();

                for (int i = 0; i < 4; i++) {
                        assertThat(service.getData()).isSameAs(first);
                }

                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(1)).getCharacters(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldRetryFullLoadAfterPartialFailureWithoutCaching() {
                ExternalQuote q1 = quote("q1", "Po-tay-toes!", "sam");
                ExternalQuote q2 = quote("q2", "Fly, you fools!", "gandalf");

                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(List.of(q1), 1, 2));
                when(theOneApiClient.getQuotes(2, PAGE_SIZE))
                                .thenThrow(new RestClientException("The One API unavailable"))
                                .thenReturn(quotePage(List.of(q2), 2, 2));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE))
                                .thenReturn(characterPage(List.of(SAM, GANDALF), 1, 1));

                LoreDataService service = newService();

                assertThatThrownBy(service::getData)
                                .isInstanceOf(RestClientException.class);

                clock.advance(COOLDOWN);

                LoreData data = service.getData();

                // The retry starts over from page 1 instead of resuming from a partial dataset.
                assertThat(data.quotes()).containsExactly(q1, q2);
                verify(theOneApiClient, times(2)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(2)).getQuotes(2, PAGE_SIZE);
                verify(theOneApiClient, times(1)).getCharacters(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldReturnDatasetWhenNoQuoteIsAttributed() {
                ExternalQuote unknownAuthor = quote("q1", "Unknown author", "sauron");

                stubSinglePage(List.of(unknownAuthor), List.of(FRODO, SAM));

                LoreData data = newService().getData();

                assertThat(data.attributedQuotes()).isEmpty();
                assertThat(data.quotes()).containsExactly(unknownAuthor);
                assertThat(data.charactersById()).containsOnlyKeys("frodo", "sam");
        }

        @Test
        void shouldFailWhenQuoteBodyIsNull() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(null);

                assertThatThrownBy(newService()::getData)
                                .isInstanceOf(LoreDataUnavailableException.class);
        }

        @Test
        void shouldFailWhenQuoteDocsAreNull() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenReturn(new ExternalQuoteResponse(null, 0, PAGE_SIZE, null, 1, 1));

                assertThatThrownBy(newService()::getData)
                                .isInstanceOf(LoreDataUnavailableException.class);
        }

        @Test
        void shouldFailWhenLaterQuotePageDocsAreNull() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, 2));
                when(theOneApiClient.getQuotes(2, PAGE_SIZE))
                                .thenReturn(new ExternalQuoteResponse(null, 0, PAGE_SIZE, null, 2, 2));

                assertThatThrownBy(newService()::getData)
                                .isInstanceOf(LoreDataUnavailableException.class);
        }

        @Test
        void shouldFailWhenCharacterBodyIsNull() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(List.of(), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(null);

                assertThatThrownBy(newService()::getData)
                                .isInstanceOf(LoreDataUnavailableException.class);
        }

        @Test
        void shouldFailWhenCharacterDocsAreNull() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(List.of(), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE))
                                .thenReturn(new ExternalCharacterResponse(null, 0, PAGE_SIZE, null, 1, 1));

                assertThatThrownBy(newService()::getData)
                                .isInstanceOf(LoreDataUnavailableException.class);
        }

        @Test
        void shouldNotCacheAfterNullDocs() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenReturn(new ExternalQuoteResponse(null, 0, PAGE_SIZE, null, 1, 1))
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(SAM), 1, 1));

                LoreDataService service = newService();

                assertThatThrownBy(service::getData)
                                .isInstanceOf(LoreDataUnavailableException.class);

                clock.advance(COOLDOWN);

                assertThat(service.getData().attributedQuotes()).hasSize(1);
                verify(theOneApiClient, times(2)).getQuotes(1, PAGE_SIZE);
        }

        @Test
        void shouldLoadOnlyOnceWhenCalledConcurrently() throws Exception {
                CountDownLatch loadStarted = new CountDownLatch(1);
                CountDownLatch releaseLoad = new CountDownLatch(1);

                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenAnswer(invocation -> {
                        loadStarted.countDown();
                        releaseLoad.await(5, TimeUnit.SECONDS);
                        return quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, 1);
                });
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(SAM), 1, 1));

                LoreDataService service = newService();
                FutureTask<LoreData> first = new FutureTask<>(service::getData);
                FutureTask<LoreData> second = new FutureTask<>(service::getData);
                Thread firstThread = new Thread(first);
                Thread secondThread = new Thread(second);

                try {
                        firstThread.start();
                        assertThat(loadStarted.await(5, TimeUnit.SECONDS)).isTrue();

                        // The second caller arrives while the first one is still loading
                        // and must wait on the lock instead of starting its own load.
                        secondThread.start();
                        awaitBlocked(secondThread);
                        releaseLoad.countDown();

                        LoreData firstData = first.get(5, TimeUnit.SECONDS);
                        LoreData secondData = second.get(5, TimeUnit.SECONDS);

                        assertThat(secondData).isSameAs(firstData);
                } finally {
                        releaseLoad.countDown();
                        firstThread.interrupt();
                        secondThread.interrupt();
                }

                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(1)).getCharacters(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldFailFastDuringCooldownWithoutCallingApi() {
                RestClientException upstreamFailure = new RestClientException("The One API unavailable");

                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenThrow(upstreamFailure);

                LoreDataService service = newService();

                assertThatThrownBy(service::getData).isSameAs(upstreamFailure);

                clock.advance(COOLDOWN.minusNanos(1));

                assertThatThrownBy(service::getData)
                                .isInstanceOf(LoreDataUnavailableException.class)
                                .hasCause(upstreamFailure);
                assertThatThrownBy(service::getData)
                                .isInstanceOf(LoreDataUnavailableException.class)
                                .hasCause(upstreamFailure);

                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldThrowNewExceptionForEachCallDuringCooldown() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(null);

                LoreDataService service = newService();

                LoreDataUnavailableException original = catchUnavailable(service);
                LoreDataUnavailableException first = catchUnavailable(service);
                LoreDataUnavailableException second = catchUnavailable(service);

                assertThat(first).isNotSameAs(original).isNotSameAs(second).hasCause(original);
                assertThat(second).hasCause(original);
                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldRetryOnceCooldownExpires() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenThrow(new RestClientException("The One API unavailable"))
                                .thenThrow(new RestClientException("Still unavailable"))
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(SAM), 1, 1));

                LoreDataService service = newService();

                assertThatThrownBy(service::getData).isInstanceOf(RestClientException.class);

                clock.advance(COOLDOWN);

                // The retry fails too, so a new cooldown starts from this failure.
                assertThatThrownBy(service::getData)
                                .isInstanceOf(RestClientException.class)
                                .hasMessage("Still unavailable");

                clock.advance(COOLDOWN.minusNanos(1));

                assertThatThrownBy(service::getData).isInstanceOf(LoreDataUnavailableException.class);

                clock.advance(Duration.ofNanos(1));

                assertThat(service.getData().attributedQuotes()).hasSize(1);
                verify(theOneApiClient, times(3)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(1)).getCharacters(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldClearFailureStateAfterSuccessfulLoad() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenThrow(new RestClientException("The One API unavailable"))
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(SAM), 1, 1));

                LoreDataService service = newService();

                assertThatThrownBy(service::getData).isInstanceOf(RestClientException.class);

                clock.advance(COOLDOWN);

                LoreData data = service.getData();

                // Rewinding into the old cooldown window must not resurrect the failure.
                clock.advance(COOLDOWN.negated());

                assertThat(service.getData()).isSameAs(data);
                verify(theOneApiClient, times(2)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(1)).getCharacters(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldNotStartCooldownWhenTokenIsMissing() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenThrow(new TheOneApiNotConfiguredException())
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(SAM), 1, 1));

                LoreDataService service = newService();

                assertThatThrownBy(service::getData).isInstanceOf(TheOneApiNotConfiguredException.class);

                // Configuration errors are not upstream failures, so no cooldown applies.
                assertThat(service.getData().attributedQuotes()).hasSize(1);
        }

        @Test
        void shouldNotStartCooldownForProgrammingErrors() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenThrow(new IllegalStateException("bug"))
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(SAM), 1, 1));

                LoreDataService service = newService();

                assertThatThrownBy(service::getData).isInstanceOf(IllegalStateException.class);
                assertThat(service.getData().attributedQuotes()).hasSize(1);
        }

        @Test
        void shouldLoadWhenPageCountEqualsLimit() {
                ExternalQuote q1 = quote("q1", "Po-tay-toes!", "sam");
                ExternalQuote q2 = quote("q2", "Fly, you fools!", "gandalf");
                ExternalQuote q3 = quote("q3", "I will take the Ring to Mordor.", "frodo");

                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(List.of(q1), 1, MAX_PAGES));
                when(theOneApiClient.getQuotes(2, PAGE_SIZE)).thenReturn(quotePage(List.of(q2), 2, MAX_PAGES));
                when(theOneApiClient.getQuotes(3, PAGE_SIZE)).thenReturn(quotePage(List.of(q3), 3, MAX_PAGES));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE))
                                .thenReturn(characterPage(List.of(FRODO, SAM, GANDALF), 1, 1));

                assertThat(newService().getData().quotes()).containsExactly(q1, q2, q3);
        }

        @Test
        void shouldFailWithoutFetchingMoreWhenQuotePagesExceedLimit() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, MAX_PAGES + 1));

                assertThatThrownBy(newService()::getData)
                                .isInstanceOf(LoreDataUnavailableException.class)
                                .hasMessageContaining("above the limit");

                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldNotCachePartialDataWhenCharacterPagesExceedLimit() {
                ExternalQuote q1 = quote("q1", "Po-tay-toes!", "sam");

                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(List.of(q1), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE))
                                .thenReturn(characterPage(List.of(SAM), 1, Integer.MAX_VALUE))
                                .thenReturn(characterPage(List.of(SAM), 1, 1));

                LoreDataService service = newService();

                assertThatThrownBy(service::getData)
                                .isInstanceOf(LoreDataUnavailableException.class)
                                .hasMessageContaining("above the limit");
                assertThatThrownBy(service::getData).isInstanceOf(LoreDataUnavailableException.class);

                clock.advance(COOLDOWN);

                // The quotes loaded before the failure were discarded, so the retry starts over.
                assertThat(service.getData().quotes()).containsExactly(q1);
                verify(theOneApiClient, times(2)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(2)).getCharacters(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldFailWhenPageCountIsNegative() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, -1));

                assertThatThrownBy(newService()::getData)
                                .isInstanceOf(LoreDataUnavailableException.class)
                                .hasMessageContaining("invalid quote page count");
        }

        @Test
        void shouldFailWhenNonEmptyPageReportsZeroPages() {
                // A missing "pages" field deserializes to 0; trusting it would cache only page 1.
                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(List.of(), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(SAM), 1, 0));

                assertThatThrownBy(newService()::getData)
                                .isInstanceOf(LoreDataUnavailableException.class)
                                .hasMessageContaining("invalid character page count");
        }

        @Test
        void shouldAcceptZeroPagesForEmptyDataset() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(List.of(), 1, 0));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(List.of(SAM), 1, 1));

                LoreData data = newService().getData();

                assertThat(data.quotes()).isEmpty();
                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
        }

        @Test
        void shouldRejectInvalidConfiguration() {
                assertThatThrownBy(() -> new LoreDataService(theOneApiClient, clock, Duration.ofSeconds(-1), MAX_PAGES))
                                .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> new LoreDataService(theOneApiClient, clock, COOLDOWN, 0))
                                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldFailFastForCallersQueuedBehindFailingLoad() throws Exception {
                CountDownLatch loadStarted = new CountDownLatch(1);
                CountDownLatch releaseLoad = new CountDownLatch(1);

                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenAnswer(invocation -> {
                        loadStarted.countDown();
                        releaseLoad.await(5, TimeUnit.SECONDS);
                        throw new RestClientException("The One API unavailable");
                });

                LoreDataService service = newService();
                FutureTask<LoreData> first = new FutureTask<>(service::getData);
                FutureTask<LoreData> second = new FutureTask<>(service::getData);
                Thread firstThread = new Thread(first);
                Thread secondThread = new Thread(second);

                try {
                        firstThread.start();
                        assertThat(loadStarted.await(5, TimeUnit.SECONDS)).isTrue();

                        // The second caller waits on the lock while the first load is failing
                        // and must not start another full load once it gets the lock.
                        secondThread.start();
                        awaitBlocked(secondThread);
                        releaseLoad.countDown();

                        assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS))
                                        .hasCauseInstanceOf(RestClientException.class);
                        assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                                        .hasCauseInstanceOf(LoreDataUnavailableException.class);
                } finally {
                        releaseLoad.countDown();
                        firstThread.interrupt();
                        secondThread.interrupt();
                }

                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        private static LoreDataUnavailableException catchUnavailable(LoreDataService service) {
                try {
                        service.getData();
                } catch (LoreDataUnavailableException exception) {
                        return exception;
                }

                throw new AssertionError("Expected LoreDataUnavailableException");
        }

        // Spins instead of sleeping until the thread is parked on the service monitor.
        private static void awaitBlocked(Thread thread) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

                while (thread.getState() != Thread.State.BLOCKED) {
                        assertThat(System.nanoTime()).as("thread blocked on lock").isLessThan(deadline);
                        Thread.yield();
                }
        }

        private LoreDataService newService() {
                return new LoreDataService(theOneApiClient, clock, COOLDOWN, MAX_PAGES);
        }

        private void stubSinglePage(List<ExternalQuote> quotes, List<ExternalCharacter> characters) {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE)).thenReturn(quotePage(quotes, 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE)).thenReturn(characterPage(characters, 1, 1));
        }

        private static ExternalQuote quote(String id, String dialog, String characterId) {
                return new ExternalQuote(id, dialog, "movie-1", characterId, id);
        }

        private static ExternalQuoteResponse quotePage(List<ExternalQuote> quotes, int page, int pages) {
                return new ExternalQuoteResponse(quotes, quotes.size(), PAGE_SIZE, null, page, pages);
        }

        private static ExternalCharacterResponse characterPage(
                        List<ExternalCharacter> characters, int page, int pages) {
                return new ExternalCharacterResponse(characters, characters.size(), PAGE_SIZE, null, page, pages);
        }

        private static final class MutableClock extends Clock {

                private final AtomicReference<Instant> currentInstant;

                private MutableClock(Instant initialInstant) {
                        this.currentInstant = new AtomicReference<>(initialInstant);
                }

                void advance(Duration duration) {
                        currentInstant.updateAndGet(instant -> instant.plus(duration));
                }

                @Override
                public ZoneId getZone() {
                        return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                        return this;
                }

                @Override
                public Instant instant() {
                        return currentInstant.get();
                }
        }
}
