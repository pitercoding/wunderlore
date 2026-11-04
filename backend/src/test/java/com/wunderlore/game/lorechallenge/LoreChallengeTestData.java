package com.wunderlore.game.lorechallenge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.wunderlore.game.lorechallenge.LoreChallengeMatch.Option;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.PlayedQuestion;

// Small builders so tests do not depend on the real content.
final class LoreChallengeTestData {

    static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    private LoreChallengeTestData() {
    }

    static LoreChallengeQuestion question(
            String id, LoreChallengeCategory category, LoreChallengeDifficulty difficulty) {
        return new LoreChallengeQuestion(
                id,
                category,
                difficulty,
                "Prompt of " + id + "?",
                "Right " + id,
                List.of("Wrong one " + id, "Wrong two " + id, "Wrong three " + id),
                "Explanation of " + id + ".");
    }

    // The correct option of every question built here is always "a".
    static PlayedQuestion played(String id, LoreChallengeDifficulty difficulty) {
        LoreChallengeQuestion question = question(id, LoreChallengeCategory.CHARACTERS, difficulty);

        return new PlayedQuestion(
                question,
                List.of(
                        new Option("a", question.answer()),
                        new Option("b", question.distractors().get(0)),
                        new Option("c", question.distractors().get(1)),
                        new Option("d", question.distractors().get(2))),
                "a");
    }

    static LoreChallengeMatch match(LoreChallengeDifficulty difficulty, int size, Instant createdAt) {
        List<PlayedQuestion> questions = new ArrayList<>();

        for (int i = 1; i <= size; i++) {
            questions.add(played("q" + i, difficulty));
        }

        return new LoreChallengeMatch(
                LoreChallengeMatch.ALL_CATEGORIES, difficulty, questions, createdAt);
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
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
            return current.get();
        }
    }
}
