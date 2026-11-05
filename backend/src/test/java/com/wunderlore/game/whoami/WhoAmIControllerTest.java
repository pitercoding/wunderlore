package com.wunderlore.game.whoami;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.wunderlore.game.GameConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// The real service, registry and curated content behind the real controller: only the HTTP layer
// is a slice. Reaches the correct option the way a cheater cannot, by reading the bank.
// The creation limit has its own test; here it would only get in the way.
@WebMvcTest(
        controllers = WhoAmIController.class,
        properties = "wunderlore.rate-limit.who-am-i-game-creation.enabled=false")
@Import({
        GameConfiguration.class,
        WhoAmIService.class,
        WhoAmIGameRegistry.class,
        WhoAmICharacterBank.class
})
class WhoAmIControllerTest {

    private static final String GAMES_PATH = "/api/games/who-am-i/games";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private WhoAmICharacterBank bank;

    private MockHttpServletResponse create(String difficulty) throws Exception {
        return mockMvc.perform(post(GAMES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"difficulty\":\"" + difficulty + "\"}"))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse answer(String gameId, int round, int clue, String option)
            throws Exception {
        return mockMvc.perform(post(GAMES_PATH + "/{id}/answer", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundNumber\":" + round + ",\"clueNumber\":" + clue
                        + ",\"optionId\":\"" + option + "\"}"))
                .andReturn().getResponse();
    }

    private JsonNode read(MockHttpServletResponse response) throws Exception {
        return jsonMapper.readTree(response.getContentAsString());
    }

    private WhoAmICharacter characterOf(JsonNode round) {
        String firstClue = round.get("clues").get(0).asString();

        return Stream.of(WhoAmIDifficulty.values())
                .flatMap(d -> bank.find(d).stream())
                .filter(c -> c.clues().get(0).equals(firstClue))
                .findFirst()
                .orElseThrow();
    }

    private static String optionNamed(JsonNode round, String name) {
        for (JsonNode option : round.get("options")) {
            if (option.get("text").asString().equals(name)) {
                return option.get("id").asString();
            }
        }

        throw new AssertionError(name + " is not among the options");
    }

    private static String optionNotNamed(JsonNode round, String name) {
        for (JsonNode option : round.get("options")) {
            if (!option.get("text").asString().equals(name)) {
                return option.get("id").asString();
            }
        }

        throw new AssertionError("no other option");
    }

    @Test
    void shouldCreateAGameOfEachDifficultyWithTheFirstClueOnly() throws Exception {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            MockHttpServletResponse response = create(difficulty.name());
            JsonNode body = read(response);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body.get("difficulty").asString()).isEqualTo(difficulty.name());
            assertThat(body.get("roundNumber").asInt()).isEqualTo(1);
            assertThat(body.get("totalRounds").asInt()).isEqualTo(10);
            assertThat(body.get("clueNumber").asInt()).isEqualTo(1);
            assertThat(body.get("totalClues").asInt()).isEqualTo(5);
            assertThat(body.get("clues")).hasSize(1);
            assertThat(body.get("options")).hasSize(4);
            assertThat(body.get("score").asInt()).isZero();
            assertThat(body.get("maxScore").asInt()).isEqualTo(1000);
            assertThat(characterOf(body).difficulty()).isEqualTo(difficulty);
        }
    }

    @Test
    void shouldNotLeakTheAnswerOrFutureCluesBeforeTheRoundEnds() throws Exception {
        for (int run = 0; run < 20; run++) {
            JsonNode round = read(create("MEDIUM"));
            WhoAmICharacter character = characterOf(round);
            String json = round.toString();

            assertThat(new TreeSet<>(round.propertyNames())).containsExactly(
                    "clueNumber", "clues", "difficulty", "gameId", "maxScore", "options",
                    "roundNumber", "score", "totalClues", "totalRounds");

            for (JsonNode option : round.get("options")) {
                assertThat(new TreeSet<>(option.propertyNames())).containsExactly("id", "text");
                assertThat(option.get("id").asString()).matches("[a-d]");
            }

            // Not the character's id and not any later clue. The exact keys above leave no
            // room for a marker of the correct option.
            assertThat(json).doesNotContain(character.id());

            for (String later : character.clues().subList(1, 5)) {
                assertThat(json).doesNotContain(later);
            }

        }
    }

    @Test
    void shouldNotLeakWhatComesNextAfterAWrongAnswerEither() throws Exception {
        JsonNode round = read(create("EASY"));
        WhoAmICharacter character = characterOf(round);
        String gameId = round.get("gameId").asString();

        MockHttpServletResponse response =
                answer(gameId, 1, 1, optionNotNamed(round, character.name()));
        JsonNode body = read(response);
        String json = body.toString();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.get("correct").asBoolean()).isFalse();
        assertThat(body.get("roundFinished").asBoolean()).isFalse();
        assertThat(body.get("pointsAwarded").asInt()).isZero();
        assertThat(body.get("reveal").isNull()).isTrue();
        assertThat(body.get("result").isNull()).isTrue();
        assertThat(body.get("next").get("clueNumber").asInt()).isEqualTo(2);
        assertThat(body.get("next").get("clues")).hasSize(2);
        assertThat(body.get("next").get("options")).hasSize(4);
        assertThat(json).doesNotContain(character.id());

        for (String later : character.clues().subList(2, 5)) {
            assertThat(json).doesNotContain(later);
        }

        // The reloaded round says the same.
        JsonNode reloaded = read(mockMvc.perform(get(GAMES_PATH + "/{id}/round", gameId))
                .andReturn().getResponse());

        assertThat(reloaded.get("clueNumber").asInt()).isEqualTo(2);
        assertThat(reloaded.get("clues")).hasSize(2);
    }

    @Test
    void shouldPlayAFullGameAndScoreItOnTheServer() throws Exception {
        JsonNode round = read(create("HARD"));
        String gameId = round.get("gameId").asString();
        List<String> names = new ArrayList<>();
        int expected = 0;
        JsonNode last = null;

        for (int number = 1; number <= 10; number++) {
            WhoAmICharacter character = characterOf(round);

            assertThat(names).doesNotContain(character.name());
            names.add(character.name());

            // Alternate: identify at the first clue, or fail once and identify at the second.
            if (number % 2 == 0) {
                JsonNode miss = read(answer(gameId, number, 1, optionNotNamed(round, character.name())));

                assertThat(miss.get("correct").asBoolean()).isFalse();

                JsonNode hit = read(answer(gameId, number, 2,
                        optionNamed(miss.get("next"), character.name())));

                assertThat(hit.get("pointsAwarded").asInt()).isEqualTo(80);
                expected += 80;
                last = hit;
            } else {
                last = read(answer(gameId, number, 1, optionNamed(round, character.name())));

                assertThat(last.get("pointsAwarded").asInt()).isEqualTo(100);
                expected += 100;
            }

            assertThat(last.get("correct").asBoolean()).isTrue();
            assertThat(last.get("roundFinished").asBoolean()).isTrue();
            assertThat(last.get("reveal").get("characterName").asString()).isEqualTo(character.name());
            assertThat(last.get("reveal").get("clues")).hasSize(5);
            assertThat(last.get("score").asInt()).isEqualTo(expected);
            assertThat(last.get("next").isNull()).isTrue();

            if (number < 10) {
                assertThat(last.get("gameFinished").asBoolean()).isFalse();
                assertThat(last.get("result").isNull()).isTrue();
                round = read(mockMvc.perform(get(GAMES_PATH + "/{id}/round", gameId))
                        .andReturn().getResponse());
                assertThat(round.get("roundNumber").asInt()).isEqualTo(number + 1);
                assertThat(round.get("clueNumber").asInt()).isEqualTo(1);
                assertThat(round.get("score").asInt()).isEqualTo(expected);
            }
        }

        assertThat(expected).isEqualTo(900);
        assertThat(last.get("gameFinished").asBoolean()).isTrue();
        assertThat(last.get("result").get("score").asInt()).isEqualTo(900);

        JsonNode result = read(mockMvc.perform(get(GAMES_PATH + "/{id}/result", gameId))
                .andReturn().getResponse());

        assertThat(result.get("score").asInt()).isEqualTo(900);
        assertThat(result.get("maxScore").asInt()).isEqualTo(1000);
        assertThat(result.get("percentage").asInt()).isEqualTo(90);
        assertThat(result.get("identified").asInt()).isEqualTo(10);
        assertThat(result.get("unidentified").asInt()).isZero();
        assertThat(result.get("incorrectAnswers").asInt()).isEqualTo(5);
        assertThat(result.get("rounds")).hasSize(10);
        assertThat(result.get("rounds").get(0).get("characterName").asString())
                .isEqualTo(names.get(0));
    }

    @Test
    void shouldEndARoundWithZeroPointsAfterFiveWrongAnswers() throws Exception {
        JsonNode round = read(create("EASY"));
        WhoAmICharacter character = characterOf(round);
        String gameId = round.get("gameId").asString();
        JsonNode body = null;
        JsonNode current = round;

        for (int clue = 1; clue <= 5; clue++) {
            body = read(answer(gameId, 1, clue, optionNotNamed(current, character.name())));
            current = body.get("next");
        }

        assertThat(body.get("correct").asBoolean()).isFalse();
        assertThat(body.get("roundFinished").asBoolean()).isTrue();
        assertThat(body.get("pointsAwarded").asInt()).isZero();
        assertThat(body.get("reveal").get("characterName").asString()).isEqualTo(character.name());
        assertThat(body.get("next").isNull()).isTrue();
    }

    @Test
    void shouldRefuseADuplicateSubmissionAndAnAnswerAfterTheGameIsOver() throws Exception {
        JsonNode round = read(create("EASY"));
        String gameId = round.get("gameId").asString();
        JsonNode body = null;

        for (int number = 1; number <= 10; number++) {
            WhoAmICharacter character = characterOf(round);

            body = read(answer(gameId, number, 1, optionNamed(round, character.name())));

            if (number == 1) {
                // The same request again: 409, and the score is not counted twice.
                assertThat(answer(gameId, 1, 1, "a").getStatus()).isEqualTo(409);
            }

            if (number < 10) {
                round = read(mockMvc.perform(get(GAMES_PATH + "/{id}/round", gameId))
                        .andReturn().getResponse());
                assertThat(round.get("score").asInt()).isEqualTo(number * 100);
            }
        }

        assertThat(body.get("gameFinished").asBoolean()).isTrue();
        assertThat(answer(gameId, 10, 1, "a").getStatus()).isEqualTo(409);
        assertThat(mockMvc.perform(get(GAMES_PATH + "/{id}/round", gameId))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void shouldRefuseTheResultOfAGameThatIsStillOpen() throws Exception {
        String gameId = read(create("EASY")).get("gameId").asString();

        assertThat(mockMvc.perform(get(GAMES_PATH + "/{id}/result", gameId))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void shouldAnswerNotFoundForUnknownGames() throws Exception {
        String unknown = "00000000-0000-4000-8000-000000000000";

        assertThat(answer(unknown, 1, 1, "a").getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(GAMES_PATH + "/{id}/round", unknown))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(GAMES_PATH + "/{id}/result", unknown))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void shouldRejectInvalidBodiesWithoutEchoingThem() throws Exception {
        for (String body : List.of(
                "", "{}", "{\"difficulty\":null}", "{\"difficulty\":\"IMPOSSIBLE\"}",
                "{\"difficulty\":\"easy\"}", "[]", "not json")) {
            MockHttpServletResponse response = mockMvc.perform(post(GAMES_PATH)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();

            assertThat(response.getStatus()).as(body).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("Invalid request");
        }
    }

    @Test
    void shouldRejectInvalidAnswerBodies() throws Exception {
        String gameId = read(create("EASY")).get("gameId").asString();

        for (String body : List.of(
                "{}",
                "{\"roundNumber\":1,\"clueNumber\":1}",
                "{\"roundNumber\":0,\"clueNumber\":1,\"optionId\":\"a\"}",
                "{\"roundNumber\":11,\"clueNumber\":1,\"optionId\":\"a\"}",
                "{\"roundNumber\":1,\"clueNumber\":6,\"optionId\":\"a\"}",
                "{\"roundNumber\":1,\"clueNumber\":1,\"optionId\":\"e\"}",
                "{\"roundNumber\":1,\"clueNumber\":1,\"optionId\":\"<script>\"}",
                "{\"roundNumber\":\"abc\",\"clueNumber\":1,\"optionId\":\"a\"}")) {
            MockHttpServletResponse response = mockMvc.perform(post(GAMES_PATH + "/{id}/answer", gameId)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();

            assertThat(response.getStatus()).as(body).isEqualTo(400);
            assertThat(response.getContentAsString()).doesNotContain("script");
        }
    }

    @Test
    void shouldIgnoreAnyScoreOrCharacterAClientTriesToSend() throws Exception {
        JsonNode round = read(create("EASY"));
        WhoAmICharacter character = characterOf(round);
        String gameId = round.get("gameId").asString();

        MockHttpServletResponse response = mockMvc.perform(post(GAMES_PATH + "/{id}/answer", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundNumber\":1,\"clueNumber\":1,\"optionId\":\""
                        + optionNotNamed(round, character.name())
                        + "\",\"score\":1000,\"correct\":true,\"pointsAwarded\":100,\"difficulty\":\"EASY\"}"))
                .andReturn().getResponse();
        JsonNode body = read(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.get("correct").asBoolean()).isFalse();
        assertThat(body.get("score").asInt()).isZero();
    }
}
