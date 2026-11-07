package com.wunderlore.game.whereinmiddleearth;

import static com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthTestData.START;
import static com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthTestData.TARGET_Y;
import static com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthTestData.targetX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthException.Reason;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthTestData.MutableClock;

class WhereInMiddleEarthGameRegistryTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
    }

    private WhereInMiddleEarthGame newGame() {
        return WhereInMiddleEarthTestData.game(WhereInMiddleEarthDifficulty.EASY, clock.instant());
    }

    private static void assertReason(Runnable action, Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(WhereInMiddleEarthException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private static void finish(WhereInMiddleEarthGameRegistry registry, String id) {
        for (int round = 1; round <= 10; round++) {
            registry.answer(id, round, targetX(round), TARGET_Y);
        }
    }

    @Test
    void shouldIssueDistinctUnguessableIds() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 10);

        String first = registry.register(newGame());
        String second = registry.register(newGame());

        assertThat(first).isNotEqualTo(second).matches("[0-9a-f-]{36}");
    }

    @Test
    void shouldPlayAGameThroughTheRegistry() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        assertThat(registry.currentRound(id).roundNumber()).isEqualTo(1);
        assertThat(registry.answer(id, 1, targetX(1), TARGET_Y).score()).isEqualTo(100);

        for (int round = 2; round <= 10; round++) {
            registry.answer(id, round, targetX(round), TARGET_Y);
        }

        assertThat(registry.result(id).score()).isEqualTo(1000);
    }

    @Test
    void shouldAnswerNotFoundForUnknownGames() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 10);

        assertReason(() -> registry.currentRound("nope"), Reason.GAME_NOT_FOUND);
        assertReason(() -> registry.answer("nope", 1, 1, 1), Reason.GAME_NOT_FOUND);
        assertReason(() -> registry.result("nope"), Reason.GAME_NOT_FOUND);
    }

    @Test
    void shouldForgetAGameAfterItsTtl() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        clock.advance(TTL.minusSeconds(1));
        assertThat(registry.currentRound(id).roundNumber()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertReason(() -> registry.currentRound(id), Reason.GAME_NOT_FOUND);
        assertReason(() -> registry.answer(id, 1, targetX(1), TARGET_Y), Reason.GAME_NOT_FOUND);
    }

    @Test
    void shouldCountTheTtlFromCreationNotFromTheLastAnswer() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        clock.advance(Duration.ofMinutes(20));
        registry.answer(id, 1, targetX(1), TARGET_Y);

        clock.advance(Duration.ofMinutes(10));
        assertReason(() -> registry.currentRound(id), Reason.GAME_NOT_FOUND);
    }

    @Test
    void shouldKeepAFinishedGameResultUntilItsTtl() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        finish(registry, id);
        clock.advance(TTL.minusSeconds(1));

        assertThat(registry.result(id).score()).isEqualTo(1000);

        clock.advance(Duration.ofSeconds(1));
        assertReason(() -> registry.result(id), Reason.GAME_NOT_FOUND);
    }

    @Test
    void shouldRefuseNewGamesAtTheActiveLimitAndFreeASlotWhenOneFinishes() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 2);
        String first = registry.register(newGame());

        registry.register(newGame());
        assertReason(() -> registry.register(newGame()), Reason.CAPACITY_EXCEEDED);

        finish(registry, first);

        assertThat(registry.register(newGame())).isNotNull();
    }

    @Test
    void shouldFreeASlotWhenAGameExpires() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 1);

        registry.register(newGame());
        assertReason(() -> registry.register(newGame()), Reason.CAPACITY_EXCEEDED);

        clock.advance(TTL);

        assertThat(registry.register(newGame())).isNotNull();
    }

    @Test
    void shouldDropTheOldestFinishedGameWhenTheRetainedLimitIsReached() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 2, 3);
        String first = registry.register(newGame());
        String second = registry.register(newGame());

        finish(registry, first);
        finish(registry, second);

        String third = registry.register(newGame());
        String fourth = registry.register(newGame());

        // Three are stored: the game that finished first was dropped to make room.
        assertReason(() -> registry.result(first), Reason.GAME_NOT_FOUND);
        assertThat(registry.result(second).score()).isEqualTo(1000);
        assertThat(registry.currentRound(third).roundNumber()).isEqualTo(1);
        assertThat(registry.currentRound(fourth).roundNumber()).isEqualTo(1);
    }

    @Test
    void shouldRefuseInvalidLimits() {
        assertThatThrownBy(() -> new WhereInMiddleEarthGameRegistry(clock, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WhereInMiddleEarthGameRegistry(clock, TTL, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WhereInMiddleEarthGameRegistry(clock, TTL, 5, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRefuseAnAnswerSentTwiceThroughTheRegistry() {
        WhereInMiddleEarthGameRegistry registry = new WhereInMiddleEarthGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        registry.answer(id, 1, targetX(1), TARGET_Y);

        assertReason(() -> registry.answer(id, 1, targetX(1), TARGET_Y), Reason.STALE_SUBMISSION);
        assertThat(registry.currentRound(id).score()).isEqualTo(100);
    }
}
