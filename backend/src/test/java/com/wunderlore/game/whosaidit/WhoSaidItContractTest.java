package com.wunderlore.game.whosaidit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Checks the public HTTP contract: the body the controller really writes, through
// the application's configured JsonMapper, against the fixtures shared with the
// frontend (contracts/who-said-it). Going through the controller rather than
// serializing the DTO directly also covers the message converters clients see.
@WebMvcTest(WhoSaidItController.class)
class WhoSaidItContractTest {

        // Maven runs the tests from the backend directory.
        private static final Path CONTRACTS = Path.of("..", "contracts", "who-said-it");

        private static final String ROUNDS_PATH = "/api/games/who-said-it/rounds";

        private static final String ROUND_ID = "3f2b8c1e-5a47-4d0e-9c36-7b1e2a9d4f60";

        private static final String GANDALF_ID = "5cd99d4bde30eff6ebccfea0";

        private static final String FRODO_ID = "5cd99d4bde30eff6ebccfc15";

        private static final WhoSaidItRound ROUND = new WhoSaidItRound(
                        "Fly, you fools!",
                        List.of(
                                        new WhoSaidItRound.CharacterOption(GANDALF_ID, "Gandalf"),
                                        new WhoSaidItRound.CharacterOption(FRODO_ID, "Frodo Baggins"),
                                        new WhoSaidItRound.CharacterOption(
                                                        "5cd99d4bde30eff6ebccfd23", "Samwise Gamgee"),
                                        new WhoSaidItRound.CharacterOption(
                                                        "5cd99d4bde30eff6ebccfd81", "Aragorn II Elessar")),
                        GANDALF_ID);

        // Required by LoreUnavailableAdvice, which the web slice loads with the controller.
        @TestConfiguration
        static class ClockConfiguration {

                @Bean
                Clock clock() {
                        return Clock.systemUTC();
                }
        }

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JsonMapper jsonMapper;

        @MockitoBean
        private WhoSaidItService whoSaidItService;

        @MockitoBean
        private WhoSaidItRoundRegistry roundRegistry;

        @Test
        void createdRoundMatchesFixture() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);
                when(roundRegistry.register(ROUND)).thenReturn(ROUND_ID);

                JsonNode body = createRoundBody();

                assertThat(body).isEqualTo(fixture("round-created.json"));
                // Explicit, so a failure names the property that changed.
                assertThat(fieldNames(body)).containsExactly("options", "quote", "roundId");
                assertThat(body.get("roundId").isString()).isTrue();
                assertThat(body.get("quote").isString()).isTrue();
                assertThat(body.get("options").isArray()).isTrue();
                assertThat(body.get("options")).hasSize(4);
                for (JsonNode option : body.get("options")) {
                        assertThat(fieldNames(option)).containsExactly("id", "name");
                        assertThat(option.get("id").isString()).isTrue();
                        assertThat(option.get("name").isString()).isTrue();
                }
        }

        @Test
        void createdRoundNeverExposesTheCorrectCharacter() throws Exception {
                when(whoSaidItService.generateRound()).thenReturn(ROUND);
                when(roundRegistry.register(ROUND)).thenReturn(ROUND_ID);

                assertThat(fieldNames(createRoundBody())).doesNotContain("correctCharacterId");
        }

        @Test
        void correctAnswerMatchesFixture() throws Exception {
                when(roundRegistry.answer(ROUND_ID, GANDALF_ID)).thenReturn(
                                new WhoSaidItAnswerResponse(ROUND_ID, GANDALF_ID, true, GANDALF_ID));

                JsonNode body = answerBody(GANDALF_ID);

                assertThat(body).isEqualTo(fixture("answer-correct.json"));
                assertAnswerShape(body);
                assertThat(body.get("correct").booleanValue()).isTrue();
        }

        @Test
        void incorrectAnswerMatchesFixture() throws Exception {
                when(roundRegistry.answer(ROUND_ID, FRODO_ID)).thenReturn(
                                new WhoSaidItAnswerResponse(ROUND_ID, FRODO_ID, false, GANDALF_ID));

                JsonNode body = answerBody(FRODO_ID);

                assertThat(body).isEqualTo(fixture("answer-incorrect.json"));
                assertAnswerShape(body);
                assertThat(body.get("correct").booleanValue()).isFalse();
        }

        private static void assertAnswerShape(JsonNode body) {
                assertThat(fieldNames(body)).containsExactly(
                                "correct", "correctCharacterId", "roundId", "submittedCharacterId");
                assertThat(body.get("roundId").isString()).isTrue();
                assertThat(body.get("submittedCharacterId").isString()).isTrue();
                assertThat(body.get("correct").isBoolean()).isTrue();
                assertThat(body.get("correctCharacterId").isString()).isTrue();
        }

        private JsonNode createRoundBody() throws Exception {
                return jsonMapper.readTree(mockMvc.perform(post(ROUNDS_PATH))
                                .andReturn().getResponse().getContentAsString());
        }

        private JsonNode answerBody(String characterId) throws Exception {
                return jsonMapper.readTree(mockMvc.perform(
                                post(ROUNDS_PATH + "/{roundId}/answer", ROUND_ID)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"characterId\":\"" + characterId + "\"}"))
                                .andReturn().getResponse().getContentAsString());
        }

        private JsonNode fixture(String name) throws IOException {
                return jsonMapper.readTree(Files.readString(CONTRACTS.resolve(name)));
        }

        // Sorted, so the check does not depend on property order.
        private static Collection<String> fieldNames(JsonNode node) {
                return new TreeSet<>(node.propertyNames());
        }
}
