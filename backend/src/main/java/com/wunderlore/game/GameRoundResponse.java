package com.wunderlore.game;

import java.util.List;

public record GameRoundResponse(
        String roundId,
        String quote,
        List<GameRound.CharacterOption> options) {

    public static GameRoundResponse from(String roundId, GameRound round) {
        return new GameRoundResponse(
                roundId,
                round.quote(),
                round.options());
    }
}
