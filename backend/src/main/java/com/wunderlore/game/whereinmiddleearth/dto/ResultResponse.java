package com.wunderlore.game.whereinmiddleearth.dto;

import java.util.List;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthDifficulty;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.Result;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.RoundSummary;

// closeHits counts the rounds that earned points (any band but FAR); averageDistance is the mean
// distance of the ten answers, in map units; percentage is the score over the maximum.
public record ResultResponse(
        String gameId,
        WhereInMiddleEarthDifficulty difficulty,
        int totalRounds,
        int score,
        int maxScore,
        int percentage,
        int closeHits,
        double averageDistance,
        List<RoundSummary> rounds) {

    public static ResultResponse from(String gameId, Result result) {
        return new ResultResponse(
                gameId,
                result.difficulty(),
                result.totalRounds(),
                result.score(),
                result.maxScore(),
                result.percentage(),
                result.closeHits(),
                result.averageDistance(),
                result.rounds());
    }
}
