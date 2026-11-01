package com.wunderlore.game.whichcharacter;

import java.util.List;

public record QuizQuestion(
        String id,
        String text,
        List<QuizOption> options) {
    public QuizQuestion {
        options = List.copyOf(options);
    }
}