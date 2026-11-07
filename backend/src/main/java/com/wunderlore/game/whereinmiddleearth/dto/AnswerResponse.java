package com.wunderlore.game.whereinmiddleearth.dto;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthAccuracy;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.AnswerOutcome;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.Position;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.Reveal;

// The outcome of one answer. The round is always over, so reveal is always set: it is where the
// place really is, with the explanation. distance is in map units. result is set only when that
// was the last round; the unset one is written as null.
public record AnswerResponse(
        String gameId,
        int roundNumber,
        Position submitted,
        double distance,
        WhereInMiddleEarthAccuracy accuracy,
        int pointsAwarded,
        int score,
        Reveal reveal,
        boolean gameFinished,
        ResultResponse result) {

    public static AnswerResponse from(String gameId, AnswerOutcome outcome) {
        return new AnswerResponse(
                gameId,
                outcome.roundNumber(),
                outcome.submitted(),
                outcome.distance(),
                outcome.accuracy(),
                outcome.pointsAwarded(),
                outcome.score(),
                outcome.reveal(),
                outcome.gameFinished(),
                outcome.result() == null ? null : ResultResponse.from(gameId, outcome.result()));
    }
}
