package com.wunderlore.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.lore.LoreBacked;
import com.wunderlore.lore.LoreDataUnavailableException;
import com.wunderlore.support.LogCapture;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The advice applies to every controller. WhoSaidItController and ExternalQuoteController tests
// cover what it sends for them; these controllers exist only here, to show that neither of them
// is special: one declares @LoreBacked, the other is a controller the advice has never heard of.
@WebMvcTest({
        LoreUnavailableAdviceTest.AnnotatedController.class,
        LoreUnavailableAdviceTest.PlainController.class })
@Import({
        LoreUnavailableAdviceTest.AnnotatedController.class,
        LoreUnavailableAdviceTest.PlainController.class })
class LoreUnavailableAdviceTest {

    private static final String ANNOTATED_DETAIL = "Unable to do the new thing right now";

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @RestController
    @LoreBacked(unavailableDetail = ANNOTATED_DETAIL, failure = "New thing failed")
    static class AnnotatedController {

        @GetMapping("/annotated/rest-client")
        String restClientFailure() {
            throw new RestClientException("upstream failure at https://secret.example/token");
        }

        @GetMapping("/annotated/not-configured")
        String notConfigured() {
            throw new TheOneApiNotConfiguredException();
        }

        @GetMapping("/annotated/lore")
        String loreUnavailable() {
            throw new LoreDataUnavailableException("cooldown", null, Instant.parse("2026-01-01T12:00:30Z"));
        }

        @GetMapping("/annotated/unrelated")
        String unrelated() {
            throw new IllegalStateException("bug");
        }
    }

    @RestController
    static class PlainController {

        @GetMapping("/plain/rest-client")
        String restClientFailure() {
            throw new RestClientException("upstream failure");
        }

        @GetMapping("/plain/not-configured")
        String notConfigured() {
            throw new TheOneApiNotConfiguredException();
        }

        @GetMapping("/plain/lore")
        String loreUnavailable() {
            throw new LoreDataUnavailableException("cooldown", null, Instant.parse("2026-01-01T12:00:30Z"));
        }

        @GetMapping("/plain/lore-no-cooldown")
        String loreUnavailableWithoutCooldown() {
            throw new LoreDataUnavailableException("invalid page count");
        }

        @GetMapping("/plain/unrelated")
        String unrelated() {
            throw new IllegalStateException("bug");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldUseTheDetailDeclaredByAnnotatedControllerForLoreUnavailable() throws Exception {
        mockMvc.perform(get("/annotated/lore"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(ANNOTATED_DETAIL));
    }

    @Test
    void shouldUseTheDetailDeclaredByAnnotatedControllerForUpstreamFailures() throws Exception {
        mockMvc.perform(get("/annotated/rest-client"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.detail").value(ANNOTATED_DETAIL));

        mockMvc.perform(get("/annotated/not-configured"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.detail").value(ANNOTATED_DETAIL));
    }

    @Test
    void shouldLogOnTheControllerLoggerWithoutExceptionMessage() throws Exception {
        try (LogCapture logs = LogCapture.of(AnnotatedController.class)) {
            mockMvc.perform(get("/annotated/rest-client")).andExpect(status().isServiceUnavailable());

            assertThat(logs.events(Level.DEBUG)).singleElement()
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .isEqualTo("New thing failed: org.springframework.web.client.RestClientException");
            assertThat(logs.allOutput()).doesNotContain("secret.example");
        }
    }

    @Test
    void shouldHandleControllersThatDeclareNothingWithGenericDetail() throws Exception {
        mockMvc.perform(get("/plain/lore"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.detail").value(LoreUnavailableAdvice.DEFAULT_DETAIL));

        mockMvc.perform(get("/plain/lore-no-cooldown"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.detail").value(LoreUnavailableAdvice.DEFAULT_DETAIL));

        mockMvc.perform(get("/plain/rest-client"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(LoreUnavailableAdvice.DEFAULT_DETAIL));

        mockMvc.perform(get("/plain/not-configured"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(LoreUnavailableAdvice.DEFAULT_DETAIL));
    }

    @Test
    void shouldNotHandleUnrelatedExceptions() {
        assertThatThrownBy(() -> mockMvc.perform(get("/annotated/unrelated")))
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> mockMvc.perform(get("/plain/unrelated")))
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
