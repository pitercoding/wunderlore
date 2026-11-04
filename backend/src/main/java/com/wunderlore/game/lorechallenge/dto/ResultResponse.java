package com.wunderlore.game.lorechallenge.dto;

import com.wunderlore.game.lorechallenge.LoreChallengeDifficulty;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.Result;

// durationSeconds: from creating the match to its last answer, so it includes reading time.
public record ResultResponse(
        String matchId,
        String category,
        LoreChallengeDifficulty difficulty,
        int totalQuestions,
        int correctAnswers,
        int incorrectAnswers,
        int score,
        int maxScore,
        int percentage,
        long durationSeconds,
        String rank) {

    public static ResultResponse from(String matchId, Result result) {
        return new ResultResponse(
                matchId,
                result.category(),
                result.difficulty(),
                result.totalQuestions(),
                result.correctAnswers(),
                result.incorrectAnswers(),
                result.score(),
                result.maxScore(),
                result.percentage(),
                result.durationSeconds(),
                result.rank());
    }
}
