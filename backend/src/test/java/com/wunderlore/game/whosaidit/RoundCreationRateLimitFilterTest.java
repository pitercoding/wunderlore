package com.wunderlore.game.whosaidit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// The filter in front of the real controller, with the default configuration: 120 per 60 s.
// The service and registry are mocks, so "no round created" is a verify on the registry.
@WebMvcTest(WhoSaidItController.class)
class RoundCreationRateLimitFilterTest {

    private static final String ROUNDS_PATH = "/api/games/who-said-it/rounds";

    private static final String ANSWER_PATH = ROUNDS_PATH + "/{roundId}/answer";

    private static final WhoSaidItRound ROUND = new WhoSaidItRound(
            "Fly, you fools!",
            List.of(
                    new WhoSaidItRound.CharacterOption("gandalf", "Gandalf"),
                    new WhoSaidItRound.CharacterOption("frodo", "Frodo Baggins")),
            "gandalf");

    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return CLOCK;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WhoSaidItService whoSaidItService;

    @MockitoBean
    private WhoSaidItRoundRegistry roundRegistry;

    // The Spring context, and so the limiter inside the filter, is shared by every test in the
    // class. Moving the clock an hour ahead expires whatever an earlier test accepted.
    @BeforeEach
    void startWithAnEmptyWindow() {
        CLOCK.advance(Duration.ofHours(1));
    }

    private void createRounds(int count) throws Exception {
        when(whoSaidItService.generateRound()).thenReturn(ROUND);
        when(roundRegistry.register(any())).thenReturn("round-1");

        for (int i = 0; i < count; i++) {
            mockMvc.perform(post(ROUNDS_PATH)).andExpect(status().isOk());
        }
    }

    @Test
    void shouldAccept120RoundsAndAnswer429OnTheNext() throws Exception {
        createRounds(120);

        mockMvc.perform(post(ROUNDS_PATH))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value("Too many round requests. Please try again later."))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Exception"))));
    }

    @Test
    void shouldNotReachTheControllerOrTheRegistryWhenRejecting() throws Exception {
        createRounds(120);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(ROUNDS_PATH)).andExpect(status().isTooManyRequests());
        }

        verify(whoSaidItService, times(120)).generateRound();
        verify(roundRegistry, times(120)).register(any());
    }

    @Test
    void shouldReportTheTimeUntilTheOldestAcceptExpires() throws Exception {
        createRounds(120);
        CLOCK.advance(Duration.ofMillis(20_500));

        // The accepts all happened at the same instant: 39.5 s left, rounded up to 40.
        mockMvc.perform(post(ROUNDS_PATH))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "40"));
    }

    @Test
    void shouldAcceptAgainOnceTheWindowHasPassed() throws Exception {
        createRounds(120);
        mockMvc.perform(post(ROUNDS_PATH)).andExpect(status().isTooManyRequests());

        CLOCK.advance(Duration.ofSeconds(60));

        mockMvc.perform(post(ROUNDS_PATH)).andExpect(status().isOk());
    }

    @Test
    void shouldNotLimitAnswers() throws Exception {
        createRounds(120);

        for (int i = 0; i < 130; i++) {
            // The mock registry answers null, which renders as an empty 200: not a 429.
            mockMvc.perform(post(ANSWER_PATH, "round-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"characterId\":\"gandalf\"}"))
                    .andExpect(status().isOk());
        }

        verify(roundRegistry, times(130)).answer("round-1", "gandalf");
    }

    @Test
    void shouldNotLimitOtherRoutesOrMethods() throws Exception {
        createRounds(120);

        // GET on the rounds path is not round creation, and neither is any other route. Their
        // statuses are whatever the app says about them; only a 429 would be wrong.
        mockMvc.perform(get(ROUNDS_PATH)).andExpect(status().is(org.hamcrest.Matchers.not(429)));
        mockMvc.perform(get("/health")).andExpect(status().is(org.hamcrest.Matchers.not(429)));
        mockMvc.perform(post("/api/games/which-character/result"))
                .andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }

    @Test
    void shouldNotBeBypassedWithAPathParameter() throws Exception {
        createRounds(120);

        // Spring ignores ;params when mapping, so this still reaches the controller.
        mockMvc.perform(post(ROUNDS_PATH + ";x=1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void shouldNotTrustForwardingHeaders() throws Exception {
        createRounds(120);

        mockMvc.perform(post(ROUNDS_PATH)
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Real-IP", "203.0.113.8")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.1");
                    return request;
                }))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post(ROUNDS_PATH)
                .with(request -> {
                    request.setRemoteAddr("198.51.100.99");
                    return request;
                }))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void shouldNotCountRejectedRequestsAgainstTheNextWindow() throws Exception {
        createRounds(120);

        for (int i = 0; i < 50; i++) {
            mockMvc.perform(post(ROUNDS_PATH)).andExpect(status().isTooManyRequests());
        }

        CLOCK.advance(Duration.ofSeconds(60));

        // A full 120 again: the 50 rejections took nothing from the new window.
        createRounds(120);
        mockMvc.perform(post(ROUNDS_PATH)).andExpect(status().isTooManyRequests());
    }

    @Test
    void shouldLeaveTheServiceUntouchedByARejectedRequest() throws Exception {
        createRounds(120);
        org.mockito.Mockito.clearInvocations(whoSaidItService, roundRegistry);

        mockMvc.perform(post(ROUNDS_PATH)).andExpect(status().isTooManyRequests());

        verifyNoInteractions(whoSaidItService, roundRegistry);
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
