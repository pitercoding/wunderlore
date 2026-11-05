package com.wunderlore.game.whoami.dto;

import java.util.List;

import com.wunderlore.game.whoami.WhoAmIDifficulty;
import com.wunderlore.game.whoami.WhoAmIGame.Option;
import com.wunderlore.game.whoami.WhoAmIGame.RoundView;

// The round waiting for an answer: the clues revealed so far (never the later ones) and the four
// options. It carries neither the character nor the correct option.
public record RoundResponse(
        String gameId,
        WhoAmIDifficulty difficulty,
        int roundNumber,
        int totalRounds,
        int clueNumber,
        int totalClues,
        List<String> clues,
        List<Option> options,
        int score,
        int maxScore) {

    public static RoundResponse from(String gameId, RoundView view) {
        return new RoundResponse(
                gameId,
                view.difficulty(),
                view.roundNumber(),
                view.totalRounds(),
                view.clueNumber(),
                view.totalClues(),
                view.clues(),
                view.options(),
                view.score(),
                view.maxScore());
    }
}
