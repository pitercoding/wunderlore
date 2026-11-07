package com.wunderlore.game.whosaidit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wunderlore.game.whosaidit.WhoSaidItMatch.Question;
import com.wunderlore.lore.LoreDataUnavailableException;

// The registry is real here, so the HTTP behaviour of sequence, retries and expiry is the real one;
// only the service, which needs the lore data, is replaced.
@WebMvcTest(WhoSaidItMatchController.class)
@Import(WhoSaidItMatchControllerTest.Config.class)
class WhoSaidItMatchControllerTest {

    private static final String MATCHES = "/api/games/who-said-it/matches";

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        WhoSaidItMatchRegistry registry(Clock clock) {
            return new WhoSaidItMatchRegistry(clock, Duration.ofMinutes(30), 1000);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WhoSaidItMatchService service;

    @BeforeEach
    void stubService() {
        when(service.createMatch()).thenAnswer(invocation -> newMatch());
    }

    private static WhoSaidItMatch newMatch() {
        List<Question> questions = new ArrayList<>();

        for (int n = 1; n <= 10; n++) {
            questions.add(new Question(
                    "Quote " + n,
                    List.of(
                            new WhoSaidItRound.CharacterOption("w" + n + "a", "A " + n),
                            new WhoSaidItRound.CharacterOption("c" + n, "Correct " + n),
                            new WhoSaidItRound.CharacterOption("w" + n + "b", "B " + n),
                            new WhoSaidItRound.CharacterOption("w" + n + "c", "C " + n)),
                    "c" + n));
        }

        return new WhoSaidItMatch(questions, NOW);
    }

    private String createMatch() throws Exception {
        String body = mockMvc.perform(post(MATCHES))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return body.replaceAll(".*\"matchId\":\"([^\"]+)\".*", "$1");
    }

    private void answer(String matchId, int questionNumber, String characterId, int expectedStatus) throws Exception {
        mockMvc.perform(post(MATCHES + "/{id}/answer", matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionNumber\":" + questionNumber + ",\"characterId\":\"" + characterId + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void shouldStartAMatchWithTheFirstQuestionAndNoHintOfTheAnswer() throws Exception {
        String body = mockMvc.perform(post(MATCHES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionNumber").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(10))
                .andExpect(jsonPath("$.quote").value("Quote 1"))
                .andExpect(jsonPath("$.options", hasSize(4)))
                .andReturn().getResponse().getContentAsString();

        // Exactly these fields, and none of them says or hints the right option.
        assertThat(body).doesNotContain("correct\":", "correctCharacterId", "correctOption", "score");
        assertThat(body).contains("matchId", "options", "quote");
    }

    @Test
    void shouldOnlyReturnTheCurrentQuestion() throws Exception {
        String id = createMatch();

        answer(id, 1, "c1", 200);

        mockMvc.perform(get(MATCHES + "/{id}/question", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionNumber").value(2))
                .andExpect(jsonPath("$.quote").value("Quote 2"));
    }

    @Test
    void shouldPlayAMatchToTheEndAndReportTheScoreComputedByTheBackend() throws Exception {
        String id = createMatch();

        for (int n = 1; n <= 10; n++) {
            // Seven right answers, three wrong ones.
            String chosen = n <= 7 ? "c" + n : "w" + n + "a";
            var result = mockMvc.perform(post(MATCHES + "/{id}/answer", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionNumber\":" + n + ",\"characterId\":\"" + chosen + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matchFinished").value(n == 10))
                    .andReturn().getResponse().getContentAsString();

            // An answer never says whether it was right until the match is over.
            if (n < 10) {
                assertThat(result).doesNotContain("score", "correct\":", "result\":{");
            }
        }

        mockMvc.perform(get(MATCHES + "/{id}/result", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(70))
                .andExpect(jsonPath("$.maxScore").value(100))
                .andExpect(jsonPath("$.percentage").value(70))
                .andExpect(jsonPath("$.correctAnswers").value(7))
                .andExpect(jsonPath("$.questions", hasSize(10)));
    }

    @Test
    void shouldIgnoreAScoreSentByTheClient() throws Exception {
        String id = createMatch();

        mockMvc.perform(post(MATCHES + "/{id}/answer", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionNumber\":1,\"characterId\":\"w1a\",\"score\":100,\"correct\":true}"))
                .andExpect(status().isOk());

        for (int n = 2; n <= 10; n++) {
            answer(id, n, "w" + n + "a", 200);
        }

        mockMvc.perform(get(MATCHES + "/{id}/result", id))
                .andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void shouldRefuseAFutureQuestionAFalseRewriteAndAnInvalidOption() throws Exception {
        String id = createMatch();

        answer(id, 2, "c2", 409);
        answer(id, 1, "c2", 400);
        answer(id, 1, "w1a", 200);
        answer(id, 1, "c1", 409);
    }

    @Test
    void shouldAcceptARetryOfTheSameAnswerWithoutCountingItTwice() throws Exception {
        String id = createMatch();

        answer(id, 1, "c1", 200);
        answer(id, 1, "c1", 200);

        for (int n = 2; n <= 10; n++) {
            answer(id, n, "w" + n + "a", 200);
        }

        mockMvc.perform(get(MATCHES + "/{id}/result", id))
                .andExpect(jsonPath("$.score").value(10));
    }

    @Test
    void shouldRefuseTheResultOfAnUnfinishedMatch() throws Exception {
        String id = createMatch();

        mockMvc.perform(get(MATCHES + "/{id}/result", id))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownMatch() throws Exception {
        mockMvc.perform(get(MATCHES + "/{id}/question", "missing")).andExpect(status().isNotFound());
        mockMvc.perform(get(MATCHES + "/{id}/result", "missing")).andExpect(status().isNotFound());
        answer("missing", 1, "c1", 404);
    }

    @Test
    void shouldRejectMalformedAnswers() throws Exception {
        String id = createMatch();

        for (String body : List.of(
                "{}",
                "{\"questionNumber\":1}",
                "{\"characterId\":\"c1\"}",
                "{\"questionNumber\":0,\"characterId\":\"c1\"}",
                "{\"questionNumber\":11,\"characterId\":\"c1\"}",
                "{\"questionNumber\":1,\"characterId\":\"\"}",
                "not json")) {
            mockMvc.perform(post(MATCHES + "/{id}/answer", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldAnswer503WhenTheMatchCannotBeFormed() throws Exception {
        when(service.createMatch()).thenThrow(new WhoSaidItInsufficientDataException("not enough"));

        mockMvc.perform(post(MATCHES))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(WhoSaidItMatchController.MATCH_UNAVAILABLE_DETAIL));
    }

    @Test
    void shouldAnswer503WhenTheLoreIsUnavailable() throws Exception {
        when(service.createMatch()).thenThrow(new LoreDataUnavailableException("down"));

        mockMvc.perform(post(MATCHES))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("down"))));
    }
}
