package com.wunderlore.ranking;

import java.time.Instant;
import java.util.Objects;

// What the server kept about a finished game: the only source of a published score.
// playedAt is when the game was created. division is the division the result competes in, decided
// by the backend from the game itself (never from the client); see RankingDivision.
public record FinishedGame(int score, int maxScore, Instant playedAt, RankingDivision division) {

    public FinishedGame {
        Objects.requireNonNull(division, "division");
    }
}
