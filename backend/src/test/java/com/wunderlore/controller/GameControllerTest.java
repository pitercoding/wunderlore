package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.game.AnswerRequest;
import com.wunderlore.game.AnswerResponse;
import com.wunderlore.game.GameRound;
import com.wunderlore.game.GameRoundRegistry;
import com.wunderlore.game.InvalidCharacterOptionException;
import com.wunderlore.game.RoundAlreadyAnsweredException;
import com.wunderlore.game.RoundCapacityExceededException;
import com.wunderlore.game.RoundNotFoundException;
import com.wunderlore.game.WhoSaidItService;
import com.wunderlore.lore.LoreDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

        private static final String UNAVAILABLE_DETAIL = "Unable to generate a game round right now";

        private static final String CAPACITY_DETAIL = "Too many active rounds, try again later";

        private static final String INVALID_REQUEST_DETAIL = "Invalid answer request";

        private static final String ROUND_ID = "round-1";

        private static final GameRound ROUND = new GameRound(
                        "Fly, you fools!",
                        List.of(
                                        new GameRound.CharacterOption("gandalf", "Gandalf"),
                                        new GameRound.CharacterOption("frodo", "Frodo Baggins"),
                                        new GameRound.CharacterOption("sam", "Samwise Gamgee"),
                                        new GameRound.CharacterOption("aragorn", "Aragorn")),
                        "gandalf");

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private WhoSaidItService whoSaidItService;

        @MockitoBean
        private GameRoundRegistry gameRoundRegistry;

        @Test
        void shouldReturnRoundWithQuoteAndOptions() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);
                when(gameRoundRegistry.register(ROUND)).thenReturn(ROUND_ID);

                mockMvc.perform(get("/api/game/round"))
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
                verify(gameRoundRegistry).register(ROUND);
        }

        @Test
        void shouldNotExposeCorrectCharacterId() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);
                when(gameRoundRegistry.register(ROUND)).thenReturn(ROUND_ID);

                mockMvc.perform(get("/api/game/round"))
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

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("The One API unavailable"))));

                verify(gameRoundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenExternalApiTimesOut() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new ResourceAccessException("Request timed out"));

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("timed out"))));

                verify(gameRoundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenGameDataIsInsufficient() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new IllegalStateException(
                                                "At least four distinct characters are required to play"));

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("At least four distinct characters"))));

                verify(gameRoundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenNoValidQuotesAreAvailable() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new IllegalStateException(
                                                "No valid quotes available to start the game"));

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("No valid quotes"))));

                verify(gameRoundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenLoreDataIsUnavailable() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new LoreDataUnavailableException(
                                                "The One API returned an empty quote page"));

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("empty quote page"))))
                                .andExpect(content().string(not(containsString("The One API"))));

                verify(gameRoundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenApiTokenIsNotConfigured() throws Exception {
                when(whoSaidItService.generateRound())
                                .thenThrow(new TheOneApiNotConfiguredException());

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("THE_ONE_API_ACCESS_TOKEN"))))
                                .andExpect(content().string(not(containsString("not configured"))));

                verify(gameRoundRegistry, never()).register(any());
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

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(UNAVAILABLE_DETAIL))
                                .andExpect(content().string(not(containsString("secret-upstream-detail"))))
                                .andExpect(content().string(not(containsString("Unauthorized"))));

                verify(gameRoundRegistry, never()).register(any());
        }

        @Test
        void shouldReturnServiceUnavailableWhenRoundCapacityIsExceeded() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);
                when(gameRoundRegistry.register(ROUND))
                                .thenThrow(new RoundCapacityExceededException());

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.status").value(503))
                                .andExpect(jsonPath("$.detail").value(CAPACITY_DETAIL))
                                .andExpect(jsonPath("$.roundId").doesNotExist());

                verify(gameRoundRegistry).register(ROUND);
        }

        @Test
        void shouldReturnCorrectResultWhenAnswerIsCorrect() throws Exception {
                AnswerRequest request = new AnswerRequest(ROUND_ID, "gandalf");

                when(gameRoundRegistry.answer(request))
                                .thenReturn(new AnswerResponse(ROUND_ID, "gandalf", true, "gandalf"));

                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson(ROUND_ID, "gandalf")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roundId").value(ROUND_ID))
                                .andExpect(jsonPath("$.submittedCharacterId").value("gandalf"))
                                .andExpect(jsonPath("$.correct").value(true))
                                .andExpect(jsonPath("$.correctCharacterId").value("gandalf"));

                verify(gameRoundRegistry).answer(request);
        }

        @Test
        void shouldReturnIncorrectResultWhenAnswerIsWrong() throws Exception {
                AnswerRequest request = new AnswerRequest(ROUND_ID, "frodo");

                when(gameRoundRegistry.answer(request))
                                .thenReturn(new AnswerResponse(ROUND_ID, "frodo", false, "gandalf"));

                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson(ROUND_ID, "frodo")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.roundId").value(ROUND_ID))
                                .andExpect(jsonPath("$.submittedCharacterId").value("frodo"))
                                .andExpect(jsonPath("$.correct").value(false))
                                .andExpect(jsonPath("$.correctCharacterId").value("gandalf"));

                verify(gameRoundRegistry).answer(request);
        }

        @Test
        void shouldReturnBadRequestWhenRoundIdIsBlank() throws Exception {
                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson(" ", "gandalf")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(gameRoundRegistry, never()).answer(any());
        }

        @Test
        void shouldReturnBadRequestWhenCharacterIdIsBlank() throws Exception {
                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson(ROUND_ID, "")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(gameRoundRegistry, never()).answer(any());
        }

        @Test
        void shouldReturnBadRequestWhenRequiredFieldIsMissing() throws Exception {
                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"roundId":"round-1"}
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(gameRoundRegistry, never()).answer(any());
        }

        @Test
        void shouldReturnBadRequestWhenJsonIsMalformed() throws Exception {
                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"roundId\":"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(gameRoundRegistry, never()).answer(any());
        }

        @Test
        void shouldReturnBadRequestWhenBodyIsEmpty() throws Exception {
                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(""))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value(INVALID_REQUEST_DETAIL));

                verify(gameRoundRegistry, never()).answer(any());
        }

        @Test
        void shouldReturnBadRequestWhenCharacterIsNotAnOption() throws Exception {
                when(gameRoundRegistry.answer(any()))
                                .thenThrow(new InvalidCharacterOptionException());

                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson(ROUND_ID, "legolas")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.detail").value("Character was not an option in this round"));
        }

        @Test
        void shouldReturnNotFoundWhenRoundDoesNotExist() throws Exception {
                when(gameRoundRegistry.answer(any()))
                                .thenThrow(new RoundNotFoundException());

                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson("unknown-round", "gandalf")))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404))
                                .andExpect(jsonPath("$.detail").value("Round not found or expired"));
        }

        @Test
        void shouldReturnConflictWhenRoundWasAlreadyAnswered() throws Exception {
                when(gameRoundRegistry.answer(any()))
                                .thenThrow(new RoundAlreadyAnsweredException());

                mockMvc.perform(post("/api/game/answer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerJson(ROUND_ID, "gandalf")))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.status").value(409))
                                .andExpect(jsonPath("$.detail").value("Round has already been answered"));
        }

        private static String answerJson(String roundId, String characterId) {
                return """
                                {"roundId":"%s","characterId":"%s"}
                                """.formatted(roundId, characterId);
        }
}
