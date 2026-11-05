package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.lore.LoreCharacter;
import com.wunderlore.lore.LoreData;
import com.wunderlore.lore.LoreDataService;
import com.wunderlore.lore.LoreDataUnavailableException;
import com.wunderlore.lore.LoreQuote;
import com.wunderlore.support.LogCapture;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalQuoteController.class)
class ExternalQuoteControllerTest {

        private static final String UNAVAILABLE_DETAIL = "Unable to load quotes right now";

        private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

        @TestConfiguration
        static class ClockConfiguration {

                @Bean
                Clock clock() {
                        return Clock.fixed(NOW, ZoneOffset.UTC);
                }
        }

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private LoreDataService loreDataService;

        @Test
        void shouldReturnQuotesUsingDefaultPagination() throws Exception {
                LoreQuote quote = new LoreQuote(
                                "quote-123",
                                "Example dialogue",
                                "movie-123",
                                "character-123",
                                "quote-123");

                when(loreDataService.getData()).thenReturn(loreData(List.of(quote)));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(1))
                                .andExpect(jsonPath("$.docs[0]._id").value("quote-123"))
                                .andExpect(jsonPath("$.docs[0].dialog").value("Example dialogue"))
                                .andExpect(jsonPath("$.docs[0].movie").value("movie-123"))
                                .andExpect(jsonPath("$.docs[0].character").value("character-123"))
                                .andExpect(jsonPath("$.docs[0].id").value("quote-123"))
                                .andExpect(jsonPath("$.docs[0].name").doesNotExist())
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.limit").value(20))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.pages").value(1))
                                .andExpect(jsonPath("$.offset").doesNotExist());

                verify(loreDataService).getData();
        }

        @Test
        void shouldResolveCharacterNameFromLoadedLoreData() throws Exception {
                LoreQuote quote = new LoreQuote("q1", "My precious", "movie-1", "gollum", "q1");

                when(loreDataService.getData()).thenReturn(new LoreData(
                                List.of(quote),
                                Map.of("gollum", new LoreCharacter("gollum", "Gollum")),
                                List.of(quote)));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs[0]._id").value("q1"))
                                .andExpect(jsonPath("$.docs[0].dialog").value("My precious"))
                                .andExpect(jsonPath("$.docs[0].movie").value("movie-1"))
                                .andExpect(jsonPath("$.docs[0].character").value("gollum"))
                                .andExpect(jsonPath("$.docs[0].id").value("q1"))
                                .andExpect(jsonPath("$.docs[0].characterName").value("Gollum"));
        }

        @Test
        void shouldMapEveryLoreQuoteFieldToItsCatalogField() throws Exception {
                // Distinct values prove each field lands where the catalog contract says it does.
                LoreQuote quote = new LoreQuote("mongo-id", "Dialog", "movie-id", "gollum", "alternate-id");

                when(loreDataService.getData()).thenReturn(new LoreData(
                                List.of(quote),
                                Map.of("gollum", new LoreCharacter("gollum", "Gollum")),
                                List.of(quote)));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs[0]._id").value("mongo-id"))
                                .andExpect(jsonPath("$.docs[0].dialog").value("Dialog"))
                                .andExpect(jsonPath("$.docs[0].movie").value("movie-id"))
                                .andExpect(jsonPath("$.docs[0].character").value("gollum"))
                                .andExpect(jsonPath("$.docs[0].id").value("alternate-id"))
                                .andExpect(jsonPath("$.docs[0].characterName").value("Gollum"));
        }

        @Test
        void shouldKeepOptionalQuoteFieldsNullInTheCatalog() throws Exception {
                LoreQuote quote = new LoreQuote("q1", "Short line", null, null, null);

                when(loreDataService.getData()).thenReturn(loreData(List.of(quote)));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs[0]._id").value("q1"))
                                .andExpect(jsonPath("$.docs[0].dialog").value("Short line"))
                                .andExpect(jsonPath("$.docs[0].movie").value(nullValue()))
                                .andExpect(jsonPath("$.docs[0].character").value(nullValue()))
                                .andExpect(jsonPath("$.docs[0].id").value(nullValue()))
                                .andExpect(jsonPath("$.docs[0].characterName").value(nullValue()));
        }

        @Test
        void shouldLeaveCharacterNameNullWhenCharacterIsUnknownOrMissing() throws Exception {
                List<LoreQuote> quotes = List.of(
                                new LoreQuote("q1", "Unknown author", "movie-1", "sauron", "q1"),
                                new LoreQuote("q2", "No author", "movie-1", null, "q2"));

                when(loreDataService.getData()).thenReturn(new LoreData(
                                quotes,
                                Map.of("frodo", new LoreCharacter("frodo", "Frodo Baggins")),
                                quotes));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(2))
                                .andExpect(jsonPath("$.docs[0].character").value("sauron"))
                                .andExpect(jsonPath("$.docs[0].characterName").value(nullValue()))
                                .andExpect(jsonPath("$.docs[1].character").value(nullValue()))
                                .andExpect(jsonPath("$.docs[1].characterName").value(nullValue()));
        }

        @Test
        void shouldResolveNamesOnlyForTheRequestedPage() throws Exception {
                List<LoreQuote> quotes = List.of(
                                new LoreQuote("q1", "One", "movie-1", "frodo", "q1"),
                                new LoreQuote("q2", "Two", "movie-1", "sam", "q2"),
                                new LoreQuote("q3", "Three", "movie-1", "frodo", "q3"));

                when(loreDataService.getData()).thenReturn(new LoreData(
                                quotes,
                                Map.of(
                                                "frodo", new LoreCharacter("frodo", "Frodo Baggins"),
                                                "sam", new LoreCharacter("sam", "Samwise Gamgee")),
                                quotes));

                mockMvc.perform(get("/api/catalog/quotes")
                                .param("page", "2")
                                .param("limit", "2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(1))
                                .andExpect(jsonPath("$.docs[0]._id").value("q3"))
                                .andExpect(jsonPath("$.docs[0].characterName").value("Frodo Baggins"))
                                .andExpect(jsonPath("$.total").value(3))
                                .andExpect(jsonPath("$.pages").value(2));
        }

        @Test
        void shouldReturnRequestedPageInUpstreamOrder() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(11)));

                mockMvc.perform(get("/api/catalog/quotes")
                                .param("page", "2")
                                .param("limit", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(5))
                                .andExpect(jsonPath("$.docs[0]._id").value("q6"))
                                .andExpect(jsonPath("$.docs[1]._id").value("q7"))
                                .andExpect(jsonPath("$.docs[2]._id").value("q8"))
                                .andExpect(jsonPath("$.docs[3]._id").value("q9"))
                                .andExpect(jsonPath("$.docs[4]._id").value("q10"))
                                .andExpect(jsonPath("$.total").value(11))
                                .andExpect(jsonPath("$.limit").value(5))
                                .andExpect(jsonPath("$.page").value(2))
                                .andExpect(jsonPath("$.pages").value(3));
        }

        @Test
        void shouldReturnPartialLastPage() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(11)));

                mockMvc.perform(get("/api/catalog/quotes")
                                .param("page", "3")
                                .param("limit", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(1))
                                .andExpect(jsonPath("$.docs[0]._id").value("q11"))
                                .andExpect(jsonPath("$.page").value(3))
                                .andExpect(jsonPath("$.pages").value(3));
        }

        @Test
        void shouldServeOnlyShortAttributedQuotesFromTheLoadedQuotes() throws Exception {
                LoreQuote shortQuote = new LoreQuote("q1", "Short", "movie-1", "frodo", "q1");
                LoreQuote blank = new LoreQuote("q2", " ", "movie-1", "frodo", "q2");
                LoreQuote missing = new LoreQuote("q3", null, "movie-1", "frodo", "q3");
                LoreQuote tooLong = new LoreQuote("q4", "x".repeat(61), "movie-1", "frodo", "q4");
                List<LoreQuote> quotes = List.of(shortQuote, blank, missing, tooLong);

                when(loreDataService.getData()).thenReturn(new LoreData(
                                quotes,
                                Map.of("frodo", new LoreCharacter("frodo", "Frodo Baggins")),
                                quotes));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(1))
                                .andExpect(jsonPath("$.docs[0]._id").value("q1"))
                                .andExpect(jsonPath("$.total").value(1));
        }

        @Test
        void shouldNotServeQuotesThatAreOnlyInTheUnfilteredList() throws Exception {
                LoreQuote hidden = new LoreQuote("hidden", "Hidden", "movie-1", "frodo", "hidden");

                // The unfiltered quotes list never feeds the catalog.
                when(loreDataService.getData()).thenReturn(new LoreData(
                                List.of(hidden),
                                Map.of("frodo", new LoreCharacter("frodo", "Frodo Baggins")),
                                List.of()));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(0))
                                .andExpect(jsonPath("$.total").value(0));
        }

        @Test
        void shouldCapThePublicCatalogAtOneHundredQuotes() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(2383)));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(100))
                                .andExpect(jsonPath("$.limit").value(20))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.pages").value(5))
                                .andExpect(jsonPath("$.docs.length()").value(20))
                                .andExpect(jsonPath("$.offset").doesNotExist());
        }

        @Test
        void shouldServeTheLastPageOfTheReducedCatalog() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(2383)));

                mockMvc.perform(get("/api/catalog/quotes").param("page", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(20))
                                .andExpect(jsonPath("$.total").value(100))
                                .andExpect(jsonPath("$.page").value(5))
                                .andExpect(jsonPath("$.pages").value(5));
        }

        @Test
        void shouldNotRevealQuotesBeyondTheReducedCatalog() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(2383)));

                // The original catalog had 120 pages of 20; none past the 5th may have content.
                for (String page : List.of("6", "7", "120", String.valueOf(Integer.MAX_VALUE))) {
                        mockMvc.perform(get("/api/catalog/quotes").param("page", page))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.docs.length()").value(0))
                                        .andExpect(jsonPath("$.total").value(100))
                                        .andExpect(jsonPath("$.pages").value(5));
                }
        }

        @Test
        void shouldNeverReturnMoreThanTheCatalogWhateverTheLimit() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(2383)));

                mockMvc.perform(get("/api/catalog/quotes").param("limit", "100"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(100))
                                .andExpect(jsonPath("$.total").value(100))
                                .andExpect(jsonPath("$.pages").value(1));

                mockMvc.perform(get("/api/catalog/quotes").param("page", "2").param("limit", "100"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(0));
        }

        @Test
        void shouldNotReturnTheSameQuoteTwiceAcrossPages() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(2383)));

                Set<String> ids = new HashSet<>();

                for (int page = 1; page <= 5; page++) {
                        String body = mockMvc.perform(get("/api/catalog/quotes").param("page", String.valueOf(page)))
                                        .andExpect(status().isOk())
                                        .andReturn().getResponse().getContentAsString();

                        ids.addAll(JsonPath.<List<String>>read(body, "$.docs[*]._id"));
                }

                assertThat(ids).hasSize(100);
        }

        @Test
        void shouldReturnEmptyDocsForPageBeyondLastPage() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(11)));

                mockMvc.perform(get("/api/catalog/quotes")
                                .param("page", "4")
                                .param("limit", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(0))
                                .andExpect(jsonPath("$.total").value(11))
                                .andExpect(jsonPath("$.limit").value(5))
                                .andExpect(jsonPath("$.page").value(4))
                                .andExpect(jsonPath("$.pages").value(3));
        }

        @Test
        void shouldNotOverflowForVeryLargePage() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(3)));

                mockMvc.perform(get("/api/catalog/quotes")
                                .param("page", String.valueOf(Integer.MAX_VALUE))
                                .param("limit", "100"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(0))
                                .andExpect(jsonPath("$.page").value(Integer.MAX_VALUE))
                                .andExpect(jsonPath("$.pages").value(1));
        }

        @Test
        void shouldReturnSingleEmptyPageWhenNoQuotesAreLoaded() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(List.of()));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(0))
                                .andExpect(jsonPath("$.total").value(0))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.pages").value(1));
        }

        @Test
        void shouldReturnServiceUnavailableWhenLoreDataIsUnavailable() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(new LoreDataUnavailableException(
                                                "The One API returned an empty quote page"));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("empty quote page"))));
        }

        @Test
        void shouldSendRetryAfterRoundedUpWhenLoreDataIsCoolingDown() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(new LoreDataUnavailableException(
                                                "The One API data load failed recently",
                                                null,
                                                NOW.plusMillis(12_200)));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().string("Retry-After", "13"))
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("failed recently"))));
        }

        @Test
        void shouldSendExactRetryAfterWithoutRoundingUpWholeSeconds() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(new LoreDataUnavailableException(
                                                "cooldown", null, NOW.plusSeconds(30)));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().string("Retry-After", "30"));
        }

        @Test
        void shouldNeverSendNegativeRetryAfterWhenRetryIsAlreadyAvailable() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(new LoreDataUnavailableException(
                                                "cooldown", null, NOW.minusSeconds(5)));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().string("Retry-After", "0"));
        }

        @Test
        void shouldOmitRetryAfterWhenLoreDataFailureHasNoRetryTime() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(new LoreDataUnavailableException("The One API returned an empty quote page"));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().doesNotExist("Retry-After"));
        }

        @Test
        void shouldOmitRetryAfterForOtherServiceUnavailableErrors() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(new ResourceAccessException("Request timed out"))
                                .thenThrow(new TheOneApiNotConfiguredException());

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().doesNotExist("Retry-After"));
                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().doesNotExist("Retry-After"));
        }

        @Test
        void shouldReturnServiceUnavailableWhenUpstreamTimesOut() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(new ResourceAccessException("Request timed out"));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("timed out"))));
        }

        @Test
        void shouldNotExposeUpstreamErrorBody() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(HttpClientErrorException.create(
                                                HttpStatus.UNAUTHORIZED,
                                                "Unauthorized",
                                                HttpHeaders.EMPTY,
                                                "{\"message\":\"Invalid token secret-upstream-detail\"}"
                                                                .getBytes(StandardCharsets.UTF_8),
                                                StandardCharsets.UTF_8));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("secret-upstream-detail"))))
                                .andExpect(content().string(not(containsString("Unauthorized"))));
        }

        @Test
        void shouldReturnServiceUnavailableWhenAccessTokenIsMissing() throws Exception {
                when(loreDataService.getData()).thenThrow(new TheOneApiNotConfiguredException());

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("THE_ONE_API_ACCESS_TOKEN"))));
        }

        @Test
        void shouldRejectPageZero() throws Exception {
                mockMvc.perform(get("/api/catalog/quotes")
                                .param("page", "0"))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(loreDataService);
        }

        @Test
        void shouldRejectLimitZero() throws Exception {
                mockMvc.perform(get("/api/catalog/quotes")
                                .param("limit", "0"))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(loreDataService);
        }

        @Test
        void shouldRejectLimitAboveMaximum() throws Exception {
                mockMvc.perform(get("/api/catalog/quotes")
                                .param("limit", "101"))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(loreDataService);
        }

        @ParameterizedTest
        @ValueSource(strings = { "-1", "-2147483648", "abc", "1.5", "99999999999" })
        void shouldRejectInvalidPageParameters(String page) throws Exception {
                mockMvc.perform(get("/api/catalog/quotes").param("page", page))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(loreDataService);
        }

        @ParameterizedTest
        @ValueSource(strings = { "-1", "-2147483648", "1000", "2147483647", "abc", "1.5", "99999999999" })
        void shouldRejectInvalidLimitParameters(String limit) throws Exception {
                mockMvc.perform(get("/api/catalog/quotes").param("limit", limit))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(loreDataService);
        }

        @Test
        void shouldTreatAnEmptyPageParameterAsAbsentAndUseTheDefault() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(2383)));

                mockMvc.perform(get("/api/catalog/quotes").param("page", ""))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.limit").value(20))
                                .andExpect(jsonPath("$.total").value(100));
        }

        @Test
        void shouldTreatAnEmptyLimitParameterAsAbsentAndUseTheDefault() throws Exception {
                when(loreDataService.getData()).thenReturn(loreData(numberedQuotes(2383)));

                mockMvc.perform(get("/api/catalog/quotes").param("limit", ""))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.limit").value(20))
                                .andExpect(jsonPath("$.docs.length()").value(20))
                                .andExpect(jsonPath("$.total").value(100));
        }

        @Test
        void shouldLogLoadFailureOnlyAtDebugWithoutUpstreamBody() throws Exception {
                when(loreDataService.getData())
                                .thenThrow(HttpClientErrorException.create(
                                                HttpStatus.UNAUTHORIZED,
                                                "Unauthorized",
                                                HttpHeaders.EMPTY,
                                                "{\"message\":\"Invalid token secret-upstream-detail\"}"
                                                                .getBytes(StandardCharsets.UTF_8),
                                                StandardCharsets.UTF_8));

                try (LogCapture logs = LogCapture.of(ExternalQuoteController.class)) {
                        mockMvc.perform(get("/api/catalog/quotes"))
                                        .andExpect(status().isServiceUnavailable())
                                        .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL));

                        // LoreDataService logs the load failure at WARN; the handler must not repeat it.
                        assertThat(logs.events(Level.WARN)).isEmpty();
                        assertThat(logs.events(Level.DEBUG)).singleElement()
                                        .extracting(ILoggingEvent::getFormattedMessage)
                                        .isEqualTo("Catalog quotes request failed: "
                                                        + "org.springframework.web.client.HttpClientErrorException$Unauthorized");
                        assertThat(logs.allOutput()).doesNotContain("secret-upstream-detail");
                }
        }

        private static LoreData loreData(List<LoreQuote> quotes) {
                return new LoreData(quotes, Map.of(), quotes);
        }

        private static List<LoreQuote> numberedQuotes(int count) {
                return IntStream.rangeClosed(1, count)
                                .mapToObj(i -> new LoreQuote(
                                                "q" + i, "Dialog " + i, "movie-1", "character-1", "q" + i))
                                .toList();
        }
}
