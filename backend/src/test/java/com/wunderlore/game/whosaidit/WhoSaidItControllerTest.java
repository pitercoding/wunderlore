package com.wunderlore.game.whosaidit;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.lore.LoreDataUnavailableException;
import com.wunderlore.support.LogCapture;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WhoSaidItController.class)
class WhoSaidItControllerTest {

        private static final String UNAVAILABLE_DETAIL = "Unable to generate a game round right now";

        private static final String CAPACITY_DETAIL = "Too many active rounds, try again later";

        private static final String INVALID_REQUEST_DETAIL = "Invalid answer request";

        private static final String ROUNDS_PATH = "/api/games/who-said-it/rounds";

        private static final String ANSWER_PATH = ROUNDS_PATH + "/{roundId}/answer";

        private static final String ROUND_ID = "round-1";

        private static final WhoSaidItRound ROUND = new WhoSaidItRound(
                        "Fly, you fools!",
                        List.of(
                                        new WhoSaidItRound.CharacterOption("gandalf", "Gandalf"),
                                        new WhoSaidItRound.CharacterOption("frodo", "Frodo Baggins"),
                                        new WhoSaidItRound.CharacterOption("sam", "Samwise Gamgee"),
                                        new WhoSaidItRound.CharacterOption("aragorn", "Aragorn")),
                        "gandalf");

        @Autowired
        private MockMvc mockMvc;

        private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

        @TestConfiguration
        static class ClockConfiguration {

                @Bean
                Clock clock() {
                        return Clock.fixed(NOW, ZoneOffset.UTC);
                }
        }

        @MockitoBean
        private WhoSaidItService whoSaidItService;

        @MockitoBean
        private WhoSaidItRoundRegistry roundRegistry;

        @Test
        void shouldCreateRoundWithQuoteAndOptions() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);
                when(roundRegistry.register(ROUND)).thenReturn(ROUND_ID);

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roundId").value(ROUND_ID))
                                .andExpect(jsonPath("$.quote").value("Fly, you fools!"))
                                .andExpect(jsonPath("$.options.length()").value(4))
                                .andExpect(jsonPath("$.options[0].id").value("gandalf"))
                                .andExpect(jsonPath("$.options[0].name").value("Gandalf"))
                                .andExpect(jsonPath("$.options[1].id").value("frodo"))
                                .andExpect(jsonPath("$.options[1].name").value("Frodo Baggins"))
                                .andExpect(jsonPath("$.options[2].id").value("sam"))
                                .andExpect(jsonPath("$.options[2].name").value("Samwise Gamgee"))
                                .andExpect(jsonPath("$.options[3].id").value("aragorn"))
                                .andExpect(jsonPath("$.options[3].name").value("Aragorn"));

                verify(whoSaidItService).generateRound();
                verify(roundRegistry).register(ROUND);
        }

        @Test
        void shouldNotExposeCorrectCharacterId() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);
                when(roundRegistry.register(ROUND)).thenReturn(ROUND_ID);

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roundId").value(ROUND_ID))
                                .andExpect(jsonPath("$.correctCharacterId").doesNotExist())
                                // Only roundId, quote and options may be exposed at the top level.
                                .andExpect(jsonPath("$.length()").value(3))
                                // Each option exposes only id and name, with no correctness flag.
                                .andExpect(jsonPath("$.options[0].length()").value(2))
                                .andExpect(jsonPath("$.options[1].length()").value(2))
                                .andExpect(jsonPath("$.options[2].length()").value(2))
                                .andExpect(jsonPath("$.options[3].length()").value(2))
                                .andExpect(content().string(not(containsString("correct"))));
        }

        @Test
        void shouldReturnServiceUnavailableWhenExternalApiFails() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new RestClientException("The One API unavailable"));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("The One API unavailable"))));

                verify(roundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenExternalApiTimesOut() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new ResourceAccessException("Request timed out"));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("timed out"))));

                verify(roundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenGameDataIsInsufficient() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new WhoSaidItInsufficientDataException(
                                                "At least four distinct characters are required to play"));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("At least four distinct characters"))));

                verify(roundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenNoValidQuotesAreAvailable() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new WhoSaidItInsufficientDataException(
                                                "No valid quotes available to start the game"));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("No valid quotes"))));

                verify(roundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenLoreDataIsUnavailable() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new LoreDataUnavailableException(
                                                "The One API returned an empty quote page"));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("empty quote page"))))
                                .andExpect(content().string(not(containsString("The One API"))));

                verify(roundRegistry, never()).register(any());
        }

        @Test
        void shouldSendRetryAfterRoundedUpWhenLoreDataIsCoolingDown() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new LoreDataUnavailableException(
                                                "The One API data load failed recently",
                                                null,
                                                NOW.plusMillis(12_200)));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().string("Retry-After", "13"))
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("failed recently"))));
        }

        @Test
        void shouldSendExactRetryAfterWithoutRoundingUpWholeSeconds() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new LoreDataUnavailableException(
                                                "cooldown", null, NOW.plusSeconds(30)));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().string("Retry-After", "30"));
        }

        @Test
        void shouldNeverSendNegativeRetryAfterWhenRetryIsAlreadyAvailable() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new LoreDataUnavailableException(
                                                "cooldown", null, NOW.minusSeconds(5)));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().string("Retry-After", "0"));
        }

        @Test
        void shouldOmitRetryAfterWhenLoreDataFailureHasNoRetryTime() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new LoreDataUnavailableException("The One API returned an empty quote page"));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().doesNotExist("Retry-After"));
        }

        @Test
        void shouldOmitRetryAfterForOtherServiceUnavailableErrors() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new ResourceAccessException("Request timed out"))
                                .thenThrow(new WhoSaidItInsufficientDataException("No valid quotes"));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().doesNotExist("Retry-After"));
                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().doesNotExist("Retry-After"));
        }

        @Test
        void shouldReturnServiceUnavailableWhenApiTokenIsNotConfigured() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new TheOneApiNotConfiguredException());

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("THE_ONE_API_ACCESS_TOKEN"))))
                                .andExpect(content().string(not(containsString("not configured"))));

                verify(roundRegistry, never()).register(any());
        }

        @Test
        void shouldNotExposeUpstreamResponseBodyWhenExternalApiRejectsRequest() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(HttpClientErrorException.create(
                                                HttpStatus.UNAUTHORIZED,
                                                "Unauthorized",
                                                HttpHeaders.EMPTY,
                                                "{\"message\":\"Invalid token secret-upstream-detail\"}"
                                                                .getBytes(StandardCharsets.UTF_8),
                                                StandardCharsets.UTF_8));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("secret-upstream-detail"))))
                                .andExpect(content().string(not(containsString("Unauthorized"))));

                verify(roundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenRoundCapacityIsExceeded() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);
                when(roundRegistry.register(ROUND))
                                .thenThrow(new WhoSaidItRoundCapacityExceededException());

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(CAPACITY_DETAIL))
                                .andExpect(header().doesNotExist("Retry-After"))
                                .andExpect(jsonPath("$.roundId").doesNotExist());

                verify(roundRegistry).register(ROUND);
        }

        @Test
        void shouldReturnCorrectResultWhenAnswerIsCorrect() throws Exception {
                when(roundRegistry.answer(ROUND_ID, "gandalf"))
                                .thenReturn(new WhoSaidItAnswerResponse(ROUND_ID, "gandalf", true, "gandalf"));

                mockMvc.perform(post(ANSWER_PATH, ROUND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson("gandalf")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roundId").value(ROUND_ID))
                                .andExpect(jsonPath("$.submittedCharacterId").value("gandalf"))
                                .andExpect(jsonPath("$.correct").value(true))
                                .andExpect(jsonPath("$.correctCharacterId").value("gandalf"));

                verify(roundRegistry).answer(ROUND_ID, "gandalf");
        }

        @Test
        void shouldReturnIncorrectResultWhenAnswerIsWrong() throws Exception {
                when(roundRegistry.answer(ROUND_ID, "frodo"))
                                .thenReturn(new WhoSaidItAnswerResponse(ROUND_ID, "frodo", false, "gandalf"));

                mockMvc.perform(post(ANSWER_PATH, ROUND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson("frodo")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roundId").value(ROUND_ID))
                                .andExpect(jsonPath("$.submittedCharacterId").value("frodo"))
                                .andExpect(jsonPath("$.correct").value(false))
                                .andExpect(jsonPath("$.correctCharacterId").value("gandalf"));

                verify(roundRegistry).answer(ROUND_ID, "frodo");
        }

        @Test
        void shouldReturnBadRequestWhenCharacterIdIsBlank() throws Exception {
                mockMvc.perform(post(ANSWER_PATH, ROUND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson("")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(roundRegistry, never()).answer(any(), any());
        }

        @Test
        void shouldReturnBadRequestWhenRequiredFieldIsMissing() throws Exception {
                mockMvc.perform(post(ANSWER_PATH, ROUND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"unknown":"gandalf"}
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(roundRegistry, never()).answer(any(), any());
        }

        @Test
        void shouldReturnBadRequestWhenJsonIsMalformed() throws Exception {
                mockMvc.perform(post(ANSWER_PATH, ROUND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"characterId\":"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(roundRegistry, never()).answer(any(), any());
        }

        @Test
        void shouldReturnBadRequestWhenBodyIsEmpty() throws Exception {
                mockMvc.perform(post(ANSWER_PATH, ROUND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(""))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(roundRegistry, never()).answer(any(), any());
        }

        @Test
        void shouldReturnBadRequestWhenCharacterIsNotAnOption() throws Exception {
                when(roundRegistry.answer(any(), any()))
                                .thenThrow(new WhoSaidItInvalidCharacterOptionException());

                mockMvc.perform(post(ANSWER_PATH, ROUND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson("legolas")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value("Character was not an option in this round"));
        }

        @Test
        void shouldReturnNotFoundWhenRoundDoesNotExist() throws Exception {
                when(roundRegistry.answer(any(), any()))
                                .thenThrow(new WhoSaidItRoundNotFoundException());

                mockMvc.perform(post(ANSWER_PATH, "unknown-round")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson("gandalf")))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404))
                                .andExpect(jsonPath("$.detail").value("Round not found or expired"));
        }

        @Test
        void shouldReturnConflictWhenRoundWasAlreadyAnswered() throws Exception {
                when(roundRegistry.answer(any(), any()))
                                .thenThrow(new WhoSaidItRoundAlreadyAnsweredException());

                mockMvc.perform(post(ANSWER_PATH, ROUND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson("gandalf")))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.status").value(409))
                                .andExpect(jsonPath("$.detail").value("Round has already been answered"));
        }

        @Test
        void shouldWarnWhenGameDataIsInsufficient() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new WhoSaidItInsufficientDataException(
                                                "At least four distinct characters are required to play"));

                try (LogCapture logs = LogCapture.of(WhoSaidItController.class)) {
                        mockMvc.perform(post(ROUNDS_PATH))
                                        .andExpect(status().isServiceUnavailable())
                                        .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL));

                        assertThat(logs.events(Level.WARN)).singleElement()
                                        .extracting(ILoggingEvent::getFormattedMessage)
                                        .isEqualTo("Game round generation failed: com.wunderlore.game.whosaidit.WhoSaidItInsufficientDataException: "
                                                        + "At least four distinct characters are required to play");
                }
        }

        @Test
        void shouldNotTreatUnexpectedIllegalStateAsInsufficientData() {
                when(whoSaidItService.generateRound()).thenThrow(new IllegalStateException("bug"));

                try (LogCapture logs = LogCapture.of(WhoSaidItController.class)) {
                        // No handler matches, so the error leaves the controller instead of becoming a 503.
                        assertThatThrownBy(() -> mockMvc.perform(post(ROUNDS_PATH)))
                                        .hasCauseExactlyInstanceOf(IllegalStateException.class);

                        assertThat(logs.events()).isEmpty();
                }

                verify(roundRegistry, never()).register(any());
        }

        @Test
        void shouldLogUpstreamFailureOnlyAtDebugWithoutItsMessage() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new ResourceAccessException(
                                                "I/O error on GET request for \"https://the-one-api.dev/v2/quote\""));

                try (LogCapture logs = LogCapture.of(WhoSaidItController.class)) {
                        mockMvc.perform(post(ROUNDS_PATH))
                                        .andExpect(status().isServiceUnavailable())
                                        .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL));

                        // LoreDataService logs the load failure at WARN; the handler must not repeat it.
                        assertThat(logs.events(Level.WARN)).isEmpty();
                        assertThat(logs.events(Level.DEBUG)).singleElement()
                                        .extracting(ILoggingEvent::getFormattedMessage)
                                        .isEqualTo("Game round generation failed: "
                                                        + "org.springframework.web.client.ResourceAccessException");
                        assertThat(logs.allOutput()).doesNotContain("https://");
                }
        }

        @Test
        void shouldNotAcceptGetOnRoundCreationRoute() throws Exception {
                mockMvc.perform(get(ROUNDS_PATH))
                                .andExpect(status().isMethodNotAllowed());

                verify(whoSaidItService, never()).generateRound();
        }

        @Test
        void shouldNotServeTheOldGameRoutes() throws Exception {
                mockMvc.perform(get("/api/game/round")).andExpect(status().isNotFound());
                mockMvc.perform(post("/api/game/round")).andExpect(status().isNotFound());
                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson("gandalf")))
                                .andExpect(status().isNotFound());

                verify(whoSaidItService, never()).generateRound();
                verify(roundRegistry, never()).answer(any(), any());
        }

        @Test
        void shouldBeCoveredByLoreUnavailableAdvice() throws Exception {
                // Only an advice scoped to this controller can turn the lore failure into a 503.
                when(whoSaidItService.generateRound())
                                .thenThrow(new LoreDataUnavailableException("cooldown", null, NOW.plusSeconds(30)));

                mockMvc.perform(post(ROUNDS_PATH))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(header().string("Retry-After", "30"));
        }

        private static String answerJson(String characterId) {
                return """
                                {"characterId":"%s"}
                                """.formatted(characterId);
        }
}
