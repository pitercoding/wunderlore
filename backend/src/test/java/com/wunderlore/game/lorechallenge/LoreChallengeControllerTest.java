package com.wunderlore.game.lorechallenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

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

// The real service, registry and curated content behind the real controller: only the HTTP
// layer is a slice. Reaches the correct option the way a cheater cannot, by reading the bank.
@WebMvcTest(LoreChallengeController.class)
@Import({
        GameConfiguration.class,
        LoreChallengeService.class,
        LoreChallengeMatchRegistry.class,
        LoreChallengeQuestionBank.class
})
class LoreChallengeControllerTest {

    private static final String MATCHES_PATH = "/api/games/lore-challenge/matches";

    private static final String CREATE_ALL_EASY = "{\"category\":\"ALL\",\"difficulty\":\"EASY\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private LoreChallengeQuestionBank bank;

    private MockHttpServletResponse create(String body) throws Exception {
        return mockMvc.perform(post(MATCHES_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse answer(String matchId, String body) throws Exception {
        return mockMvc.perform(post(MATCHES_PATH + "/{id}/answer", matchId)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private static String answerBody(String questionId, String optionId) {
        return "{\"questionId\":\"" + questionId + "\",\"optionId\":\"" + optionId + "\"}";
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return jsonMapper.readTree(response.getContentAsString());
    }

    private JsonNode currentQuestion(String matchId) throws Exception {
        return json(mockMvc.perform(get(MATCHES_PATH + "/{id}/question", matchId))
                .andReturn().getResponse());
    }

    private String correctOptionId(JsonNode question) {
        String id = question.get("questionId").asString();

        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            for (LoreChallengeDifficulty difficulty : LoreChallengeDifficulty.values()) {
                for (LoreChallengeQuestion curated : bank.find(category, difficulty)) {
                    if (curated.id().equals(id)) {
                        for (JsonNode option : question.get("options")) {
                            if (option.get("text").asString().equals(curated.answer())) {
                                return option.get("id").asString();
                            }
                        }
                    }
                }
            }
        }

        throw new AssertionError("Unknown question " + id);
    }

    private String wrongOptionId(JsonNode question) {
        String correct = correctOptionId(question);

        return correct.equals("a") ? "b" : "a";
    }

    @Test
    void shouldStartAMatchWithTheFirstQuestionAndNoAnswer() throws Exception {
        MockHttpServletResponse response = create(CREATE_ALL_EASY);
        JsonNode body = json(response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.get("matchId").asString()).matches("[0-9a-f-]{36}");
        assertThat(body.get("category").asString()).isEqualTo("ALL");
        assertThat(body.get("difficulty").asString()).isEqualTo("EASY");
        assertThat(body.get("number").asInt()).isEqualTo(1);
        assertThat(body.get("total").asInt()).isEqualTo(10);
        assertThat(body.get("options")).hasSize(4);

        // Nothing that would give the answer away before it is submitted.
        String raw = response.getContentAsString();

        assertThat(raw).doesNotContain("correct").doesNotContain("explanation").doesNotContain("answer");
    }

    @Test
    void shouldPlayAFullMatchAndScoreItOnTheServer() throws Exception {
        JsonNode started = json(create("{\"category\":\"HISTORY\",\"difficulty\":\"HARD\"}"));
        String matchId = started.get("matchId").asString();
        JsonNode question = started;
        int expectedScore = 0;

        for (int number = 1; number <= 10; number++) {
            assertThat(question.get("number").asInt()).isEqualTo(number);
            assertThat(question.get("questionCategory").asString()).isEqualTo("HISTORY");

            boolean right = number <= 6;
            String option = right ? correctOptionId(question) : wrongOptionId(question);
            JsonNode outcome = json(answer(matchId, answerBody(question.get("questionId").asString(), option)));

            expectedScore += right ? 30 : 0;
            assertThat(outcome.get("correct").asBoolean()).isEqualTo(right);
            assertThat(outcome.get("correctOptionId").asString()).isEqualTo(correctOptionId(question));
            assertThat(outcome.get("pointsAwarded").asInt()).isEqualTo(right ? 30 : 0);
            assertThat(outcome.get("score").asInt()).isEqualTo(expectedScore);
            assertThat(outcome.get("explanation").asString()).isNotBlank();
            assertThat(outcome.get("finished").asBoolean()).isEqualTo(number == 10);

            if (number < 10) {
                question = currentQuestion(matchId);
            }
        }

        JsonNode result = json(mockMvc.perform(get(MATCHES_PATH + "/{id}/result", matchId))
                .andReturn().getResponse());

        assertThat(result.get("score").asInt()).isEqualTo(180);
        assertThat(result.get("maxScore").asInt()).isEqualTo(300);
        assertThat(result.get("correctAnswers").asInt()).isEqualTo(6);
        assertThat(result.get("incorrectAnswers").asInt()).isEqualTo(4);
        assertThat(result.get("percentage").asInt()).isEqualTo(60);
        assertThat(result.get("category").asString()).isEqualTo("HISTORY");
        assertThat(result.get("difficulty").asString()).isEqualTo("HARD");
        assertThat(result.get("rank").asString()).isEqualTo("Scholar of Rivendell");
        assertThat(result.get("durationSeconds").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldIgnoreAScoreSentByTheClient() throws Exception {
        JsonNode question = json(create(CREATE_ALL_EASY));
        String matchId = question.get("matchId").asString();

        JsonNode outcome = json(answer(matchId, "{\"questionId\":\"" + question.get("questionId").asString()
                + "\",\"optionId\":\"" + wrongOptionId(question)
                + "\",\"score\":9999,\"correct\":true,\"pointsAwarded\":500}"));

        assertThat(outcome.get("correct").asBoolean()).isFalse();
        assertThat(outcome.get("score").asInt()).isZero();
    }

    @Test
    void shouldRefuseAnAnswerSentTwice() throws Exception {
        JsonNode question = json(create(CREATE_ALL_EASY));
        String matchId = question.get("matchId").asString();
        String body = answerBody(question.get("questionId").asString(), correctOptionId(question));

        assertThat(answer(matchId, body).getStatus()).isEqualTo(200);

        MockHttpServletResponse second = answer(matchId, body);

        assertThat(second.getStatus()).isEqualTo(409);
        assertThat(second.getContentAsString()).contains("That is not the current question of this match");
        // The duplicate did not score: the next question is still the second one.
        assertThat(currentQuestion(matchId).get("number").asInt()).isEqualTo(2);
    }

    @Test
    void shouldRefuseAQuestionFromAnotherMatchOrThatDoesNotExist() throws Exception {
        JsonNode question = json(create(CREATE_ALL_EASY));
        String matchId = question.get("matchId").asString();

        MockHttpServletResponse unknown = answer(matchId, answerBody("no-such-question", "a"));

        assertThat(unknown.getStatus()).isEqualTo(400);
        assertThat(unknown.getContentAsString()).doesNotContain("no-such-question");
    }

    @Test
    void shouldRefuseAnInvalidOption() throws Exception {
        JsonNode question = json(create(CREATE_ALL_EASY));
        String matchId = question.get("matchId").asString();
        String questionId = question.get("questionId").asString();

        assertThat(answer(matchId, answerBody(questionId, "z")).getStatus()).isEqualTo(400);
        assertThat(answer(matchId, answerBody(questionId, "")).getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRefuseMalformedRequests() throws Exception {
        JsonNode question = json(create(CREATE_ALL_EASY));
        String matchId = question.get("matchId").asString();

        List<String> badCreates = List.of(
                "{}",
                "",
                "not json",
                "{\"category\":\"ALL\"}",
                "{\"difficulty\":\"EASY\"}",
                "{\"category\":\"SPELLS\",\"difficulty\":\"EASY\"}",
                "{\"category\":\"characters\",\"difficulty\":\"EASY\"}",
                "{\"category\":\"ALL\",\"difficulty\":\"IMPOSSIBLE\"}",
                "{\"category\":1,\"difficulty\":\"EASY\"}");

        for (String body : badCreates) {
            assertThat(create(body).getStatus()).as(body).isEqualTo(400);
        }

        for (String body : List.of("{}", "", "[]", "{\"questionId\":\"x\"}", "{\"optionId\":\"a\"}")) {
            assertThat(answer(matchId, body).getStatus()).as(body).isEqualTo(400);
        }

        assertThat(create("{}").getContentAsString()).contains("Invalid request");
    }

    @Test
    void shouldAnswerNotFoundForUnknownMatches() throws Exception {
        String unknown = "00000000-0000-4000-8000-000000000000";

        assertThat(answer(unknown, answerBody("characters-easy-01", "a")).getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(MATCHES_PATH + "/{id}/question", unknown))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(MATCHES_PATH + "/{id}/result", unknown))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void shouldNotGiveAResultBeforeTheEnd() throws Exception {
        String matchId = json(create(CREATE_ALL_EASY)).get("matchId").asString();
        MockHttpServletResponse response = mockMvc.perform(get(MATCHES_PATH + "/{id}/result", matchId))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("Match has not finished yet");
    }

    @Test
    void shouldRefuseAnswersAndQuestionsAfterTheMatchEnded() throws Exception {
        JsonNode question = json(create("{\"category\":\"PLACES\",\"difficulty\":\"EASY\"}"));
        String matchId = question.get("matchId").asString();
        String lastQuestionId = null;

        for (int number = 1; number <= 10; number++) {
            lastQuestionId = question.get("questionId").asString();
            answer(matchId, answerBody(lastQuestionId, correctOptionId(question)));

            if (number < 10) {
                question = currentQuestion(matchId);
            }
        }

        MockHttpServletResponse late = answer(matchId, answerBody(lastQuestionId, "a"));

        assertThat(late.getStatus()).isEqualTo(409);
        assertThat(late.getContentAsString()).contains("Match has already finished");
        assertThat(mockMvc.perform(get(MATCHES_PATH + "/{id}/question", matchId))
                .andReturn().getResponse().getStatus()).isEqualTo(409);

        JsonNode result = json(mockMvc.perform(get(MATCHES_PATH + "/{id}/result", matchId))
                .andReturn().getResponse());

        assertThat(result.get("score").asInt()).isEqualTo(100);
        assertThat(result.get("rank").asString()).isEqualTo("Loremaster of Middle-earth");
    }
}
