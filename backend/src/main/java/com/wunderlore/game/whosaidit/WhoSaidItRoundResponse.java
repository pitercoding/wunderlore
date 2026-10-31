package com.wunderlore.game.whosaidit;

import java.util.List;

public record WhoSaidItRoundResponse(
        String roundId,
        String quote,
        List<WhoSaidItRound.CharacterOption> options) {

    public static WhoSaidItRoundResponse from(String roundId, WhoSaidItRound round) {
        return new WhoSaidItRoundResponse(
                roundId,
                round.quote(),
                round.options());
    }
}
