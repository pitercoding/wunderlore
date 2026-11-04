package com.wunderlore.game.lorechallenge.dto;

import com.wunderlore.game.lorechallenge.LoreChallengeMatch.AnswerOutcome;

public record AnswerResponse(
        String matchId,
        String questionId,
        String submittedOptionId,
        boolean correct,
        String correctOptionId,
        String explanation,
        int pointsAwarded,
        int score,
        int answered,
        int total,
        boolean finished) {

    public static AnswerResponse from(String matchId, AnswerOutcome outcome) {
        return new AnswerResponse(
                matchId,
                outcome.questionId(),
                outcome.submittedOptionId(),
                outcome.correct(),
                outcome.correctOptionId(),
                outcome.explanation(),
                outcome.pointsAwarded(),
                outcome.score(),
                outcome.answered(),
                outcome.total(),
                outcome.finished());
    }
}
