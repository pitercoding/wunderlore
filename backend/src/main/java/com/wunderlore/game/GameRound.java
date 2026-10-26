package com.wunderlore.game;

import java.util.List;

public record GameRound(
        String quote,
        List<CharacterOption> options,
        String correctCharacterId) {

    public record CharacterOption(
            String id,
            String name) {
    }
}
