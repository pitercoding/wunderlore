package com.wunderlore.game.whosaidit;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhoSaidItRoundRegistryTest {

    private MutableClock clock;
    private WhoSaidItRoundRegistry registry;

    private final WhoSaidItRound round = new WhoSaidItRound(
            "A quote",
            List.of(
                    new WhoSaidItRound.CharacterOption("1", "Frodo"),
                    new WhoSaidItRound.CharacterOption("2", "Gandalf"),
                    new WhoSaidItRound.CharacterOption("3", "Aragorn"),
                    new WhoSaidItRound.CharacterOption("4", "Legolas")),
            "2");

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        registry = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 2);
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

        WhoSaidItAnswerResponse response = registry.answer(roundId, "2");

        assertEquals(roundId, response.roundId());
        assertEquals("2", response.submittedCharacterId());
        assertTrue(response.correct());
        assertEquals("2", response.correctCharacterId());
    }

    @Test
    void answerShouldReturnFalseWhenAnswerIsWrong() {
        String roundId = registry.register(round);

        WhoSaidItAnswerResponse response = registry.answer(roundId, "1");

        assertFalse(response.correct());
        assertEquals("2", response.correctCharacterId());
    }

    @Test
    void answerShouldRejectUnknownRound() {
        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> registry.answer("unknown", "2"));
    }

    @Test
    void invalidCharacterShouldNotConsumeAttempt() {
        String roundId = registry.register(round);

        assertThrows(
                WhoSaidItInvalidCharacterOptionException.class,
                () -> registry.answer(roundId, "999"));

        WhoSaidItAnswerResponse response = registry.answer(roundId, "2");

        assertTrue(response.correct());
    }

    @Test
    void answerShouldRejectSecondValidAttempt() {
        String roundId = registry.register(round);

        registry.answer(roundId, "1");

        assertThrows(
                WhoSaidItRoundAlreadyAnsweredException.class,
                () -> registry.answer(roundId, "2"));
    }

    @Test
    void answerShouldRejectExpiredRound() {
        String roundId = registry.register(round);

        clock.advance(Duration.ofMinutes(10));

        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> registry.answer(roundId, "2"));
    }

    @Test
    void answerShouldAcceptRoundJustBeforeExpiration() {
        String roundId = registry.register(round);

        clock.advance(Duration.ofMinutes(10).minusNanos(1));

        assertTrue(registry.answer(roundId, "2").correct());
    }

    @Test
    void registerShouldRejectRoundWhenCorrectCharacterIsNotAnOption() {
        WhoSaidItRound invalidRound = new WhoSaidItRound(
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
                () -> new WhoSaidItRoundRegistry(clock, Duration.ZERO, 2));

        assertThrows(
                IllegalArgumentException.class,
                () -> new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(-1), 2));
    }

    @Test
    void constructorShouldRejectMaxActiveRoundsBelowOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 0));
    }

    @Test
    void registerShouldRejectNewRoundWhenCapacityIsReached() {
        String firstId = registry.register(round);
        String secondId = registry.register(round);

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> registry.register(round));

        // As rodadas que já existiam continuam respondíveis.
        assertTrue(registry.answer(firstId, "2").correct());

        assertTrue(registry.answer(secondId, "2").correct());
    }

    @Test
    void registerShouldRemoveExpiredRoundsAndFreeCapacity() {
        String firstId = registry.register(round);
        registry.register(round);

        clock.advance(Duration.ofMinutes(10));

        // A rodada expirada é removida durante o novo registro.
        String newId = registry.register(round);

        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> registry.answer(firstId, "2"));

        assertTrue(registry.answer(newId, "2").correct());
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

                        registry.answer(roundId, "2");
                        accepted.incrementAndGet();

                    } catch (WhoSaidItRoundAlreadyAnsweredException exception) {
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

    @Test
    void answeringShouldFreeCapacityImmediately() {
        String firstId = registry.register(round);
        registry.register(round);

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> registry.register(round));

        registry.answer(firstId, "2");

        // The slot is free right away, without waiting for the TTL; and only one slot.
        registry.register(round);

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> registry.register(round));
    }

    @Test
    void answeredRoundShouldRemainStoredAndKeepReturningAlreadyAnswered() {
        String answeredId = registry.register(round);

        registry.answer(answeredId, "1");

        // Registering new rounds, which sweeps the map, must not drop the answered one.
        registry.register(round);
        registry.register(round);

        assertThrows(
                WhoSaidItRoundAlreadyAnsweredException.class,
                () -> registry.answer(answeredId, "2"));
        assertThrows(
                WhoSaidItRoundAlreadyAnsweredException.class,
                () -> registry.answer(answeredId, "1"));
    }

    @Test
    void invalidAnswerShouldNotFreeCapacity() {
        String firstId = registry.register(round);
        registry.register(round);

        assertThrows(
                WhoSaidItInvalidCharacterOptionException.class,
                () -> registry.answer(firstId, "999"));

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> registry.register(round));

        // The round is still open and answerable.
        assertTrue(registry.answer(firstId, "2").correct());
    }

    @Test
    void unknownOrExpiredAnswerShouldNotFreeCapacity() {
        registry.register(round);
        registry.register(round);

        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> registry.answer("unknown", "2"));

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> registry.register(round));
    }

    @Test
    void answeringTheSameRoundTwiceShouldFreeOnlyOneSlot() {
        String firstId = registry.register(round);
        registry.register(round);

        registry.answer(firstId, "2");

        assertThrows(
                WhoSaidItRoundAlreadyAnsweredException.class,
                () -> registry.answer(firstId, "2"));

        registry.register(round);

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> registry.register(round));
    }

    @Test
    void multipleAnsweredRoundsShouldFreeTheCorrespondingNumberOfSlots() {
        WhoSaidItRoundRegistry larger = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 4);
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            ids.add(larger.register(round));
        }

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> larger.register(round));

        larger.answer(ids.get(0), "2");
        larger.answer(ids.get(1), "1");
        larger.answer(ids.get(2), "2");

        for (int i = 0; i < 3; i++) {
            larger.register(round);
        }

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> larger.register(round));
    }

    @Test
    void expiredAnsweredAndOpenRoundsShouldBeCountedSeparately() {
        WhoSaidItRoundRegistry larger = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 3);

        String expiredId = larger.register(round);
        String answeredId = larger.register(round);
        larger.answer(answeredId, "2");

        clock.advance(Duration.ofMinutes(5));

        larger.register(round);
        larger.register(round);

        clock.advance(Duration.ofMinutes(5));

        // The first two rounds are expired and gone; two open rounds remain, so one slot is free.
        larger.register(round);

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> larger.register(round));
        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> larger.answer(expiredId, "2"));
        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> larger.answer(answeredId, "2"));
    }

    @Test
    void concurrentAnswersToTheSameRoundShouldFreeExactlyOneSlot() throws Exception {
        String contestedId = registry.register(round);
        registry.register(round);

        AtomicInteger accepted = new AtomicInteger();
        var tasks = new ArrayList<Runnable>();

        for (int i = 0; i < 16; i++) {
            tasks.add(() -> {
                try {
                    registry.answer(contestedId, "2");
                    accepted.incrementAndGet();
                } catch (WhoSaidItRoundAlreadyAnsweredException exception) {
                    // Expected for every loser.
                }
            });
        }

        runConcurrently(tasks);

        assertEquals(1, accepted.get());

        registry.register(round);

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> registry.register(round));
    }

    @Test
    void concurrentAnswersToDifferentRoundsShouldFreeOneSlotEach() throws Exception {
        int capacity = 10;
        WhoSaidItRoundRegistry larger = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), capacity);
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < capacity; i++) {
            ids.add(larger.register(round));
        }

        var tasks = new ArrayList<Runnable>();

        for (String id : ids) {
            tasks.add(() -> assertTrue(larger.answer(id, "2").correct()));
        }

        runConcurrently(tasks);

        for (int i = 0; i < capacity; i++) {
            larger.register(round);
        }

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> larger.register(round));

        for (String id : ids) {
            assertThrows(
                    WhoSaidItRoundAlreadyAnsweredException.class,
                    () -> larger.answer(id, "2"));
        }
    }

    @Test
    void concurrentRegistrationAndAnsweringShouldNeverExceedCapacity() throws Exception {
        int capacity = 5;
        WhoSaidItRoundRegistry limited = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), capacity);

        List<String> initialIds = new ArrayList<>();

        for (int i = 0; i < capacity; i++) {
            initialIds.add(limited.register(round));
        }

        Set<String> newIds = ConcurrentHashMap.newKeySet();
        Set<String> answeredIds = ConcurrentHashMap.newKeySet();
        AtomicInteger rejected = new AtomicInteger();
        var tasks = new ArrayList<Runnable>();

        for (String id : initialIds) {
            tasks.add(() -> {
                limited.answer(id, "2");
                answeredIds.add(id);
            });
        }

        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                try {
                    newIds.add(limited.register(round));
                } catch (WhoSaidItRoundCapacityExceededException exception) {
                    rejected.incrementAndGet();
                }
            });
        }

        runConcurrently(tasks);

        // Only the five answers could have freed slots.
        assertTrue(newIds.size() <= capacity, "At most five registrations can fit");
        assertEquals(20, newIds.size() + rejected.get());

        // Nothing answered yet among the new rounds, so they are exactly the open ones.
        long open = initialIds.stream().filter(id -> !answeredIds.contains(id)).count() + newIds.size();

        assertTrue(open <= capacity, "Open rounds must never exceed capacity");
    }

    @Test
    void answeredRoundShouldBeNotFoundAfterExpirationAndFreeCapacity() {
        String answeredId = registry.register(round);
        registry.answer(answeredId, "2");
        registry.register(round);

        clock.advance(Duration.ofMinutes(10));

        // Após o TTL, a rodada respondida é tratada como expirada, não como já respondida.
        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> registry.answer(answeredId, "2"));

        // A limpeza no registro libera as duas vagas ocupadas.
        registry.register(round);
        registry.register(round);

        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> registry.answer(answeredId, "2"));
    }

    @Test
    void invalidCharacterAfterValidAnswerShouldBeRejectedAsInvalidOption() {
        String roundId = registry.register(round);

        registry.answer(roundId, "2");

        // A validação das opções acontece antes da verificação de resposta duplicada.
        assertThrows(
                WhoSaidItInvalidCharacterOptionException.class,
                () -> registry.answer(roundId, "999"));
    }

    @Test
    void concurrentRegistrationsShouldNotExceedCapacity() throws Exception {
        int maxActiveRounds = 5;
        int numberOfThreads = 20;

        WhoSaidItRoundRegistry limitedRegistry = new WhoSaidItRoundRegistry(
                clock, Duration.ofMinutes(10), maxActiveRounds);

        Set<String> registeredIds = ConcurrentHashMap.newKeySet();
        AtomicInteger rejected = new AtomicInteger();

        var tasks = new ArrayList<Runnable>();

        for (int i = 0; i < numberOfThreads; i++) {
            WhoSaidItRound distinctRound = new WhoSaidItRound(
                    "Quote " + i, round.options(), round.correctCharacterId());

            tasks.add(() -> {
                try {
                    registeredIds.add(limitedRegistry.register(distinctRound));
                } catch (WhoSaidItRoundCapacityExceededException exception) {
                    rejected.incrementAndGet();
                }
            });
        }

        runConcurrently(tasks);

        assertEquals(maxActiveRounds, registeredIds.size());
        assertEquals(numberOfThreads - maxActiveRounds, rejected.get());

        for (String roundId : registeredIds) {
            assertTrue(limitedRegistry.answer(roundId, "2").correct());
        }
    }

    @Test
    void concurrentAnswersDuringExpiredRoundCleanupShouldAcceptAtMostOne() throws Exception {
        String roundId = registry.register(round);

        int numberOfAnswerThreads = 15;

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger alreadyAnswered = new AtomicInteger();
        AtomicInteger notFound = new AtomicInteger();
        AtomicReference<String> newRoundId = new AtomicReference<>();

        var tasks = new ArrayList<Runnable>();

        for (int i = 0; i < numberOfAnswerThreads; i++) {
            tasks.add(() -> {
                try {
                    registry.answer(roundId, "2");
                    accepted.incrementAndGet();

                } catch (WhoSaidItRoundAlreadyAnsweredException exception) {
                    alreadyAnswered.incrementAndGet();

                } catch (WhoSaidItRoundNotFoundException exception) {
                    notFound.incrementAndGet();
                }
            });
        }

        // Expira a rodada e dispara a limpeza enquanto as respostas competem.
        tasks.add(() -> {
            clock.advance(Duration.ofMinutes(10));
            newRoundId.set(registry.register(round));
        });

        runConcurrently(tasks);

        // Uma resposta que leu o relógio antes da expiração ainda pode ser aceita.
        assertTrue(accepted.get() <= 1, "At most one answer should be accepted");
        assertEquals(
                numberOfAnswerThreads,
                accepted.get() + alreadyAnswered.get() + notFound.get());

        if (alreadyAnswered.get() > 0) {
            assertEquals(1, accepted.get());
        }

        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> registry.answer(roundId, "2"));

        assertTrue(registry.answer(newRoundId.get(), "2").correct());
    }

    @Test
    void totalRetentionShouldBeBoundedAndDropTheOldestCompletedRoundsFirst() {
        WhoSaidItRoundRegistry bounded = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 2, 4);
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            String id = bounded.register(round);

            bounded.answer(id, "2");
            ids.add(id);
        }

        // Only the four most recently answered records remain: they still read as 409.
        for (int i = 6; i < 10; i++) {
            String id = ids.get(i);

            assertThrows(
                    WhoSaidItRoundAlreadyAnsweredException.class,
                    () -> bounded.answer(id, "2"));
        }

        // The older ones were dropped to stay within the bound: they read as unknown.
        for (int i = 0; i < 6; i++) {
            String id = ids.get(i);

            assertThrows(
                    WhoSaidItRoundNotFoundException.class,
                    () -> bounded.answer(id, "2"));
        }
    }

    @Test
    void completedRoundsShouldBeDroppedInTheOrderTheyWereAnswered() {
        WhoSaidItRoundRegistry bounded = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 3, 3);

        String a = bounded.register(round);
        String b = bounded.register(round);
        String c = bounded.register(round);

        // b is answered before a, although a was created first.
        bounded.answer(b, "2");
        bounded.answer(a, "2");

        String d = bounded.register(round);

        assertThrows(WhoSaidItRoundNotFoundException.class, () -> bounded.answer(b, "2"));
        assertThrows(WhoSaidItRoundAlreadyAnsweredException.class, () -> bounded.answer(a, "2"));
        assertTrue(bounded.answer(c, "2").correct());
        assertTrue(bounded.answer(d, "2").correct());
    }

    @Test
    void openRoundsShouldNeverBeDroppedToMakeRoom() {
        WhoSaidItRoundRegistry bounded = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 2, 2);

        String a = bounded.register(round);
        String b = bounded.register(round);

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> bounded.register(round));

        bounded.answer(a, "2");

        // The only thing that can make room is the completed round.
        String c = bounded.register(round);

        assertThrows(WhoSaidItRoundNotFoundException.class, () -> bounded.answer(a, "2"));
        assertTrue(bounded.answer(b, "2").correct());
        assertTrue(bounded.answer(c, "2").correct());
    }

    @Test
    void expiredRoundsShouldReleaseBothTheActiveAndTheTotalCapacity() {
        WhoSaidItRoundRegistry bounded = new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 2, 3);

        String answeredA = bounded.register(round);
        String answeredB = bounded.register(round);
        bounded.answer(answeredA, "2");
        bounded.answer(answeredB, "2");
        bounded.register(round);

        clock.advance(Duration.ofMinutes(10));

        // Expired answered rounds read as unknown, not as already answered.
        assertThrows(WhoSaidItRoundNotFoundException.class, () -> bounded.answer(answeredA, "2"));
        assertThrows(WhoSaidItRoundNotFoundException.class, () -> bounded.answer(answeredB, "2"));

        // The expired open round no longer counts either: exactly two fit.
        bounded.register(round);
        bounded.register(round);

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> bounded.register(round));
    }

    @Test
    void defaultRetainedLimitShouldBeTwiceTheActiveLimit() {
        // registry is built with two active rounds and no explicit retained limit.
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            String id = registry.register(round);

            registry.answer(id, "2");
            ids.add(id);
        }

        assertThrows(
                WhoSaidItRoundNotFoundException.class,
                () -> registry.answer(ids.get(0), "2"));

        for (int i = 1; i < 5; i++) {
            String id = ids.get(i);

            assertThrows(
                    WhoSaidItRoundAlreadyAnsweredException.class,
                    () -> registry.answer(id, "2"));
        }
    }

    @Test
    void constructorShouldRejectRetainedLimitBelowTheActiveLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 5, 4));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 5, -1));

        // Equal limits are allowed: no room for completed records beyond the open ones.
        new WhoSaidItRoundRegistry(clock, Duration.ofMinutes(10), 5, 5);
    }

    @Test
    void concurrentRegistrationAndAnsweringAtCapacityShouldLeaveConsistentCounts() throws Exception {
        int maxActive = 5;
        WhoSaidItRoundRegistry storm = new WhoSaidItRoundRegistry(
                clock, Duration.ofMinutes(10), maxActive, 8);
        var tasks = new ArrayList<Runnable>();

        for (int i = 0; i < 40; i++) {
            tasks.add(() -> {
                try {
                    String id = storm.register(round);

                    storm.answer(id, "2");
                } catch (WhoSaidItRoundCapacityExceededException exception) {
                    // Expected while many rounds are open at once.
                }
            });
        }

        runConcurrently(tasks);

        // Every round that was created has been answered, so none may still count as open:
        // exactly maxActive new rounds fit, which fails if the open count leaked or went negative.
        for (int i = 0; i < maxActive; i++) {
            storm.register(round);
        }

        assertThrows(
                WhoSaidItRoundCapacityExceededException.class,
                () -> storm.register(round));
    }

    // Libera todas as tarefas ao mesmo tempo; exceções não tratadas falham o teste via Future.get.
    private static void runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);

        var futures = new ArrayList<Future<?>>();

        try {
            for (Runnable task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Timed out waiting for concurrent start");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "Thread interrupted", exception);
                    }

                    task.run();
                }));
            }

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "All threads should be ready");

            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertTrue(
                executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor should terminate");
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
