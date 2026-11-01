package com.wunderlore.game.whichcharacter.dto;

import java.util.List;

import com.wunderlore.game.whichcharacter.QuizQuestion;

public record QuizQuestionResponse(
        String id,
        String text,
        List<QuizOptionResponse> options) {

    public static QuizQuestionResponse from(QuizQuestion question) {
        return new QuizQuestionResponse(
                question.id(),
                question.text(),
                question.options().stream().map(QuizOptionResponse::from).toList());
    }
}
