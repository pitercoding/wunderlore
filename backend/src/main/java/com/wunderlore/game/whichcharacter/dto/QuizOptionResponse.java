package com.wunderlore.game.whichcharacter.dto;

import com.wunderlore.game.whichcharacter.QuizOption;

public record QuizOptionResponse(
        String id,
        String text) {

    public static QuizOptionResponse from(QuizOption option) {
        return new QuizOptionResponse(option.id(), option.text());
    }
}
