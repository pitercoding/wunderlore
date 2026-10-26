package com.wunderlore.controller;

import com.wunderlore.game.GameRound;
import com.wunderlore.game.WhoSaidItService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

        private static final String UNAVAILABLE_DETAIL = "Unable to generate a game round right now";

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

        @Test
        void shouldReturnRoundWithQuoteAndOptions() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.quote").value("Fly, you fools!"))
                                .andExpect(jsonPath("$.options.length()").value(4))
                                .andExpect(jsonPath("$.options[0].id").value("gandalf"))
                                .andExpect(jsonPath("$.options[0].name").value("Gandalf"))
                                .andExpect(jsonPath("$.options[3].id").value("aragorn"))
                                .andExpect(jsonPath("$.options[3].name").value("Aragorn"));

                verify(whoSaidItService).generateRound();
        }

        @Test
        void shouldNotExposeCorrectCharacterId() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);

                mockMvc.perform(get("/api/game/round"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.correctCharacterId").doesNotExist());
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
        }
}
