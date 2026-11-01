package com.wunderlore.game.whichcharacter.dto;

import java.util.List;

import com.wunderlore.game.whichcharacter.QuizAnswer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Only the shape is checked here; the scoring service decides whether the answers are valid.
// The size cap just bounds what is processed: the quiz has exactly 10 questions.
public record QuizResultRequest(
                @NotNull @Size(max = 10) List<@NotNull @Valid AnswerRequest> answers) {

        public record AnswerRequest(
                        @NotBlank String questionId,
                        @NotBlank String answerId) {
        }

        public List<QuizAnswer> toQuizAnswers() {
                return answers.stream()
                                .map(answer -> new QuizAnswer(answer.questionId(), answer.answerId()))
                                .toList();
        }
}
