package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalBook;
import com.wunderlore.client.theone.dto.ExternalBookResponse;
import com.wunderlore.support.LogCapture;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalBookController.class)
class ExternalBookControllerTest {

    private static final String UNAVAILABLE_DETAIL = "Unable to load books right now";

    private static final String UPSTREAM_URL = "https://the-one-api.dev/v2/book";

    // LoreUnavailableAdvice is part of every web slice and needs a Clock; it must
    // not apply here.
    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TheOneApiClient theOneApiClient;

    @Test
    void shouldReturnBooks() throws Exception {
        when(theOneApiClient.getBooks()).thenReturn(new ExternalBookResponse(
                List.of(new ExternalBook("book-123", "The Fellowship Of The Ring")),
                1, 1000, 0, 1, 1));

        mockMvc.perform(get("/api/catalog/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docs.length()").value(1))
                .andExpect(jsonPath("$.docs[0]._id").value("book-123"))
                .andExpect(jsonPath("$.docs[0].name").value("The Fellowship Of The Ring"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.limit").value(1000))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pages").value(1));

        verify(theOneApiClient).getBooks();
    }

    @Test
    void shouldReturnServiceUnavailableWhenUpstreamReturnsClientError() throws Exception {
        when(theOneApiClient.getBooks()).thenThrow(upstreamError(HttpStatus.TOO_MANY_REQUESTS));

        expectUnavailable(mockMvc.perform(get("/api/catalog/books")));
    }

    @Test
    void shouldReturnServiceUnavailableWhenUpstreamReturnsServerError() throws Exception {
        when(theOneApiClient.getBooks()).thenThrow(HttpServerErrorException.create(
                "500 Internal Server Error on GET request for \"" + UPSTREAM_URL + "\": \"secret-upstream-detail\"",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                HttpHeaders.EMPTY,
                "secret-upstream-detail".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        expectUnavailable(mockMvc.perform(get("/api/catalog/books")));
    }

    @Test
    void shouldReturnServiceUnavailableWhenUpstreamTimesOut() throws Exception {
        when(theOneApiClient.getBooks()).thenThrow(timeout());

        expectUnavailable(mockMvc.perform(get("/api/catalog/books")))
                .andExpect(content().string(not(containsString("timed out"))));
    }

    @Test
    void shouldReturnServiceUnavailableWhenUpstreamBodyCannotBeRead() throws Exception {
        when(theOneApiClient.getBooks()).thenThrow(new RestClientException(
                "Error while extracting response for type [ExternalBookResponse]: secret-upstream-detail"));

        expectUnavailable(mockMvc.perform(get("/api/catalog/books")));
    }

    @Test
    void shouldReturnServiceUnavailableWhenUpstreamReturnsNoBody() throws Exception {
        when(theOneApiClient.getBooks()).thenReturn(null);

        expectUnavailable(mockMvc.perform(get("/api/catalog/books")))
                .andExpect(content().string(not(containsString("empty book response"))));
    }

    @Test
    void shouldReturnServiceUnavailableWhenUpstreamReturnsNoDocs() throws Exception {
        when(theOneApiClient.getBooks()).thenReturn(new ExternalBookResponse(null, 0, 0, 0, 0, 0));

        expectUnavailable(mockMvc.perform(get("/api/catalog/books")));
    }

    @Test
    void shouldNotMaskProgrammingErrorsAsUpstreamFailures() throws Exception {
        when(theOneApiClient.getBooks()).thenThrow(new IllegalStateException("bug"));

        try (LogCapture logs = LogCapture.of(ExternalBookController.class)) {
            // Unhandled here, so it reaches the default error handling (HTTP 500) instead
            // of a 503.
            assertThatThrownBy(() -> mockMvc.perform(get("/api/catalog/books")))
                    .isInstanceOf(ServletException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class);

            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    void shouldLogUpstreamHttpFailureOnceAtWarnWithoutSensitiveDetails() throws Exception {
        when(theOneApiClient.getBooks()).thenThrow(upstreamError(HttpStatus.UNAUTHORIZED));

        try (LogCapture logs = LogCapture.of(ExternalBookController.class)) {
            mockMvc.perform(get("/api/catalog/books"))
                    .andExpect(status().isServiceUnavailable());

            // Books bypass LoreDataService, so the handler is the only place this is
            // logged.
            assertThat(logs.events()).singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getThrowableProxy()).isNull();
                    })
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .isEqualTo("Catalog books request failed: error="
                            + "org.springframework.web.client.HttpClientErrorException$Unauthorized (HTTP 401)");
            assertThat(logs.allOutput())
                    .doesNotContain("secret-upstream-detail")
                    .doesNotContain(UPSTREAM_URL);
        }
    }

    @Test
    void shouldLogTimeoutOnceAtWarnWithoutUrl() throws Exception {
        when(theOneApiClient.getBooks()).thenThrow(timeout());

        try (LogCapture logs = LogCapture.of(ExternalBookController.class)) {
            mockMvc.perform(get("/api/catalog/books"))
                    .andExpect(status().isServiceUnavailable());

            assertThat(logs.events()).singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getThrowableProxy()).isNull();
                    })
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .isEqualTo("Catalog books request failed: error="
                            + "org.springframework.web.client.ResourceAccessException"
                            + " caused by java.net.http.HttpTimeoutException");
            assertThat(logs.allOutput())
                    .doesNotContain(UPSTREAM_URL)
                    .doesNotContain("timed out");
        }
    }

    @Test
    void shouldLogEmptyResponseOnceAtWarn() throws Exception {
        when(theOneApiClient.getBooks()).thenReturn(null);

        try (LogCapture logs = LogCapture.of(ExternalBookController.class)) {
            mockMvc.perform(get("/api/catalog/books"))
                    .andExpect(status().isServiceUnavailable());

            assertThat(logs.events(Level.WARN)).singleElement()
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .isEqualTo("Catalog books request failed: error="
                            + "The One API returned an empty book response");
        }
    }

    private static ResultActions expectUnavailable(ResultActions result) throws Exception {
        return result
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                .andExpect(content().string(not(containsString("secret-upstream-detail"))))
                .andExpect(content().string(not(containsString("the-one-api.dev"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    // Mirrors the message RestClient builds for an error status, which embeds the
    // request URL and the upstream response body.
    private static HttpClientErrorException upstreamError(HttpStatus status) {
        String body = "{\"message\":\"Invalid token secret-upstream-detail\"}";

        return HttpClientErrorException.create(
                status.value() + " " + status.getReasonPhrase()
                        + " on GET request for \"" + UPSTREAM_URL + "\": \"" + body + "\"",
                status,
                status.getReasonPhrase(),
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    private static ResourceAccessException timeout() {
        return new ResourceAccessException(
                "I/O error on GET request for \"" + UPSTREAM_URL + "\": request timed out",
                new HttpTimeoutException("request timed out"));
    }
}
