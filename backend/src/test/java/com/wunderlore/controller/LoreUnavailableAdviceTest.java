package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.lore.LoreDataUnavailableException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// The advice is limited to WhoSaidItController and ExternalQuoteController. Their own tests cover
// what it sends; this checks that a controller outside that scope never gets those responses.
@WebMvcTest(LoreUnavailableAdviceTest.OutOfScopeController.class)
@Import(LoreUnavailableAdviceTest.OutOfScopeController.class)
class LoreUnavailableAdviceTest {

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @RestController
    static class OutOfScopeController {

        @GetMapping("/out-of-scope/rest-client")
        String restClientFailure() {
            throw new RestClientException("upstream failure");
        }

        @GetMapping("/out-of-scope/not-configured")
        String notConfigured() {
            throw new TheOneApiNotConfiguredException();
        }

        @GetMapping("/out-of-scope/lore")
        String loreUnavailable() {
            throw new LoreDataUnavailableException("cooldown", null, Instant.parse("2026-01-01T12:00:30Z"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldNotHandleRestClientExceptionFromControllersOutsideItsScope() {
        assertThatThrownBy(() -> mockMvc.perform(get("/out-of-scope/rest-client")))
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(RestClientException.class);
    }

    @Test
    void shouldNotHandleMissingTokenFromControllersOutsideItsScope() {
        assertThatThrownBy(() -> mockMvc.perform(get("/out-of-scope/not-configured")))
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(TheOneApiNotConfiguredException.class);
    }

    @Test
    void shouldNotHandleLoreUnavailableFromControllersOutsideItsScope() {
        assertThatThrownBy(() -> mockMvc.perform(get("/out-of-scope/lore")))
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(LoreDataUnavailableException.class);
    }
}
