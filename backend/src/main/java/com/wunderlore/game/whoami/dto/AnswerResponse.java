package com.wunderlore.game.whoami.dto;

import com.wunderlore.game.whoami.WhoAmIGame.AnswerOutcome;
import com.wunderlore.game.whoami.WhoAmIGame.NextClue;
import com.wunderlore.game.whoami.WhoAmIGame.Reveal;

// The outcome of one answer. Exactly one of next and reveal is set: next while the round goes on
// (a wrong answer with clues left), reveal when it ended (identified, or the fifth clue failed).
// result is set only when that was the last round. The unset ones are written as null.
public record AnswerResponse(
        String gameId,
        int roundNumber,
        int clueNumber,
        String submittedOptionId,
        boolean correct,
        boolean roundFinished,
        int pointsAwarded,
        int score,
        boolean gameFinished,
        NextClue next,
        Reveal reveal,
        ResultResponse result) {

    public static AnswerResponse from(String gameId, AnswerOutcome outcome) {
        return new AnswerResponse(
                gameId,
                outcome.roundNumber(),
                outcome.clueNumber(),
                outcome.submittedOptionId(),
                outcome.correct(),
                outcome.roundFinished(),
                outcome.pointsAwarded(),
                outcome.score(),
                outcome.gameFinished(),
                outcome.next(),
                outcome.reveal(),
                outcome.result() == null ? null : ResultResponse.from(gameId, outcome.result()));
    }
}
