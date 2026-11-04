package com.wunderlore.game.lorechallenge;

import static com.wunderlore.game.lorechallenge.LoreChallengeTestData.START;
import static com.wunderlore.game.lorechallenge.LoreChallengeTestData.match;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wunderlore.game.lorechallenge.LoreChallengeException.Reason;
import com.wunderlore.game.lorechallenge.LoreChallengeTestData.MutableClock;

class LoreChallengeMatchRegistryTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
    }

    private LoreChallengeMatch newMatch(int size) {
        return match(LoreChallengeDifficulty.EASY, size, clock.instant());
    }

    private static void assertReason(Runnable action, Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(LoreChallengeException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private void finish(LoreChallengeMatchRegistry registry, String id, int size) {
        for (int i = 1; i <= size; i++) {
            registry.answer(id, "q" + i, "a");
        }
    }

    @Test
    void shouldIssueDistinctUnguessableIds() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 10);

        String first = registry.register(newMatch(2));
        String second = registry.register(newMatch(2));

        assertThat(first).isNotEqualTo(second).matches("[0-9a-f-]{36}");
    }

    @Test
    void shouldPlayAMatchThroughTheRegistry() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 10);
        String id = registry.register(newMatch(2));

        assertThat(registry.currentQuestion(id).questionId()).isEqualTo("q1");
        assertThat(registry.answer(id, "q1", "a").score()).isEqualTo(10);
        assertThat(registry.answer(id, "q2", "b").finished()).isTrue();
        assertThat(registry.result(id).score()).isEqualTo(10);
    }

    @Test
    void shouldAnswerNotFoundForUnknownMatches() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 10);

        assertReason(() -> registry.currentQuestion("nope"), Reason.MATCH_NOT_FOUND);
        assertReason(() -> registry.answer("nope", "q1", "a"), Reason.MATCH_NOT_FOUND);
        assertReason(() -> registry.result("nope"), Reason.MATCH_NOT_FOUND);
    }

    @Test
    void shouldForgetAMatchAfterItsTtl() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 10);
        String id = registry.register(newMatch(2));

        clock.advance(TTL.minusSeconds(1));
        assertThat(registry.currentQuestion(id).questionId()).isEqualTo("q1");

        clock.advance(Duration.ofSeconds(1));
        assertReason(() -> registry.currentQuestion(id), Reason.MATCH_NOT_FOUND);
        assertReason(() -> registry.answer(id, "q1", "a"), Reason.MATCH_NOT_FOUND);
    }

    @Test
    void shouldKeepAFinishedResultReadableUntilTheTtl() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 10);
        String id = registry.register(newMatch(2));

        finish(registry, id, 2);
        clock.advance(TTL.minusSeconds(1));

        assertThat(registry.result(id).correctAnswers()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(1));
        assertReason(() -> registry.result(id), Reason.MATCH_NOT_FOUND);
    }

    @Test
    void shouldRefuseNewMatchesAtTheActiveLimit() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 2);

        registry.register(newMatch(2));
        registry.register(newMatch(2));

        assertReason(() -> registry.register(newMatch(2)), Reason.CAPACITY_EXCEEDED);
    }

    @Test
    void shouldFreeASlotWhenAMatchFinishes() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 2);
        String first = registry.register(newMatch(1));

        registry.register(newMatch(1));
        finish(registry, first, 1);

        assertThat(registry.register(newMatch(1))).isNotBlank();
        assertReason(() -> registry.register(newMatch(1)), Reason.CAPACITY_EXCEEDED);
    }

    @Test
    void shouldFreeSlotsWhenOpenMatchesExpire() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 2);

        registry.register(newMatch(2));
        registry.register(newMatch(2));
        clock.advance(TTL);

        assertThat(registry.register(newMatch(2))).isNotBlank();
    }

    @Test
    void shouldDropTheOldestFinishedMatchWhenTheTotalLimitIsReached() {
        // Two open at most, three stored at most.
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 2, 3);
        String firstDone = registry.register(newMatch(1));
        String secondDone = registry.register(newMatch(1));

        finish(registry, firstDone, 1);
        finish(registry, secondDone, 1);

        String third = registry.register(newMatch(1));
        String fourth = registry.register(newMatch(1));

        // The first finished match made room for the fourth; the second is still readable.
        assertReason(() -> registry.result(firstDone), Reason.MATCH_NOT_FOUND);
        assertThat(registry.result(secondDone).score()).isEqualTo(10);
        assertThat(registry.currentQuestion(third).questionId()).isEqualTo("q1");
        assertThat(registry.currentQuestion(fourth).questionId()).isEqualTo("q1");
    }

    @Test
    void shouldNeverDropAnOpenMatchToMakeRoom() {
        LoreChallengeMatchRegistry registry = new LoreChallengeMatchRegistry(clock, TTL, 2, 2);
        String first = registry.register(newMatch(1));

        registry.register(newMatch(1));

        assertReason(() -> registry.register(newMatch(1)), Reason.CAPACITY_EXCEEDED);
        assertThat(registry.currentQuestion(first).questionId()).isEqualTo("q1");
    }

    @Test
    void shouldRejectInvalidLimits() {
        assertThatThrownBy(() -> new LoreChallengeMatchRegistry(clock, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoreChallengeMatchRegistry(clock, TTL, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoreChallengeMatchRegistry(clock, TTL, 5, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoreChallengeMatchRegistry(clock, TTL, 5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
