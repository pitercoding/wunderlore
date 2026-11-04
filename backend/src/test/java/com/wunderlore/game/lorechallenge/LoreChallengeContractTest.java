package com.wunderlore.game.lorechallenge;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.wunderlore.game.lorechallenge.LoreChallengeMatch.AnswerOutcome;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.Option;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.QuestionView;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.Result;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Checks the public HTTP contract: the bodies the controller really writes, through the
// application's configured JsonMapper, against the fixtures shared with the frontend
// (contracts/lore-challenge).
@WebMvcTest(LoreChallengeController.class)
class LoreChallengeContractTest {

    // Maven runs the tests from the backend directory.
    private static final Path CONTRACTS = Path.of("..", "contracts", "lore-challenge");

    private static final String MATCHES_PATH = "/api/games/lore-challenge/matches";

    private static final String MATCH_ID = "3f2b8c1e-5a47-4d0e-9c36-7b1e2a9d4f60";

    private static final String QUESTION_ID = "characters-easy-01";

    private static final QuestionView VIEW = new QuestionView(
            "ALL",
            LoreChallengeDifficulty.EASY,
            1,
            10,
            QUESTION_ID,
            LoreChallengeCategory.CHARACTERS,
            "Who carries the One Ring to Mount Doom?",
            List.of(
                    new Option("a", "Bilbo Baggins"),
                    new Option("b", "Frodo Baggins"),
                    new Option("c", "Samwise Gamgee"),
                    new Option("d", "Gandalf")));

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
    private LoreChallengeService service;

    @MockitoBean
    private LoreChallengeMatchRegistry registry;

    @Test
    void createdMatchMatchesTheQuestionFixture() throws Exception {
        when(registry.register(org.mockito.ArgumentMatchers.any())).thenReturn(MATCH_ID);
        when(registry.currentQuestion(MATCH_ID)).thenReturn(VIEW);

        JsonNode body = read(mockMvc.perform(post(MATCHES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"ALL\",\"difficulty\":\"EASY\"}"))
                .andReturn().getResponse().getContentAsString());

        assertQuestionShape(body);
        assertThat(body).isEqualTo(fixture("question.json"));
    }

    @Test
    void currentQuestionMatchesTheQuestionFixture() throws Exception {
        when(registry.currentQuestion(MATCH_ID)).thenReturn(VIEW);

        JsonNode body = read(mockMvc.perform(get(MATCHES_PATH + "/{id}/question", MATCH_ID))
                .andReturn().getResponse().getContentAsString());

        assertQuestionShape(body);
        assertThat(body).isEqualTo(fixture("question.json"));
    }

    @Test
    void answerMatchesTheAnswerFixture() throws Exception {
        when(registry.answer(MATCH_ID, QUESTION_ID, "b")).thenReturn(new AnswerOutcome(
                QUESTION_ID,
                "b",
                true,
                "b",
                "Frodo inherited the Ring from his uncle Bilbo and took on the task of destroying it.",
                10,
                10,
                1,
                10,
                false));

        JsonNode body = read(mockMvc.perform(post(MATCHES_PATH + "/{id}/answer", MATCH_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":\"" + QUESTION_ID + "\",\"optionId\":\"b\"}"))
                .andReturn().getResponse().getContentAsString());

        assertThat(fieldNames(body)).containsExactly(
                "answered", "correct", "correctOptionId", "explanation", "finished", "matchId",
                "pointsAwarded", "questionId", "score", "submittedOptionId", "total");
        assertThat(body).isEqualTo(fixture("answer.json"));
    }

    @Test
    void resultMatchesTheResultFixture() throws Exception {
        when(registry.result(MATCH_ID)).thenReturn(new Result(
                "ALL", LoreChallengeDifficulty.MEDIUM, 10, 7, 3, 140, 200, 70, 95, "Scholar of Rivendell"));

        JsonNode body = read(mockMvc.perform(get(MATCHES_PATH + "/{id}/result", MATCH_ID))
                .andReturn().getResponse().getContentAsString());

        assertThat(fieldNames(body)).containsExactly(
                "category", "correctAnswers", "difficulty", "durationSeconds", "incorrectAnswers",
                "matchId", "maxScore", "percentage", "rank", "score", "totalQuestions");
        assertThat(body).isEqualTo(fixture("result.json"));
    }

    // Explicit, so a failure names the property that changed. The correct option and the
    // explanation are deliberately not here: they only travel with the answer.
    private static void assertQuestionShape(JsonNode body) {
        assertThat(fieldNames(body)).containsExactly(
                "category", "difficulty", "matchId", "number", "options", "prompt",
                "questionCategory", "questionId", "total");
        assertThat(body.get("options")).hasSize(4);

        for (JsonNode option : body.get("options")) {
            assertThat(fieldNames(option)).containsExactly("id", "text");
        }
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
