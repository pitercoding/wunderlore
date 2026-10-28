package com.wunderlore.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import com.wunderlore.lore.LoreDataService;

@ExtendWith(MockitoExtension.class)
class WhoSaidItServiceTest {

        private static final int PAGE_SIZE = 100;

        private static final ExternalCharacter FRODO = new ExternalCharacter("frodo", "Frodo Baggins");
        private static final ExternalCharacter SAM = new ExternalCharacter("sam", "Samwise Gamgee");
        private static final ExternalCharacter GANDALF = new ExternalCharacter("gandalf", "Gandalf");
        private static final ExternalCharacter ARAGORN = new ExternalCharacter("aragorn", "Aragorn");
        private static final ExternalCharacter GOLLUM = new ExternalCharacter("gollum", "Gollum");

        @Mock
        private TheOneApiClient theOneApiClient;

        @Test
        void shouldGenerateRoundWithQuoteAndFourOptions() {
                stubSinglePage(
                                List.of(quote("q1",
                                                "All we have to decide is what to do with the time that is given us.",
                                                "gandalf")),
                                List.of(FRODO, SAM, GANDALF, ARAGORN, GOLLUM));

                GameRound round = newService(new Random(42)).generateRound();

                assertThat(round.quote())
                                .isEqualTo("All we have to decide is what to do with the time that is given us.");
                assertThat(round.options()).hasSize(4);
                assertThat(round.correctCharacterId()).isEqualTo("gandalf");
        }

        @Test
        void shouldIncludeCorrectCharacterAmongOptions() {
                stubSinglePage(
                                List.of(quote("q1", "Po-tay-toes!", "sam")),
                                List.of(FRODO, SAM, GANDALF, ARAGORN, GOLLUM));

                GameRound round = newService(new Random(42)).generateRound();

                assertThat(round.options())
                                .contains(new GameRound.CharacterOption("sam", "Samwise Gamgee"));
        }

        @Test
        void shouldReturnOptionsWithDistinctIds() {
                stubSinglePage(
                                List.of(quote("q1", "My precious.", "gollum")),
                                List.of(FRODO, SAM, GANDALF, ARAGORN, GOLLUM));

                WhoSaidItService service = newService(new Random(42));

                for (int i = 0; i < 20; i++) {
                        GameRound round = service.generateRound();

                        assertThat(optionIds(round)).hasSize(4);
                }
        }

        @Test
        void shouldReturnThreeWrongOptionsThatAreNotTheQuoteAuthor() {
                stubSinglePage(
                                List.of(quote("q1", "I am no man.", "aragorn")),
                                List.of(FRODO, SAM, GANDALF, ARAGORN, GOLLUM));

                GameRound round = newService(new Random(42)).generateRound();

                List<GameRound.CharacterOption> wrongOptions = round.options().stream()
                                .filter(option -> !option.id().equals(round.correctCharacterId()))
                                .toList();

                assertThat(wrongOptions)
                                .hasSize(3)
                                .noneMatch(option -> option.id().equals("aragorn"));
        }

        @Test
        void shouldDiscardInvalidQuotesAndCharacters() {
                stubSinglePage(
                                List.of(
                                                quote("q1", "", "frodo"),
                                                quote("q2", "   ", "frodo"),
                                                quote("q3", null, "frodo"),
                                                quote("q4", "No author", null),
                                                quote("q5", "Blank author", " "),
                                                quote("q6", "Unknown author", "sauron"),
                                                quote("q7", "Nameless author", "nameless"),
                                                quote("q8", "I will take the Ring to Mordor.", "frodo")),
                                List.of(
                                                FRODO, SAM, GANDALF, ARAGORN,
                                                new ExternalCharacter("nameless", " "),
                                                new ExternalCharacter(null, "No id"),
                                                new ExternalCharacter("", "Blank id")));

                WhoSaidItService service = newService(new Random(42));

                for (int i = 0; i < 20; i++) {
                        GameRound round = service.generateRound();

                        assertThat(round.quote()).isEqualTo("I will take the Ring to Mordor.");
                        assertThat(round.correctCharacterId()).isEqualTo("frodo");
                        assertThat(optionIds(round))
                                        .containsExactlyInAnyOrder("frodo", "sam", "gandalf", "aragorn");
                }
        }

        @Test
        void shouldFailWhenThereAreNoValidQuotes() {
                stubSinglePage(
                                List.of(quote("q1", "", "frodo"), quote("q2", "Unknown author", "sauron")),
                                List.of(FRODO, SAM, GANDALF, ARAGORN));

                WhoSaidItService service = newService(new Random(42));

                assertThatThrownBy(service::generateRound)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("No valid quotes available to start the game");
        }

        @Test
        void shouldFailWhenThereAreFewerThanFourValidCharacters() {
                stubSinglePage(
                                List.of(quote("q1", "Fly, you fools!", "gandalf")),
                                List.of(FRODO, SAM, GANDALF, new ExternalCharacter("nameless", "")));

                WhoSaidItService service = newService(new Random(42));

                assertThatThrownBy(service::generateRound)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("At least four distinct characters are required to play");
        }

        @Test
        void shouldReuseCachedDataForSubsequentRounds() {
                stubSinglePage(
                                List.of(quote("q1", "Po-tay-toes!", "sam")),
                                List.of(FRODO, SAM, GANDALF, ARAGORN));

                WhoSaidItService service = newService(new Random(42));

                for (int i = 0; i < 5; i++) {
                        service.generateRound();
                }

                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(1)).getCharacters(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldLoadEveryPageOfQuotesAndCharacters() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenReturn(quotePage(List.of(quote("q1", "", "frodo")), 1, 3));
                when(theOneApiClient.getQuotes(2, PAGE_SIZE))
                                .thenReturn(quotePage(List.of(quote("q2", "Unknown author", "sauron")), 2, 3));
                when(theOneApiClient.getQuotes(3, PAGE_SIZE))
                                .thenReturn(quotePage(List.of(quote("q3", "My precious.", "gollum")), 3, 3));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE))
                                .thenReturn(characterPage(List.of(FRODO, SAM), 1, 2));
                when(theOneApiClient.getCharacters(2, PAGE_SIZE))
                                .thenReturn(characterPage(List.of(GANDALF, GOLLUM), 2, 2));

                GameRound round = newService(new Random(42)).generateRound();

                // The only valid quote is on the last page and its author on the last character
                // page
                assertThat(round.quote()).isEqualTo("My precious.");
                assertThat(round.correctCharacterId()).isEqualTo("gollum");
                assertThat(optionIds(round))
                                .containsExactlyInAnyOrder("frodo", "sam", "gandalf", "gollum");

                verify(theOneApiClient).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient).getQuotes(2, PAGE_SIZE);
                verify(theOneApiClient).getQuotes(3, PAGE_SIZE);
                verify(theOneApiClient).getCharacters(1, PAGE_SIZE);
                verify(theOneApiClient).getCharacters(2, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @Test
        void shouldSelectQuoteUsingInjectedRandomGenerator() {
                stubSinglePage(
                                List.of(
                                                quote("q1", "Po-tay-toes!", "sam"),
                                                quote("q2", "Fly, you fools!", "gandalf"),
                                                quote("q3", "My precious.", "gollum"),
                                                quote("q4", "I am no man.", "aragorn"),
                                                quote("q5", "I will take the Ring to Mordor.", "frodo")),
                                List.of(FRODO, SAM, GANDALF, ARAGORN, GOLLUM));

                RandomGenerator random = mock(RandomGenerator.class);
                when(random.nextInt(anyInt())).thenReturn(1);

                GameRound round = newService(random).generateRound();

                assertThat(round.quote()).isEqualTo("Fly, you fools!");
                assertThat(round.correctCharacterId()).isEqualTo("gandalf");
                verify(random).nextInt(5);
        }

        @Test
        void shouldProduceSameRoundsWithSameSeed() {
                stubSinglePage(
                                List.of(
                                                quote("q1", "Po-tay-toes!", "sam"),
                                                quote("q2", "Fly, you fools!", "gandalf"),
                                                quote("q3", "My precious.", "gollum"),
                                                quote("q4", "I am no man.", "aragorn")),
                                List.of(FRODO, SAM, GANDALF, ARAGORN, GOLLUM));

                WhoSaidItService firstService = newService(new Random(7));
                WhoSaidItService secondService = newService(new Random(7));

                for (int i = 0; i < 10; i++) {
                        assertThat(secondService.generateRound()).isEqualTo(firstService.generateRound());
                }
        }

        @Test
        void shouldNotCacheDataWhenApiFailsDuringLoading() {
                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenReturn(quotePage(List.of(quote("q1", "Po-tay-toes!", "sam")), 1, 1));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE))
                                .thenThrow(new RestClientException("The One API unavailable"))
                                .thenReturn(characterPage(List.of(FRODO, SAM, GANDALF, ARAGORN), 1, 1));

                WhoSaidItService service = newService(new Random(42));

                assertThatThrownBy(service::generateRound)
                                .isInstanceOf(RestClientException.class);

                GameRound round = service.generateRound();

                assertThat(round.correctCharacterId()).isEqualTo("sam");
                verify(theOneApiClient, times(2)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(2)).getCharacters(1, PAGE_SIZE);
        }

        private WhoSaidItService newService(RandomGenerator random) {
                // No failure cooldown, so retries are not delayed; the cooldown is covered in LoreDataServiceTest.
                LoreDataService loreDataService = new LoreDataService(
                                theOneApiClient, Clock.systemUTC(), Duration.ZERO, 100);

                return new WhoSaidItService(loreDataService, random);
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

        private static Set<String> optionIds(GameRound round) {
                return round.options().stream()
                                .map(GameRound.CharacterOption::id)
                                .collect(Collectors.toSet());
        }
}
