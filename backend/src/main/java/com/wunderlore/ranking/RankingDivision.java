package com.wunderlore.ranking;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

// The division a published result competes in. Results are only ever compared inside one
// (game, division) pair. STANDARD is not a fourth difficulty: it is the single division of
// WHO_SAID_IT, which has no difficulty levels. The slug is the public name (URLs and JSON); the
// constant name is what the database stores.
//
// This enum is the one place that knows which divisions each game has (see of and isValidFor).
public enum RankingDivision {

    EASY,
    MEDIUM,
    HARD,
    STANDARD;

    private static final List<RankingDivision> DIFFICULTY_LEVELS = List.of(EASY, MEDIUM, HARD);

    private static final List<RankingDivision> SINGLE_MODE = List.of(STANDARD);

    // The divisions of a game, in display order.
    public static List<RankingDivision> of(GameType game) {
        return switch (game) {
            case LORE_CHALLENGE, WHO_AM_I, WHERE_IN_MIDDLE_EARTH -> DIFFICULTY_LEVELS;
            case WHO_SAID_IT -> SINGLE_MODE;
        };
    }

    public boolean isValidFor(GameType game) {
        return of(game).contains(this);
    }

    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<RankingDivision> fromSlug(String slug) {
        for (RankingDivision division : values()) {
            if (division.slug().equals(slug)) {
                return Optional.of(division);
            }
        }

        return Optional.empty();
    }
}
