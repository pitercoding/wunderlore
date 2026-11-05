package com.wunderlore.game.whoami.dto;

import java.util.List;

import com.wunderlore.game.whoami.WhoAmIDifficulty;
import com.wunderlore.game.whoami.WhoAmIGame.Result;
import com.wunderlore.game.whoami.WhoAmIGame.RoundSummary;

// incorrectAnswers counts wrong guesses over the whole game; unidentified counts the rounds in
// which all five clues were used without finding the character.
public record ResultResponse(
        String gameId,
        WhoAmIDifficulty difficulty,
        int totalRounds,
        int score,
        int maxScore,
        int percentage,
        int identified,
        int incorrectAnswers,
        int unidentified,
        List<RoundSummary> rounds) {

    public static ResultResponse from(String gameId, Result result) {
        return new ResultResponse(
                gameId,
                result.difficulty(),
                result.totalRounds(),
                result.score(),
                result.maxScore(),
                result.percentage(),
                result.identified(),
                result.incorrectAnswers(),
                result.unidentified(),
                result.rounds());
    }
}
