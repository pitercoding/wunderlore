package com.wunderlore.game.whosaidit.dto;

import java.util.List;

import com.wunderlore.game.whosaidit.WhoSaidItMatch.QuestionView;
import com.wunderlore.game.whosaidit.WhoSaidItRound.CharacterOption;

// The question waiting for an answer: its number, the quote and four options. It carries no
// field that says or hints which option is correct.
public record MatchQuestionResponse(
        String matchId,
        int questionNumber,
        int totalQuestions,
        String quote,
        List<CharacterOption> options) {

    public static MatchQuestionResponse from(String matchId, QuestionView view) {
        return new MatchQuestionResponse(
                matchId,
                view.questionNumber(),
                view.totalQuestions(),
                view.quote(),
                view.options());
    }
}
