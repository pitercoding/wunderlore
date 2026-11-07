package com.wunderlore.game.whosaidit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wunderlore.game.whosaidit.WhoSaidItMatch.Question;
import com.wunderlore.game.whosaidit.WhoSaidItMatchException.Reason;

class WhoSaidItMatchRegistryTest {

    private MutableClock clock;
    private WhoSaidItMatchRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        registry = new WhoSaidItMatchRegistry(clock, Duration.ofMinutes(30), 2);
    }

    private WhoSaidItMatch newMatch() {
        List<Question> questions = new ArrayList<>();

        for (int n = 1; n <= WhoSaidItMatch.TOTAL_QUESTIONS; n++) {
            questions.add(new Question(
                    "Quote " + n,
                    List.of(
                            new WhoSaidItRound.CharacterOption("w" + n + "a", "A"),
                            new WhoSaidItRound.CharacterOption("c" + n, "Correct"),
                            new WhoSaidItRound.CharacterOption("w" + n + "b", "B"),
                            new WhoSaidItRound.CharacterOption("w" + n + "c", "C")),
                    "c" + n));
        }

        return new WhoSaidItMatch(questions, clock.instant());
    }

    private static Reason reasonOf(Throwable throwable) {
        return ((WhoSaidItMatchException) throwable).reason();
    }

    @Test
    void shouldFailForAnUnknownMatch() {
        assertThatThrownBy(() -> registry.currentQuestion("nope"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.MATCH_NOT_FOUND));
        assertThatThrownBy(() -> registry.answer("nope", 1, "c1"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.MATCH_NOT_FOUND));
        assertThatThrownBy(() -> registry.result("nope"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.MATCH_NOT_FOUND));
    }

    @Test
    void shouldExpireAMatchAfterThirtyMinutes() {
        String id = registry.register(newMatch());

        clock.advance(Duration.ofMinutes(29));
        assertThat(registry.currentQuestion(id).questionNumber()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> registry.currentQuestion(id))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.MATCH_NOT_FOUND));
        assertThatThrownBy(() -> registry.answer(id, 1, "c1"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.MATCH_NOT_FOUND));
    }

    @Test
    void shouldKeepAFinishedMatchReadableUntilItExpires() {
        String id = registry.register(newMatch());

        for (int n = 1; n <= 10; n++) {
            registry.answer(id, n, "c" + n);
        }

        clock.advance(Duration.ofMinutes(10));

        assertThat(registry.result(id).score()).isEqualTo(100);
        assertThatThrownBy(() -> registry.currentQuestion(id))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.MATCH_FINISHED));
    }

    @Test
    void shouldRefuseTheResultOfAnUnfinishedMatch() {
        String id = registry.register(newMatch());

        assertThatThrownBy(() -> registry.result(id))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.MATCH_NOT_FINISHED));
    }

    @Test
    void shouldFreeASlotWhenAMatchFinishesAndWhenItExpires() {
        String first = registry.register(newMatch());

        registry.register(newMatch());
        assertThatThrownBy(() -> registry.register(newMatch()))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.CAPACITY_EXCEEDED));

        for (int n = 1; n <= 10; n++) {
            registry.answer(first, n, "c" + n);
        }

        registry.register(newMatch());
        assertThatThrownBy(() -> registry.register(newMatch()))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.CAPACITY_EXCEEDED));

        clock.advance(Duration.ofMinutes(31));
        registry.register(newMatch());
    }

    @Test
    void shouldCountAnAnswerOnlyOnceWhenTheSameAnswerArrivesConcurrently() throws Exception {
        registry = new WhoSaidItMatchRegistry(clock, Duration.ofMinutes(30), 10);

        String id = registry.register(newMatch());
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    registry.answer(id, 1, "c1");
                    accepted.incrementAndGet();

                    return null;
                }));
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Every retry is accepted, none counted twice: finish the rest and check the score.
        assertThat(accepted.get()).isEqualTo(threads);

        for (int n = 2; n <= 10; n++) {
            registry.answer(id, n, "w" + n + "a");
        }

        assertThat(registry.result(id).score()).isEqualTo(10);
    }

    @Test
    void shouldAcceptOnlyOneOfTwoConflictingConcurrentAnswers() throws Exception {
        registry = new WhoSaidItMatchRegistry(clock, Duration.ofMinutes(30), 10);

        String id = registry.register(newMatch());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        try {
            List<Future<?>> futures = new ArrayList<>();

            for (String characterId : List.of("c1", "w1a")) {
                futures.add(pool.submit(() -> {
                    start.await();

                    try {
                        registry.answer(id, 1, characterId);
                        accepted.incrementAndGet();
                    } catch (WhoSaidItMatchException e) {
                        refused.incrementAndGet();
                    }

                    return null;
                }));
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(accepted.get()).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
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
            return now;
        }
    }
}
