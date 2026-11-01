package com.wunderlore.game.whichcharacter.dto;

import com.wunderlore.game.whichcharacter.CharacterResult;

public record QuizResultResponse(
        CharacterResultResponse character) {

    public static QuizResultResponse from(CharacterResult result) {
        return new QuizResultResponse(CharacterResultResponse.from(result));
    }
}
