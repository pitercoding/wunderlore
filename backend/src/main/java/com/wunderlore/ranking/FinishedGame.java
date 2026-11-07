package com.wunderlore.ranking;

import java.time.Instant;

// What the server kept about a finished game: the only source of a published score.
// playedAt is when the game was created.
public record FinishedGame(int score, int maxScore, Instant playedAt) {
}
