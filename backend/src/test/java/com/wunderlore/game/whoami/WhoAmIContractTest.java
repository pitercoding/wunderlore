package com.wunderlore.game.whoami;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import com.wunderlore.game.whoami.WhoAmIGame.AnswerOutcome;
import com.wunderlore.game.whoami.WhoAmIGame.NextClue;
import com.wunderlore.game.whoami.WhoAmIGame.Option;
import com.wunderlore.game.whoami.WhoAmIGame.Result;
import com.wunderlore.game.whoami.WhoAmIGame.Reveal;
import com.wunderlore.game.whoami.WhoAmIGame.RoundSummary;
import com.wunderlore.game.whoami.WhoAmIGame.RoundView;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Checks the public HTTP contract: the bodies the controller really writes, through the
// application's configured JsonMapper, against the fixtures shared with the frontend
// (contracts/who-am-i).
@WebMvcTest(WhoAmIController.class)
class WhoAmIContractTest {

    // Maven runs the tests from the backend directory.
    private static final Path CONTRACTS = Path.of("..", "contracts", "who-am-i");

    private static final String GAMES_PATH = "/api/games/who-am-i/games";

    private static final String GAME_ID = "3f2b8c1e-5a47-4d0e-9c36-7b1e2a9d4f60";

    private static final List<String> FROM_FRODO = List.of(
            "Both of this character's parents died in a boating accident long before any adventure began.",
            "A beloved older relative adopted this character, then left behind his home and a small golden trinket.",
            "A wound from a cursed blade, taken beneath a ruined watchtower on a hill, never fully healed.",
            "At a council in Rivendell, this character volunteered to carry a dangerous object to the land where it was made.",
            "At the Cracks of Doom this character could not give up the Ring willingly, and lost a finger to the creature who seized it.");

    private static final List<String> FROM_SARUMAN = List.of(
            "Head of an order sent from across the Sea, this character was a deep student of the lore of the Rings.",
            "This character made his home in a black tower inside a ringed fortress, where he kept a seeing-stone.",
            "Gazing into a stone of far-seeing, this character was ensnared by his greater enemy and betrayed his task.",
            "This character's fires felled forests, and in pits beneath his tower he bred a stronger kind of Orc.",
            "After his fall, this character took over the Shire in disguise, and was finally killed by his own former servant.");

    private static final List<RoundSummary> ROUNDS = List.of(
            new RoundSummary(1, "Frodo Baggins", 2, true, 80, 1),
            new RoundSummary(10, "Saruman", 5, false, 0, 5));

    private static final Result RESULT =
            new Result(WhoAmIDifficulty.EASY, 10, 720, 1000, 72, 9, 9, 1, ROUNDS);

    // Required by LoreUnavailableAdvice and the filters, which the web slice loads.
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
    private WhoAmIService service;

    @MockitoBean
    private WhoAmIGameRegistry registry;

    private static RoundView roundView() {
        return new RoundView(
                WhoAmIDifficulty.EASY,
                1,
                10,
                1,
                5,
                FROM_FRODO.subList(0, 1),
                List.of(
                        new Option("a", "Samwise Gamgee"),
                        new Option("b", "Frodo Baggins"),
                        new Option("c", "Bilbo Baggins"),
                        new Option("d", "Gollum")),
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
    void incorrectAnswerMatchesItsFixtureAndCarriesOnlyTheNextClue() throws Exception {
        when(registry.answer(eq(GAME_ID), anyInt(), anyInt(), any())).thenReturn(new AnswerOutcome(
                1, 1, "a", false, false, 0, 0, false,
                new NextClue(2, FROM_FRODO.subList(0, 2), List.of(
                        new Option("a", "Frodo Baggins"),
                        new Option("b", "Boromir"),
                        new Option("c", "Gimli"),
                        new Option("d", "Legolas"))),
                null,
                null));

        JsonNode body = answer();

        assertAnswerShape(body);
        assertThat(body).isEqualTo(fixture("answer-incorrect.json"));
        assertThat(body.get("reveal").isNull()).isTrue();
        assertThat(body.get("result").isNull()).isTrue();
    }

    @Test
    void correctAnswerMatchesItsFixtureAndRevealsTheCharacter() throws Exception {
        when(registry.answer(eq(GAME_ID), anyInt(), anyInt(), any())).thenReturn(new AnswerOutcome(
                1, 2, "a", true, true, 80, 80, false,
                null,
                new Reveal("Frodo Baggins", FROM_FRODO),
                null));

        JsonNode body = answer();

        assertAnswerShape(body);
        assertThat(body).isEqualTo(fixture("answer-correct.json"));
        assertThat(body.get("next").isNull()).isTrue();
        assertThat(body.get("result").isNull()).isTrue();
    }

    @Test
    void finalAnswerMatchesItsFixtureAndCarriesTheResult() throws Exception {
        when(registry.answer(eq(GAME_ID), anyInt(), anyInt(), any())).thenReturn(new AnswerOutcome(
                10, 5, "d", false, true, 0, 720, true,
                null,
                new Reveal("Saruman", FROM_SARUMAN),
                RESULT));

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
                    "characterName", "cluesUsed", "identified", "points", "roundNumber", "wrongAttempts");
        }
    }

    private static final List<String> RESULT_FIELDS = List.of(
            "difficulty", "gameId", "identified", "incorrectAnswers", "maxScore", "percentage",
            "rounds", "score", "totalRounds", "unidentified");

    private JsonNode answer() throws Exception {
        return read(mockMvc.perform(post(GAMES_PATH + "/{id}/answer", GAME_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundNumber\":1,\"clueNumber\":1,\"optionId\":\"a\"}"))
                .andReturn().getResponse().getContentAsString());
    }

    // Explicit, so a failure names the property that changed. The character and the correct option
    // are deliberately not here: they never travel with a round that is waiting for an answer.
    private static void assertRoundShape(JsonNode body) {
        assertThat(fieldNames(body)).containsExactly(
                "clueNumber", "clues", "difficulty", "gameId", "maxScore", "options",
                "roundNumber", "score", "totalClues", "totalRounds");
        assertThat(body.get("options")).hasSize(4);

        for (JsonNode option : body.get("options")) {
            assertThat(fieldNames(option)).containsExactly("id", "text");
        }
    }

    private static void assertAnswerShape(JsonNode body) {
        assertThat(fieldNames(body)).containsExactly(
                "clueNumber", "correct", "gameFinished", "gameId", "next", "pointsAwarded", "result",
                "reveal", "roundFinished", "roundNumber", "score", "submittedOptionId");
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
