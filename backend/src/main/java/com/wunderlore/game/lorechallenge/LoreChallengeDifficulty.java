package com.wunderlore.game.lorechallenge;

// Points are awarded per correct answer, by the backend only: 10, 20 or 30.
public enum LoreChallengeDifficulty {
    EASY(10),
    MEDIUM(20),
    HARD(30);

    private final int pointsPerCorrectAnswer;

    LoreChallengeDifficulty(int pointsPerCorrectAnswer) {
        this.pointsPerCorrectAnswer = pointsPerCorrectAnswer;
    }

    public int pointsPerCorrectAnswer() {
        return pointsPerCorrectAnswer;
    }
}
