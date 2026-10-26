package com.wunderlore.game;

public record AnswerResponse(
        String roundId,
        String submittedCharacterId,
        boolean correct,
        String correctCharacterId) {
}
