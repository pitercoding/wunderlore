package com.wunderlore.game;

import java.util.List;

public record GameRoundResponse(
        String quote,
        List<GameRound.CharacterOption> options) {

    public static GameRoundResponse from(GameRound round) {
        return new GameRoundResponse(round.quote(), round.options());
    }
}
