package com.wunderlore.game.whichcharacter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WhichCharacterController.class)
class WhichCharacterControllerTest {

    private static final String QUESTIONS_PATH = "/api/games/which-character/questions";
    private static final String RESULT_PATH = "/api/games/which-character/result";
    private static final String INVALID_DETAIL = "Invalid quiz answers";

    private static final CharacterResult FRODO = new CharacterResult(
            CharacterId.FRODO, "Frodo", "A humble bearer of a great burden.");

    // LoreUnavailableAdvice is loaded by @WebMvcTest and needs a Clock, even though it does
    // not apply to this controller.
    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WhichCharacterScoringService scoringService;

    // Helpers

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String answerJson(String questionId, String answerId) {
        return "{\"questionId\":" + quoted(questionId) + ",\"answerId\":" + quoted(answerId) + "}";
    }

    private static String bodyOf(List<String> answerJsons) {
        return "{\"answers\":[" + String.join(",", answerJsons) + "]}";
    }

    private static List<String> validAnswerJsons() {
        return new ArrayList<>(IntStream.rangeClosed(1, 10)
                .mapToObj(i -> String.format("q%02d", i))
                .map(q -> answerJson(q, q + "_a"))
                .toList());
    }

    private static List<String> validAnswersWith(int index, String replacement) {
        List<String> list = validAnswerJsons();
        list.set(index, replacement);
        return list;
    }

    private void postExpectingBadRequest(String body) throws Exception {
        mockMvc.perform(post(RESULT_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_DETAIL));
    }

    // Simulates the scoring service rejecting well-formed but invalid answers.
    private void postExpectingRejectedByScoring(String body) throws Exception {
        when(scoringService.calculateResult(any()))
                .thenThrow(new WhichCharacterInvalidAnswersException("secret internal message q99"));

        mockMvc.perform(post(RESULT_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(INVALID_DETAIL))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("q99"))));

        verify(scoringService).calculateResult(any());
    }

    // GET /questions

    @Test
    void shouldReturnTenQuestionsEachWithFourOptions() throws Exception {
        mockMvc.perform(get(QUESTIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(10))
                .andExpect(jsonPath("$.questions[*].options.length()").value(everyItem(is(4))));
    }

    @Test
    void shouldPreserveCatalogOrder() throws Exception {
        List<QuizQuestion> catalog = WhichCharacterQuiz.QUESTIONS;

        for (int i = 0; i < catalog.size(); i++) {
            String question = "$.questions[" + i + "]";
            QuizQuestion expected = catalog.get(i);

            mockMvc.perform(get(QUESTIONS_PATH))
                    .andExpect(jsonPath(question + ".id").value(expected.id()))
                    .andExpect(jsonPath(question + ".text").value(expected.text()));

            for (int j = 0; j < expected.options().size(); j++) {
                mockMvc.perform(get(QUESTIONS_PATH))
                        .andExpect(jsonPath(question + ".options[" + j + "].id")
                                .value(expected.options().get(j).id()))
                        .andExpect(jsonPath(question + ".options[" + j + "].text")
                                .value(expected.options().get(j).text()));
            }
        }
    }

    @Test
    void shouldNotExposeScoringInformationInQuestions() throws Exception {
        mockMvc.perform(get(QUESTIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.questions[0].length()").value(3))
                .andExpect(jsonPath("$.questions[0].options[0].length()").value(2))
                .andExpect(jsonPath("$.questions[0].options[0].primaryCharacter").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[0].secondaryCharacter").doesNotExist())
                .andExpect(content().string(not(containsString("primaryCharacter"))))
                .andExpect(content().string(not(containsString("secondaryCharacter"))))
                .andExpect(content().string(not(containsString("score"))));
    }

    // POST /result

    @Test
    void shouldReturnCharacterAndDelegateScoring() throws Exception {
        when(scoringService.calculateResult(any())).thenReturn(FRODO);

        mockMvc.perform(post(RESULT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf(validAnswerJsons())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.id").value("FRODO"))
                .andExpect(jsonPath("$.character.name").value("Frodo"))
                .andExpect(jsonPath("$.character.description").value(FRODO.description()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuizAnswer>> captor = ArgumentCaptor.forClass(List.class);
        verify(scoringService).calculateResult(captor.capture());
        assertThat(captor.getValue()).hasSize(10);
        assertThat(captor.getValue().get(0)).isEqualTo(new QuizAnswer("q01", "q01_a"));
        assertThat(captor.getValue().get(9)).isEqualTo(new QuizAnswer("q10", "q10_a"));
    }

    @Test
    void shouldReturnOnlyTheCharacterWithThreePublicFields() throws Exception {
        when(scoringService.calculateResult(any())).thenReturn(FRODO);

        mockMvc.perform(post(RESULT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf(validAnswerJsons())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.character.length()").value(3));
    }

    @Test
    void shouldIgnoreClientSuppliedScoresAndResults() throws Exception {
        when(scoringService.calculateResult(any())).thenReturn(FRODO);
        String body = "{\"character\":\"SAURON\",\"scores\":{\"SAURON\":99},\"answers\":["
                + String.join(",", validAnswerJsons()) + "]}";

        mockMvc.perform(post(RESULT_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.id").value("FRODO"));
    }

    @Test
    void shouldRejectTooFewAnswers() throws Exception {
        postExpectingRejectedByScoring(bodyOf(validAnswerJsons().subList(0, 9)));
    }

    @Test
    void shouldRejectMoreThanTenAnswersWithoutScoring() throws Exception {
        List<String> eleven = validAnswerJsons();
        eleven.add(answerJson("q01", "q01_a"));

        postExpectingBadRequest(bodyOf(eleven));
        verifyNoInteractions(scoringService);
    }

    @Test
    void shouldRejectDuplicateQuestionIds() throws Exception {
        postExpectingRejectedByScoring(bodyOf(validAnswersWith(9, answerJson("q01", "q01_a"))));
    }

    @Test
    void shouldRejectUnknownQuestionIds() throws Exception {
        postExpectingRejectedByScoring(bodyOf(validAnswersWith(9, answerJson("q99", "q99_a"))));
    }

    @Test
    void shouldRejectAnswersBelongingToAnotherQuestion() throws Exception {
        postExpectingRejectedByScoring(bodyOf(validAnswersWith(0, answerJson("q01", "q02_a"))));
    }

    @Test
    void shouldRejectBlankAndMissingAnswerFieldsWithoutScoring() throws Exception {
        postExpectingBadRequest(bodyOf(validAnswersWith(0, answerJson("", "q01_a"))));
        postExpectingBadRequest(bodyOf(validAnswersWith(0, answerJson("q01", "  "))));
        postExpectingBadRequest(bodyOf(validAnswersWith(0, "{\"answerId\":\"q01_a\"}")));
        postExpectingBadRequest(bodyOf(validAnswersWith(0, "{\"questionId\":\"q01\"}")));
        postExpectingBadRequest(bodyOf(validAnswersWith(0, "{}")));

        verifyNoInteractions(scoringService);
    }

    @Test
    void shouldRejectNullAnswers() throws Exception {
        postExpectingBadRequest("{\"answers\":null}");
        postExpectingBadRequest("{}");
        postExpectingBadRequest(bodyOf(validAnswersWith(0, "null")));
        postExpectingBadRequest(bodyOf(validAnswersWith(0, answerJson("q01", null))));
        postExpectingBadRequest(bodyOf(validAnswersWith(0, answerJson(null, "q01_a"))));

        verifyNoInteractions(scoringService);
    }

    @Test
    void shouldReturnBadRequestForEmptyOrMalformedBodies() throws Exception {
        postExpectingBadRequest("");
        postExpectingBadRequest("{");
        postExpectingBadRequest("not json");
        postExpectingBadRequest("{\"answers\":\"q01_a\"}");
        postExpectingBadRequest("{\"answers\":[\"q01_a\"]}");
        postExpectingBadRequest("[]");

        mockMvc.perform(post(RESULT_PATH))
                .andExpect(status().isBadRequest());

        verify(scoringService, never()).calculateResult(any());
    }

    @Test
    void shouldNotTurnUnexpectedExceptionsIntoBadRequest() {
        when(scoringService.calculateResult(any()))
                .thenThrow(new IllegalArgumentException("programming error"));

        assertThatThrownBy(() -> mockMvc.perform(post(RESULT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf(validAnswerJsons()))))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("programming error");

        doThrow(new IllegalStateException("boom")).when(scoringService).calculateResult(any());

        assertThatThrownBy(() -> mockMvc.perform(post(RESULT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyOf(validAnswerJsons()))))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
