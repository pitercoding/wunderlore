package com.wunderlore.game.whereinmiddleearth;

import java.util.List;

// One curated place: where it is on the map, what it is, and the facts that support it. Immutable
// and shared by every game. The coordinates are in the map's viewBox and are the answer: they
// leave the backend only in the reveal of a finished round.
//
// - region: id of one of the bank's regions, a coarse area of the map (display names live there).
// - period: when the place existed in the stories, shown with the explanation, because the map
//   shows one geography but the places belong to different ages.
// - description: what the place is, shown while the player looks for it. It never says where.
// - explanation: where the place is, shown once the round is over.
public record WhereInMiddleEarthLocation(
        String id,
        String name,
        WhereInMiddleEarthDifficulty difficulty,
        String region,
        double x,
        double y,
        String period,
        String description,
        String explanation,
        List<Reference> references) {

    // supports: what the source is used to confirm. The verification record is REFERENCES.md.
    public record Reference(String source, String url, String supports) {
    }

    public WhereInMiddleEarthLocation {
        references = references == null ? List.of() : List.copyOf(references);
    }
}
