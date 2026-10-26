package com.wunderlore.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameRoundRegistryTest {

    private MutableClock clock;
    private GameRoundRegistry registry;

    private final GameRound round = new GameRound(
            "A quote",
            List.of(
                    new GameRound.CharacterOption("1", "Frodo"),
                    new GameRound.CharacterOption("2", "Gandalf"),
                    new GameRound.CharacterOption("3", "Aragorn"),
                    new GameRound.CharacterOption("4", "Legolas")),
            "2");

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        registry = new GameRoundRegistry(clock, Duration.ofMinutes(10), 2);
    }

    @Test
    void registerShouldReturnUniqueRoundIds() {
        String firstId = registry.register(round);
        String secondId = registry.register(round);

        assertFalse(firstId.isBlank());
        assertFalse(firstId.equals(secondId));
    }

    @Test
    void answerShouldReturnCorrectResultWhenAnswerIsCorrect() {
        String roundId = registry.register(round);

        AnswerResponse response = registry.answer(
                new AnswerRequest(roundId, "2"));

        assertEquals(roundId, response.roundId());
        assertEquals("2", response.submittedCharacterId());
        assertTrue(response.correct());
        assertEquals("2", response.correctCharacterId());
    }

    @Test
    void answerShouldReturnFalseWhenAnswerIsWrong() {
        String roundId = registry.register(round);

        AnswerResponse response = registry.answer(
                new AnswerRequest(roundId, "1"));

        assertFalse(response.correct());
        assertEquals("2", response.correctCharacterId());
    }

    @Test
    void answerShouldRejectUnknownRound() {
        assertThrows(
                RoundNotFoundException.class,
                () -> registry.answer(new AnswerRequest("unknown", "2")));
    }

    @Test
    void invalidCharacterShouldNotConsumeAttempt() {
        String roundId = registry.register(round);

        assertThrows(
                InvalidCharacterOptionException.class,
                () -> registry.answer(new AnswerRequest(roundId, "999")));

        AnswerResponse response = registry.answer(
                new AnswerRequest(roundId, "2"));

        assertTrue(response.correct());
    }

    @Test
    void answerShouldRejectSecondValidAttempt() {
        String roundId = registry.register(round);

        registry.answer(new AnswerRequest(roundId, "1"));

        assertThrows(
                RoundAlreadyAnsweredException.class,
                () -> registry.answer(new AnswerRequest(roundId, "2")));
    }

    @Test
    void answerShouldRejectExpiredRound() {
        String roundId = registry.register(round);

        clock.advance(Duration.ofMinutes(10));

        assertThrows(
                RoundNotFoundException.class,
                () -> registry.answer(new AnswerRequest(roundId, "2")));
    }

    @Test
    void answerShouldAcceptRoundJustBeforeExpiration() {
        String roundId = registry.register(round);

        clock.advance(Duration.ofMinutes(10).minusNanos(1));

        assertTrue(registry.answer(
                new AnswerRequest(roundId, "2")).correct());
    }

    @Test
    void registerShouldRejectRoundWhenCorrectCharacterIsNotAnOption() {
        GameRound invalidRound = new GameRound(
                round.quote(),
                round.options(),
                "999");

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(invalidRound));
    }

    @Test
    void constructorShouldRejectNonPositiveTtl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GameRoundRegistry(clock, Duration.ZERO, 2));

        assertThrows(
                IllegalArgumentException.class,
                () -> new GameRoundRegistry(clock, Duration.ofMinutes(-1), 2));
    }

    @Test
    void constructorShouldRejectMaxActiveRoundsBelowOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GameRoundRegistry(clock, Duration.ofMinutes(10), 0));
    }

    @Test
    void registerShouldRejectNewRoundWhenCapacityIsReached() {
        String firstId = registry.register(round);
        String secondId = registry.register(round);

        assertThrows(
                RoundCapacityExceededException.class,
                () -> registry.register(round));

        // As rodadas que já existiam continuam respondíveis.
        assertTrue(registry.answer(
                new AnswerRequest(firstId, "2")).correct());

        assertTrue(registry.answer(
                new AnswerRequest(secondId, "2")).correct());
    }

    @Test
    void registerShouldRemoveExpiredRoundsAndFreeCapacity() {
        String firstId = registry.register(round);
        registry.register(round);

        clock.advance(Duration.ofMinutes(10));

        // A rodada expirada é removida durante o novo registro.
        String newId = registry.register(round);

        assertThrows(
                RoundNotFoundException.class,
                () -> registry.answer(new AnswerRequest(firstId, "2")));

        assertTrue(registry.answer(
                new AnswerRequest(newId, "2")).correct());
    }

    @Test
    void onlyOneConcurrentAnswerShouldBeAccepted() throws Exception {
        String roundId = registry.register(round);

        int numberOfThreads = 16;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch ready = new CountDownLatch(numberOfThreads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger alreadyAnswered = new AtomicInteger();

        var futures = new ArrayList<Future<?>>();

        try {
            for (int i = 0; i < numberOfThreads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Timed out waiting for concurrent start");
                        }

                        registry.answer(new AnswerRequest(roundId, "2"));
                        accepted.incrementAndGet();

                    } catch (RoundAlreadyAnsweredException exception) {
                        alreadyAnswered.incrementAndGet();

                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "Thread interrupted", exception);
                    }
                }));
            }

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "All threads should be ready");

            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertEquals(1, accepted.get());
            assertEquals(numberOfThreads - 1, alreadyAnswered.get());

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
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
