package com.wunderlore.game.whichcharacter.dto;

import java.util.List;

import com.wunderlore.game.whichcharacter.QuizQuestion;

public record QuizQuestionsResponse(
        List<QuizQuestionResponse> questions) {

    public static QuizQuestionsResponse from(List<QuizQuestion> questions) {
        return new QuizQuestionsResponse(
                questions.stream().map(QuizQuestionResponse::from).toList());
    }
}
