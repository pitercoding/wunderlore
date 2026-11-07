package com.wunderlore.game.whereinmiddleearth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// Small builders so tests do not depend on the real content. The ten places lie on a line, 90
// map units apart, from (50, 200) to (860, 200).
final class WhereInMiddleEarthTestData {

    static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    static final String REGION = "test-region";

    private WhereInMiddleEarthTestData() {
    }

    static double targetX(int round) {
        return 50.0 + 90.0 * (round - 1);
    }

    static final double TARGET_Y = 200.0;

    static WhereInMiddleEarthLocation location(int index, WhereInMiddleEarthDifficulty difficulty) {
        return at(index, difficulty, targetX(index + 1), TARGET_Y);
    }

    static WhereInMiddleEarthLocation at(
            int index, WhereInMiddleEarthDifficulty difficulty, double x, double y) {
        return new WhereInMiddleEarthLocation(
                "place-" + index,
                "Place " + (char) ('A' + index),
                difficulty,
                REGION,
                x,
                y,
                "Third Age",
                "A made-up spot number " + index + ".",
                "It lies where the test put it.",
                List.of(new WhereInMiddleEarthLocation.Reference(
                        "Test", "https://example.org/place-" + index, "Nothing real.")));
    }

    static List<WhereInMiddleEarthLocation> locations(WhereInMiddleEarthDifficulty difficulty) {
        List<WhereInMiddleEarthLocation> all = new ArrayList<>();

        for (int i = 0; i < WhereInMiddleEarthGame.TOTAL_ROUNDS; i++) {
            all.add(location(i, difficulty));
        }

        return all;
    }

    static WhereInMiddleEarthGame game(WhereInMiddleEarthDifficulty difficulty, Instant createdAt) {
        return new WhereInMiddleEarthGame(
                difficulty, locations(difficulty), location -> "Test Region", createdAt);
    }

    static WhereInMiddleEarthGame game() {
        return game(WhereInMiddleEarthDifficulty.EASY, START);
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
