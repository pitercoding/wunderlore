package com.wunderlore.game.whosaidit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

// Time only moves when a test moves it, so nothing here sleeps or depends on machine speed.
class RoundCreationRateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));

    private RoundCreationRateLimiter limiter(int limit) {
        return new RoundCreationRateLimiter(clock, limit, WINDOW);
    }

    @Test
    void shouldAcceptTheFirst120AttemptsAndRejectTheNext() {
        RoundCreationRateLimiter limiter = limiter(120);

        for (int i = 0; i < 120; i++) {
            assertThat(limiter.tryAcquire().allowed()).as("attempt %d", i + 1).isTrue();
        }

        assertThat(limiter.tryAcquire().allowed()).isFalse();
    }

    @Test
    void shouldReportAWholeRetryAfterOfAtLeastOneSecond() {
        RoundCreationRateLimiter limiter = limiter(1);

        limiter.tryAcquire();

        assertThat(limiter.tryAcquire().retryAfterSeconds()).isEqualTo(60);

        clock.advance(Duration.ofMillis(10_500));

        // 49.5 s left, rounded up.
        assertThat(limiter.tryAcquire().retryAfterSeconds()).isEqualTo(50);

        clock.advance(Duration.ofMillis(49_499));

        // 1 ms left still reads as one second, never zero.
        RoundCreationRateLimiter.Decision decision = limiter.tryAcquire();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void shouldAcceptAgainAtExactlyOneWindowAfterTheOldestAccept() {
        RoundCreationRateLimiter limiter = limiter(2);

        limiter.tryAcquire();
        clock.advance(Duration.ofSeconds(10));
        limiter.tryAcquire();

        clock.advance(Duration.ofMillis(49_999));
        assertThat(limiter.tryAcquire().allowed()).isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.tryAcquire().allowed()).isTrue();
        // The second accept is still inside its window, and the third took the first slot.
        assertThat(limiter.tryAcquire().allowed()).isFalse();
    }

    @Test
    void shouldAcceptAgainAfterTheWindow() {
        RoundCreationRateLimiter limiter = limiter(3);

        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire();
        }

        assertThat(limiter.tryAcquire().allowed()).isFalse();

        clock.advance(WINDOW);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire().allowed()).isTrue();
        }

        assertThat(limiter.tryAcquire().allowed()).isFalse();
    }

    @Test
    void shouldFreeSlotsAsTheWindowSlides() {
        RoundCreationRateLimiter limiter = limiter(2);

        limiter.tryAcquire();
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire();

        clock.advance(Duration.ofSeconds(30));

        // Only the first accept is a full window old: one slot, not two.
        assertThat(limiter.tryAcquire().allowed()).isTrue();
        assertThat(limiter.tryAcquire().allowed()).isFalse();
    }

    @Test
    void shouldNotUseUpQuotaWhenRejecting() {
        RoundCreationRateLimiter limiter = limiter(1);

        limiter.tryAcquire();

        for (int i = 0; i < 1_000; i++) {
            assertThat(limiter.tryAcquire().allowed()).isFalse();
            clock.advance(Duration.ofMillis(10));
        }

        // 10 s of rejections did not push the point where the slot frees up.
        clock.advance(Duration.ofSeconds(50));

        assertThat(limiter.tryAcquire().allowed()).isTrue();
    }

    @Test
    void shouldNotLetABackwardsClockGiveBackQuota() {
        RoundCreationRateLimiter limiter = limiter(1);

        limiter.tryAcquire();
        clock.advance(Duration.ofSeconds(-30));

        assertThat(limiter.tryAcquire().allowed()).isFalse();
    }

    @Test
    void shouldNeverAcceptMoreThanTheLimitUnderConcurrency() throws Exception {
        int limit = 120;
        int threads = 32;
        int attemptsPerThread = 50;
        RoundCreationRateLimiter limiter = limiter(limit);
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        try {
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();

                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.tryAcquire().allowed()) {
                            accepted.incrementAndGet();
                        }
                    }

                    return null;
                }));
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        // The clock never moves, so exactly the limit gets through out of 1,600 attempts.
        assertThat(accepted.get()).isEqualTo(limit);
    }

    @Test
    void shouldKeepAFixedAmountOfStateWhateverTheTraffic() throws Exception {
        RoundCreationRateLimiter limiter = limiter(50);
        Field ring = RoundCreationRateLimiter.class.getDeclaredField("acceptedAt");

        ring.setAccessible(true);

        long[] before = (long[]) ring.get(limiter);

        for (int i = 0; i < 100_000; i++) {
            limiter.tryAcquire();
            clock.advance(Duration.ofMillis(100));
        }

        long[] after = (long[]) ring.get(limiter);

        assertThat(after).isSameAs(before).hasSize(50);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThatThrownBy(() -> new RoundCreationRateLimiter(clock, 0, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundCreationRateLimiter(clock, -1, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundCreationRateLimiter(
                clock, RoundCreationRateLimiter.MAX_LIMIT + 1, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundCreationRateLimiter(clock, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoundCreationRateLimiter(clock, 1, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> currentInstant;

        private MutableClock(Instant initialInstant) {
            this.currentInstant = new AtomicReference<>(initialInstant);
        }

        void advance(Duration duration) {
            currentInstant.updateAndGet(instant -> instant.plus(duration));
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
            return currentInstant.get();
        }
    }
}
