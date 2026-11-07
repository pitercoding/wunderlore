package com.wunderlore.game.whereinmiddleearth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.AnswerOutcome;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.Position;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.Result;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.Reveal;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.RoundSummary;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.RoundView;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Checks the public HTTP contract: the bodies the controller really writes, through the
// application's configured JsonMapper, against the fixtures shared with the frontend
// (contracts/where-in-middle-earth).
@WebMvcTest(WhereInMiddleEarthController.class)
class WhereInMiddleEarthContractTest {

    // Maven runs the tests from the backend directory.
    private static final Path CONTRACTS = Path.of("..", "contracts", "where-in-middle-earth");

    private static final String GAMES_PATH = "/api/games/where-in-middle-earth/games";

    private static final String GAME_ID = "3f2b8c1e-5a47-4d0e-9c36-7b1e2a9d4f60";

    private static final Reveal RIVENDELL = new Reveal(
            "Rivendell",
            "Eriador",
            "Third Age",
            "Rivendell (Imladris) lay on the eastern edge of Eriador, in the foothills of the Misty "
                    + "Mountains, at the edge of a narrow gorge of the river Bruinen.",
            385.0,
            226.0);

    private static final Reveal MOUNT_DOOM = new Reveal(
            "Mount Doom",
            "Mordor",
            "Third Age",
            "Mount Doom (Orodruin) stood alone on the Plateau of Gorgoroth in Mordor, about thirty "
                    + "miles west of Barad-dûr.",
            722.0,
            545.0);

    private static final List<RoundSummary> ROUNDS = List.of(
            new RoundSummary(1, "Rivendell", WhereInMiddleEarthAccuracy.EXCELLENT, 6.0, 100),
            new RoundSummary(10, "Mount Doom", WhereInMiddleEarthAccuracy.FAR, 602.2, 0));

    private static final Result RESULT = new Result(
            WhereInMiddleEarthDifficulty.EASY, 10, 720, 1000, 72, 9, 61.3, ROUNDS);

    private static final List<String> RESULT_FIELDS = List.of(
            "averageDistance", "closeHits", "difficulty", "gameId", "maxScore", "percentage",
            "rounds", "score", "totalRounds");

    // Required by the filters, which the web slice loads.
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
    private WhereInMiddleEarthService service;

    @MockitoBean
    private WhereInMiddleEarthGameRegistry registry;

    private static RoundView roundView() {
        return new RoundView(
                WhereInMiddleEarthDifficulty.EASY,
                1,
                10,
                "Rivendell",
                "Elrond's hidden refuge, the Last Homely House.",
                0,
                1000);
    }

    @Test
    void createdGameMatchesTheRoundFixture() throws Exception {
        when(registry.register(any())).thenReturn(GAME_ID);
        when(registry.currentRound(GAME_ID)).thenReturn(roundView());

        JsonNode body = read(mockMvc.perform(post(GAMES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"difficulty\":\"EASY\"}"))
                .andReturn().getResponse().getContentAsString());

        assertRoundShape(body);
        assertThat(body).isEqualTo(fixture("round.json"));
    }

    @Test
    void currentRoundMatchesTheRoundFixture() throws Exception {
        when(registry.currentRound(GAME_ID)).thenReturn(roundView());

        JsonNode body = read(mockMvc.perform(get(GAMES_PATH + "/{id}/round", GAME_ID))
                .andReturn().getResponse().getContentAsString());

        assertRoundShape(body);
        assertThat(body).isEqualTo(fixture("round.json"));
    }

    @Test
    void answerMatchesItsFixtureAndRevealsThePlace() throws Exception {
        when(registry.answer(eq(GAME_ID), anyInt(), anyDouble(), anyDouble())).thenReturn(
                new AnswerOutcome(
                        1, new Position(380.5, 230.0), 6.0, WhereInMiddleEarthAccuracy.EXCELLENT,
                        100, 100, RIVENDELL, false, null));

        JsonNode body = answer();

        assertAnswerShape(body);
        assertThat(body).isEqualTo(fixture("answer.json"));
        assertThat(body.get("result").isNull()).isTrue();
    }

    @Test
    void finalAnswerMatchesItsFixtureAndCarriesTheResult() throws Exception {
        when(registry.answer(eq(GAME_ID), anyInt(), anyDouble(), anyDouble())).thenReturn(
                new AnswerOutcome(
                        10, new Position(120.0, 560.0), 602.2, WhereInMiddleEarthAccuracy.FAR,
                        0, 720, MOUNT_DOOM, true, RESULT));

        JsonNode body = answer();

        assertAnswerShape(body);
        assertThat(body).isEqualTo(fixture("answer-final.json"));
        assertThat(fieldNames(body.get("result"))).containsExactlyElementsOf(RESULT_FIELDS);
    }

    @Test
    void resultMatchesTheResultFixture() throws Exception {
        when(registry.result(GAME_ID)).thenReturn(RESULT);

        JsonNode body = read(mockMvc.perform(get(GAMES_PATH + "/{id}/result", GAME_ID))
                .andReturn().getResponse().getContentAsString());

        assertThat(fieldNames(body)).containsExactlyElementsOf(RESULT_FIELDS);
        assertThat(body).isEqualTo(fixture("result.json"));

        for (JsonNode round : body.get("rounds")) {
            assertThat(fieldNames(round)).containsExactly(
                    "accuracy", "distance", "locationName", "points", "roundNumber");
        }
    }

    private JsonNode answer() throws Exception {
        return read(mockMvc.perform(post(GAMES_PATH + "/{id}/answer", GAME_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundNumber\":1,\"x\":380.5,\"y\":230.0}"))
                .andReturn().getResponse().getContentAsString());
    }

    // Explicit, so a failure names the property that changed. The coordinates of the place, its
    // region and its explanation are deliberately not here: they never travel with a round that
    // is waiting for an answer.
    private static void assertRoundShape(JsonNode body) {
        assertThat(fieldNames(body)).containsExactly(
                "description", "difficulty", "gameId", "locationName", "maxScore", "roundNumber",
                "score", "totalRounds");
    }

    private static void assertAnswerShape(JsonNode body) {
        assertThat(fieldNames(body)).containsExactly(
                "accuracy", "distance", "gameFinished", "gameId", "pointsAwarded", "result",
                "reveal", "roundNumber", "score", "submitted");
        assertThat(fieldNames(body.get("reveal"))).containsExactly(
                "explanation", "locationName", "period", "regionName", "x", "y");
        assertThat(fieldNames(body.get("submitted"))).containsExactly("x", "y");
    }

    private JsonNode read(String json) {
        return jsonMapper.readTree(json);
    }

    private JsonNode fixture(String name) throws IOException {
        return jsonMapper.readTree(Files.readString(CONTRACTS.resolve(name)));
    }

    // Sorted, so the check does not depend on property order.
    private static Collection<String> fieldNames(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }
}
