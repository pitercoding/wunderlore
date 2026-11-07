package com.wunderlore.game.whereinmiddleearth.dto;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthDifficulty;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.RoundView;

// The round waiting for an answer: which place to find and what it is. It carries neither the
// coordinates of the place nor its region or explanation.
public record RoundResponse(
        String gameId,
        WhereInMiddleEarthDifficulty difficulty,
        int roundNumber,
        int totalRounds,
        String locationName,
        String description,
        int score,
        int maxScore) {

    public static RoundResponse from(String gameId, RoundView view) {
        return new RoundResponse(
                gameId,
                view.difficulty(),
                view.roundNumber(),
                view.totalRounds(),
                view.locationName(),
                view.description(),
                view.score(),
                view.maxScore());
    }
}
