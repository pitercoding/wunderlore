package com.wunderlore.game.whosaidit.dto;

import java.util.List;

import com.wunderlore.game.whosaidit.WhoSaidItMatch.QuestionSummary;
import com.wunderlore.game.whosaidit.WhoSaidItMatch.Result;

// Only available once the match is finished; it is the one place the correct characters appear.
public record MatchResultResponse(
        String matchId,
        int totalQuestions,
        int correctAnswers,
        int incorrectAnswers,
        int score,
        int maxScore,
        int percentage,
        List<QuestionSummary> questions) {

    public static MatchResultResponse from(String matchId, Result result) {
        return new MatchResultResponse(
                matchId,
                result.totalQuestions(),
                result.correctAnswers(),
                result.incorrectAnswers(),
                result.score(),
                result.maxScore(),
                result.percentage(),
                result.questions());
    }
}
