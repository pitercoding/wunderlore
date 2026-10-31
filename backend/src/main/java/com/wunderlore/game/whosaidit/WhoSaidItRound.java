package com.wunderlore.game.whosaidit;

import java.util.List;

public record WhoSaidItRound(
        String quote,
        List<CharacterOption> options,
        String correctCharacterId) {

    public record CharacterOption(
            String id,
            String name) {
    }
}
