package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.lore.LoreData;
import com.wunderlore.lore.LoreDataService;
import com.wunderlore.lore.LoreDataUnavailableException;
import com.wunderlore.support.LogCapture;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalQuoteController.class)
class ExternalQuoteControllerTest {

        private static final String UNAVAILABLE_DETAIL = "Unable to load quotes right now";

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private LoreDataService loreDataService;

        @Test
        void shouldReturnQuotesUsingDefaultPagination() throws Exception {
                ExternalQuote quote = new ExternalQuote(
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
        void shouldIncludeQuotesWithoutDialogOrKnownCharacter() throws Exception {
                List<ExternalQuote> quotes = List.of(
                                new ExternalQuote("q1", "", "movie-1", "frodo", "q1"),
                                new ExternalQuote("q2", null, "movie-1", "frodo", "q2"),
                                new ExternalQuote("q3", "Unknown author", "movie-1", "sauron", "q3"),
                                new ExternalQuote("q4", "No author", "movie-1", null, "q4"));

                // attributedQuotes is empty on purpose: the catalog must not rely on it.
                when(loreDataService.getData()).thenReturn(new LoreData(
                                quotes,
                                Map.of("frodo", new ExternalCharacter("frodo", "Frodo Baggins")),
                                List.of()));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(4))
                                .andExpect(jsonPath("$.docs[0]._id").value("q1"))
                                .andExpect(jsonPath("$.docs[1]._id").value("q2"))
                                .andExpect(jsonPath("$.docs[2]._id").value("q3"))
                                .andExpect(jsonPath("$.docs[3]._id").value("q4"))
                                .andExpect(jsonPath("$.total").value(4));
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

        private static LoreData loreData(List<ExternalQuote> quotes) {
                return new LoreData(quotes, Map.of(), List.of());
        }

        private static List<ExternalQuote> numberedQuotes(int count) {
                return IntStream.rangeClosed(1, count)
                                .mapToObj(i -> new ExternalQuote(
                                                "q" + i, "Dialog " + i, "movie-1", "character-1", "q" + i))
                                .toList();
        }
}
