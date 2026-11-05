package com.wunderlore.game.whoami;

import static com.wunderlore.game.whoami.WhoAmITestData.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wunderlore.game.whoami.WhoAmIException.Reason;
import com.wunderlore.game.whoami.WhoAmITestData.MutableClock;

class WhoAmIGameRegistryTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
    }

    private WhoAmIGame newGame() {
        return WhoAmITestData.game(WhoAmIDifficulty.EASY, clock.instant());
    }

    private static void assertReason(Runnable action, Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(WhoAmIException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private static void finish(WhoAmIGameRegistry registry, String id) {
        for (int round = 1; round <= 10; round++) {
            registry.answer(id, round, 1, "a");
        }
    }

    @Test
    void shouldIssueDistinctUnguessableIds() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 10);

        String first = registry.register(newGame());
        String second = registry.register(newGame());

        assertThat(first).isNotEqualTo(second).matches("[0-9a-f-]{36}");
    }

    @Test
    void shouldPlayAGameThroughTheRegistry() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        assertThat(registry.currentRound(id).roundNumber()).isEqualTo(1);
        assertThat(registry.answer(id, 1, 1, "a").score()).isEqualTo(100);

        finishFromRoundTwo(registry, id);

        assertThat(registry.result(id).score()).isEqualTo(1000);
    }

    private static void finishFromRoundTwo(WhoAmIGameRegistry registry, String id) {
        for (int round = 2; round <= 10; round++) {
            registry.answer(id, round, 1, "a");
        }
    }

    @Test
    void shouldAnswerNotFoundForUnknownGames() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 10);

        assertReason(() -> registry.currentRound("nope"), Reason.GAME_NOT_FOUND);
        assertReason(() -> registry.answer("nope", 1, 1, "a"), Reason.GAME_NOT_FOUND);
        assertReason(() -> registry.result("nope"), Reason.GAME_NOT_FOUND);
    }

    @Test
    void shouldForgetAGameAfterItsTtl() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        clock.advance(TTL.minusSeconds(1));
        assertThat(registry.currentRound(id).roundNumber()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertReason(() -> registry.currentRound(id), Reason.GAME_NOT_FOUND);
        assertReason(() -> registry.answer(id, 1, 1, "a"), Reason.GAME_NOT_FOUND);
    }

    @Test
    void shouldCountTheTtlFromCreationNotFromTheLastAnswer() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        clock.advance(TTL.minusSeconds(1));
        registry.answer(id, 1, 1, "a");
        clock.advance(Duration.ofSeconds(2));

        assertReason(() -> registry.currentRound(id), Reason.GAME_NOT_FOUND);
    }

    @Test
    void shouldKeepAFinishedGameResultUntilItsTtl() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        finish(registry, id);
        clock.advance(TTL.minusSeconds(1));

        assertThat(registry.result(id).score()).isEqualTo(1000);

        clock.advance(Duration.ofSeconds(1));
        assertReason(() -> registry.result(id), Reason.GAME_NOT_FOUND);
    }

    @Test
    void shouldRefuseNewGamesAtTheActiveLimitAndFreeASlotWhenOneFinishes() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 2);
        String first = registry.register(newGame());

        registry.register(newGame());

        assertReason(() -> registry.register(newGame()), Reason.CAPACITY_EXCEEDED);

        finish(registry, first);

        assertThat(registry.register(newGame())).isNotBlank();
        assertReason(() -> registry.register(newGame()), Reason.CAPACITY_EXCEEDED);
    }

    @Test
    void shouldFreeASlotWhenAGameExpires() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 1);

        registry.register(newGame());
        assertReason(() -> registry.register(newGame()), Reason.CAPACITY_EXCEEDED);

        clock.advance(TTL);

        assertThat(registry.register(newGame())).isNotBlank();
    }

    @Test
    void shouldDropTheOldestFinishedGameWhenTheRetainedLimitIsReached() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 2, 3);
        String a = registry.register(newGame());
        String b = registry.register(newGame());

        finish(registry, a);
        finish(registry, b);

        String c = registry.register(newGame());

        // Three stored. A fourth makes room by dropping the game that finished first.
        String d = registry.register(newGame());

        assertReason(() -> registry.result(a), Reason.GAME_NOT_FOUND);
        assertThat(registry.result(b).score()).isEqualTo(1000);
        assertThat(registry.currentRound(c).roundNumber()).isEqualTo(1);
        assertThat(registry.currentRound(d).roundNumber()).isEqualTo(1);
    }

    @Test
    void shouldRefuseInvalidLimits() {
        assertThatThrownBy(() -> new WhoAmIGameRegistry(clock, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WhoAmIGameRegistry(clock, Duration.ofSeconds(-1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WhoAmIGameRegistry(clock, TTL, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WhoAmIGameRegistry(clock, TTL, 5, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRefuseAnAnswerSentTwiceThroughTheRegistry() {
        WhoAmIGameRegistry registry = new WhoAmIGameRegistry(clock, TTL, 10);
        String id = registry.register(newGame());

        registry.answer(id, 1, 1, "a");

        assertReason(() -> registry.answer(id, 1, 1, "a"), Reason.STALE_SUBMISSION);
        assertThat(registry.currentRound(id).score()).isEqualTo(100);
    }
}
