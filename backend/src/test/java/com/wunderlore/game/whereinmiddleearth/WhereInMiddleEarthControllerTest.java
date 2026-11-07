package com.wunderlore.game.whereinmiddleearth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
// is a slice. Finds the real position of each place the way a cheater cannot, by reading the bank.
// The creation limit has its own test; here it would only get in the way.
@WebMvcTest(
        controllers = WhereInMiddleEarthController.class,
        properties = "wunderlore.rate-limit.where-in-middle-earth-game-creation.enabled=false")
@Import({
        GameConfiguration.class,
        WhereInMiddleEarthService.class,
        WhereInMiddleEarthGameRegistry.class,
        WhereInMiddleEarthLocationBank.class
})
class WhereInMiddleEarthControllerTest {

    private static final String GAMES_PATH = "/api/games/where-in-middle-earth/games";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private WhereInMiddleEarthLocationBank bank;

    private MockHttpServletResponse create(String difficulty) throws Exception {
        return mockMvc.perform(post(GAMES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"difficulty\":\"" + difficulty + "\"}"))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse answer(String gameId, String body) throws Exception {
        return mockMvc.perform(post(GAMES_PATH + "/{id}/answer", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse answer(String gameId, int round, double x, double y)
            throws Exception {
        return answer(gameId, "{\"roundNumber\":" + round + ",\"x\":" + x + ",\"y\":" + y + "}");
    }

    private JsonNode read(MockHttpServletResponse response) throws Exception {
        return jsonMapper.readTree(response.getContentAsString());
    }

    private WhereInMiddleEarthLocation placeOf(JsonNode round) {
        String name = round.get("locationName").asString();
        WhereInMiddleEarthDifficulty difficulty =
                WhereInMiddleEarthDifficulty.valueOf(round.get("difficulty").asString());

        return bank.find(difficulty).stream()
                .filter(place -> place.name().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void shouldCreateAGameAndAskTheFirstPlaceWithoutSayingWhereItIs() throws Exception {
        MockHttpServletResponse response = create("EASY");
        JsonNode round = read(response);
        WhereInMiddleEarthLocation place = placeOf(round);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(round.get("gameId").asString()).matches("[0-9a-f-]{36}");
        assertThat(round.get("roundNumber").asInt()).isEqualTo(1);
        assertThat(round.get("totalRounds").asInt()).isEqualTo(10);
        assertThat(round.get("score").asInt()).isZero();
        assertThat(round.get("maxScore").asInt()).isEqualTo(1000);
        assertThat(round.get("description").asString()).isEqualTo(place.description());

        // Nothing that locates the place: no coordinate field, and none of its values in the body.
        assertThat(round.propertyNames()).doesNotContain("x", "y", "region", "explanation");

        String body = response.getContentAsString();

        assertThat(body).doesNotContain(place.explanation());
        assertThat(body).doesNotContain(String.valueOf(place.x()));
        assertThat(body).doesNotContain(String.valueOf(place.y()));
    }

    @Test
    void shouldNotLeakTheCoordinatesOnReadingTheCurrentRound() throws Exception {
        JsonNode created = read(create("HARD"));
        String id = created.get("gameId").asString();
        WhereInMiddleEarthLocation place = placeOf(created);

        MockHttpServletResponse response = mockMvc.perform(get(GAMES_PATH + "/{id}/round", id))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(read(response)).isEqualTo(created);
        assertThat(response.getContentAsString()).doesNotContain("\"x\"", "\"y\"")
                .doesNotContain(String.valueOf(place.x()));
    }

    @Test
    void shouldAskTenDifferentPlacesAndEndWithTheResult() throws Exception {
        for (WhereInMiddleEarthDifficulty difficulty : WhereInMiddleEarthDifficulty.values()) {
            JsonNode round = read(create(difficulty.name()));
            String id = round.get("gameId").asString();
            java.util.Set<String> asked = new java.util.HashSet<>();
            JsonNode last = null;

            for (int number = 1; number <= 10; number++) {
                assertThat(round.get("roundNumber").asInt()).isEqualTo(number);

                WhereInMiddleEarthLocation place = placeOf(round);

                assertThat(asked.add(place.id())).as("no repeated place").isTrue();

                last = read(answer(id, number, place.x(), place.y()));

                assertThat(last.get("accuracy").asString()).isEqualTo("EXCELLENT");
                assertThat(last.get("pointsAwarded").asInt()).isEqualTo(100);
                assertThat(last.get("reveal").get("locationName").asString()).isEqualTo(place.name());
                assertThat(last.get("reveal").get("x").asDouble()).isEqualTo(place.x());
                assertThat(last.get("reveal").get("y").asDouble()).isEqualTo(place.y());
                assertThat(last.get("reveal").get("regionName").asString()).isNotBlank();
                assertThat(last.get("reveal").get("explanation").asString())
                        .isEqualTo(place.explanation());

                if (number < 10) {
                    round = read(mockMvc.perform(get(GAMES_PATH + "/{id}/round", id))
                            .andReturn().getResponse());
                }
            }

            assertThat(last.get("gameFinished").asBoolean()).isTrue();
            assertThat(last.get("result").get("score").asInt()).isEqualTo(1000);
            assertThat(last.get("result").get("rounds")).hasSize(10);

            JsonNode result = read(mockMvc.perform(get(GAMES_PATH + "/{id}/result", id))
                    .andReturn().getResponse());

            assertThat(result.get("score").asInt()).isEqualTo(1000);
            assertThat(result.get("percentage").asInt()).isEqualTo(100);
            assertThat(result.get("closeHits").asInt()).isEqualTo(10);
            assertThat(result.get("averageDistance").asDouble()).isZero();
        }
    }

    @Test
    void shouldScoreTheDistanceOnTheServerAndIgnoreAnyScoreTheClientSends() throws Exception {
        JsonNode round = read(create("EASY"));
        String id = round.get("gameId").asString();
        WhereInMiddleEarthLocation place = placeOf(round);

        // A click 40 units away is GOOD (60 points) whatever the client claims about its score.
        MockHttpServletResponse response = answer(id, "{\"roundNumber\":1,\"x\":" + (place.x() - 40)
                + ",\"y\":" + place.y() + ",\"score\":1000,\"points\":100,\"accuracy\":\"EXCELLENT\"}");
        JsonNode outcome = read(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(outcome.get("distance").asDouble()).isEqualTo(40.0);
        assertThat(outcome.get("accuracy").asString()).isEqualTo("GOOD");
        assertThat(outcome.get("pointsAwarded").asInt()).isEqualTo(60);
        assertThat(outcome.get("score").asInt()).isEqualTo(60);
    }

    @Test
    void shouldRefuseTheSameRoundTwiceWith409AndScoreItOnce() throws Exception {
        JsonNode round = read(create("MEDIUM"));
        String id = round.get("gameId").asString();
        WhereInMiddleEarthLocation place = placeOf(round);

        assertThat(answer(id, 1, place.x(), place.y()).getStatus()).isEqualTo(200);

        MockHttpServletResponse second = answer(id, 1, place.x(), place.y());

        assertThat(second.getStatus()).isEqualTo(409);
        assertThat(second.getContentAsString()).contains("That round is not awaiting an answer");
        assertThat(read(mockMvc.perform(get(GAMES_PATH + "/{id}/round", id))
                .andReturn().getResponse()).get("score").asInt()).isEqualTo(100);
    }

    @Test
    void shouldRefuseARoundOutOfOrderWith409() throws Exception {
        JsonNode round = read(create("EASY"));
        String id = round.get("gameId").asString();

        assertThat(answer(id, 2, 100.0, 100.0).getStatus()).isEqualTo(409);
        assertThat(answer(id, 10, 100.0, 100.0).getStatus()).isEqualTo(409);
    }

    @Test
    void shouldRefuseInvalidBodiesWith400() throws Exception {
        String id = read(create("EASY")).get("gameId").asString();

        for (String body : new String[] {
                "{\"roundNumber\":1,\"x\":-1,\"y\":10}",
                "{\"roundNumber\":1,\"x\":1000.5,\"y\":10}",
                "{\"roundNumber\":1,\"x\":10,\"y\":-0.1}",
                "{\"roundNumber\":1,\"x\":10,\"y\":700.5}",
                "{\"roundNumber\":1,\"x\":10}",
                "{\"roundNumber\":1,\"y\":10}",
                "{\"x\":10,\"y\":10}",
                "{\"roundNumber\":0,\"x\":10,\"y\":10}",
                "{\"roundNumber\":11,\"x\":10,\"y\":10}",
                "{\"roundNumber\":1,\"x\":\"abc\",\"y\":10}",
                "{\"roundNumber\":1,\"x\":null,\"y\":10}",
                "not json",
                "{}"
        }) {
            MockHttpServletResponse response = answer(id, body);

            assertThat(response.getStatus()).as(body).isEqualTo(400);
            assertThat(response.getContentAsString()).as(body).contains("Invalid request");
        }

        // None of them consumed the round.
        assertThat(read(mockMvc.perform(get(GAMES_PATH + "/{id}/round", id))
                .andReturn().getResponse()).get("roundNumber").asInt()).isEqualTo(1);
    }

    @Test
    void shouldRefuseAnUnknownDifficultyWith400() throws Exception {
        assertThat(create("IMPOSSIBLE").getStatus()).isEqualTo(400);
        assertThat(mockMvc.perform(post(GAMES_PATH)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void shouldAnswer404ForAnUnknownGame() throws Exception {
        String unknown = "00000000-0000-4000-8000-000000000000";

        assertThat(mockMvc.perform(get(GAMES_PATH + "/{id}/round", unknown))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(GAMES_PATH + "/{id}/result", unknown))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(answer(unknown, 1, 10.0, 10.0).getStatus()).isEqualTo(404);
    }

    @Test
    void shouldRefuseTheResultBeforeTheGameIsOverWith409() throws Exception {
        String id = read(create("EASY")).get("gameId").asString();

        MockHttpServletResponse response = mockMvc.perform(get(GAMES_PATH + "/{id}/result", id))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("Game has not finished yet");
    }

    @Test
    void shouldRefuseMoreAnswersAfterTheLastRoundWith409() throws Exception {
        JsonNode round = read(create("EASY"));
        String id = round.get("gameId").asString();

        for (int number = 1; number <= 10; number++) {
            WhereInMiddleEarthLocation place = placeOf(round);

            answer(id, number, place.x(), place.y());

            if (number < 10) {
                round = read(mockMvc.perform(get(GAMES_PATH + "/{id}/round", id))
                        .andReturn().getResponse());
            }
        }

        assertThat(answer(id, 10, 10.0, 10.0).getStatus()).isEqualTo(409);
        assertThat(mockMvc.perform(get(GAMES_PATH + "/{id}/round", id))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
    }
}
