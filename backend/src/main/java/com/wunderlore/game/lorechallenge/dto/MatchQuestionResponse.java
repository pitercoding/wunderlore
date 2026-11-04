package com.wunderlore.game.lorechallenge.dto;

import java.util.List;

import com.wunderlore.game.lorechallenge.LoreChallengeCategory;
import com.wunderlore.game.lorechallenge.LoreChallengeDifficulty;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.Option;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.QuestionView;

// A question waiting for an answer. It never carries the correct option or the explanation.
public record MatchQuestionResponse(
        String matchId,
        String category,
        LoreChallengeDifficulty difficulty,
        int number,
        int total,
        String questionId,
        LoreChallengeCategory questionCategory,
        String prompt,
        List<Option> options) {

    public static MatchQuestionResponse from(String matchId, QuestionView view) {
        return new MatchQuestionResponse(
                matchId,
                view.category(),
                view.difficulty(),
                view.number(),
                view.total(),
                view.questionId(),
                view.questionCategory(),
                view.prompt(),
                view.options());
    }
}
