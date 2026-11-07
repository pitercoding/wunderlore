package com.wunderlore.game.whosaidit.dto;

import com.wunderlore.game.whosaidit.WhoSaidItMatch.AnswerOutcome;

// The outcome of one answer. It deliberately does not say whether the answer was right: that is
// revealed in the result of the finished match. Exactly one of next and result is set: next while
// the match goes on, result when that was the last question.
public record MatchAnswerResponse(
        String matchId,
        int questionNumber,
        String submittedCharacterId,
        boolean matchFinished,
        MatchQuestionResponse next,
        MatchResultResponse result) {

    public static MatchAnswerResponse from(String matchId, AnswerOutcome outcome) {
        return new MatchAnswerResponse(
                matchId,
                outcome.questionNumber(),
                outcome.submittedCharacterId(),
                outcome.matchFinished(),
                outcome.next() == null ? null : MatchQuestionResponse.from(matchId, outcome.next()),
                outcome.result() == null ? null : MatchResultResponse.from(matchId, outcome.result()));
    }
}
