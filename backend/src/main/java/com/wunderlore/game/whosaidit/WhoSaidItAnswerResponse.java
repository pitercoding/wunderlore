package com.wunderlore.game.whosaidit;

public record WhoSaidItAnswerResponse(
        String roundId,
        String submittedCharacterId,
        boolean correct,
        String correctCharacterId) {
}
