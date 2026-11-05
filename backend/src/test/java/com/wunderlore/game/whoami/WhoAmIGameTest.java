package com.wunderlore.game.whoami;

import static com.wunderlore.game.whoami.WhoAmITestData.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wunderlore.game.whoami.WhoAmIException.Reason;
import com.wunderlore.game.whoami.WhoAmIGame.AnswerOutcome;
import com.wunderlore.game.whoami.WhoAmIGame.Result;
import com.wunderlore.game.whoami.WhoAmITestData.FixedDrawer;

// The rules of one game, with the correct option always "a" and a wrong one "b".
class WhoAmIGameTest {

    private static void assertReason(Runnable action, Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(WhoAmIException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private static WhoAmIGame newGame() {
        return WhoAmITestData.game(WhoAmIDifficulty.MEDIUM, START);
    }

    // Answers the current round wrongly `wrong` times, then correctly (if wrong < 5).
    private static AnswerOutcome play(WhoAmIGame game, int round, int wrong) {
        AnswerOutcome outcome = null;

        for (int clue = 1; clue <= wrong; clue++) {
            outcome = game.answer(round, clue, "b");
        }

        if (wrong < WhoAmIGame.TOTAL_CLUES) {
            outcome = game.answer(round, wrong + 1, "a");
        }

        return outcome;
    }

    @Test
    void shouldHaveTenRoundsAndAMaximumOfOneThousandPoints() {
        WhoAmIGame game = newGame();

        assertThat(game.currentRound().totalRounds()).isEqualTo(10);
        assertThat(game.currentRound().totalClues()).isEqualTo(5);
        assertThat(game.currentRound().maxScore()).isEqualTo(1000);
    }

    @Test
    void shouldScoreEachClueAsSpecified() {
        int[] expected = {100, 80, 60, 40, 20};

        for (int wrong = 0; wrong < 5; wrong++) {
            WhoAmIGame game = newGame();
            AnswerOutcome outcome = play(game, 1, wrong);

            assertThat(outcome.correct()).isTrue();
            assertThat(outcome.roundFinished()).isTrue();
            assertThat(outcome.clueNumber()).isEqualTo(wrong + 1);
            assertThat(outcome.pointsAwarded()).as("clue %d", wrong + 1).isEqualTo(expected[wrong]);
            assertThat(outcome.score()).isEqualTo(expected[wrong]);
        }
    }

    @Test
    void shouldScoreZeroAfterFiveWrongAnswersAndRevealTheCharacter() {
        WhoAmIGame game = newGame();
        AnswerOutcome outcome = play(game, 1, 5);

        assertThat(outcome.correct()).isFalse();
        assertThat(outcome.roundFinished()).isTrue();
        assertThat(outcome.pointsAwarded()).isZero();
        assertThat(outcome.score()).isZero();
        assertThat(outcome.next()).isNull();
        assertThat(outcome.reveal().characterName()).isEqualTo("Alderon");
        assertThat(outcome.reveal().clues()).hasSize(5);
        // The game moved on to the second character.
        assertThat(game.currentRound().roundNumber()).isEqualTo(2);
        assertThat(game.currentRound().clueNumber()).isEqualTo(1);
    }

    @Test
    void shouldRevealTheNextClueAfterAWrongAnswerAndNothingElse() {
        WhoAmIGame game = newGame();

        assertThat(game.currentRound().clues()).containsExactly("Hint 1 about subject 0.");

        AnswerOutcome outcome = game.answer(1, 1, "b");

        assertThat(outcome.correct()).isFalse();
        assertThat(outcome.roundFinished()).isFalse();
        assertThat(outcome.reveal()).isNull();
        assertThat(outcome.pointsAwarded()).isZero();
        assertThat(outcome.next().clueNumber()).isEqualTo(2);
        assertThat(outcome.next().clues())
                .containsExactly("Hint 1 about subject 0.", "Hint 2 about subject 0.");
        assertThat(game.currentRound().clueNumber()).isEqualTo(2);
    }

    @Test
    void shouldRuleOutWrongAnswersWhenDrawingTheNextOptions() {
        FixedDrawer drawer = new FixedDrawer();
        WhoAmIGame game = WhoAmITestData.game(WhoAmIDifficulty.EASY, drawer, START);

        game.answer(1, 1, "b");
        game.answer(1, 2, "c");

        // Ten initial draws, then one per wrong answer, excluding what was already guessed.
        assertThat(drawer.excluded).hasSize(12);
        assertThat(drawer.excluded.get(10)).containsExactly("filler-b");
        assertThat(drawer.excluded.get(11)).containsExactlyInAnyOrder("filler-b", "filler-c");
    }

    @Test
    void shouldFinishAfterTenRoundsAndSumTheScoreOnTheServer() {
        WhoAmIGame game = newGame();
        // Rounds: 100, 80, 60, 40, 20 (clues 1-5), then 0 (not identified), then three at clue 1.
        int[] wrongBefore = {0, 1, 2, 3, 4, 5, 0, 0, 0, 0};
        AnswerOutcome last = null;

        for (int round = 1; round <= 10; round++) {
            assertThat(game.isFinished()).isFalse();
            last = play(game, round, wrongBefore[round - 1]);
        }

        assertThat(game.isFinished()).isTrue();
        assertThat(last.gameFinished()).isTrue();
        assertThat(last.result()).isNotNull();
        assertThat(last.score()).isEqualTo(100 + 80 + 60 + 40 + 20 + 0 + 4 * 100);

        Result result = game.result();

        assertThat(result.score()).isEqualTo(700);
        assertThat(result.maxScore()).isEqualTo(1000);
        assertThat(result.percentage()).isEqualTo(70);
        assertThat(result.identified()).isEqualTo(9);
        assertThat(result.unidentified()).isEqualTo(1);
        assertThat(result.incorrectAnswers()).isEqualTo(0 + 1 + 2 + 3 + 4 + 5);
        assertThat(result.rounds()).hasSize(10);
        assertThat(result.rounds().get(0).cluesUsed()).isEqualTo(1);
        assertThat(result.rounds().get(4).cluesUsed()).isEqualTo(5);
        assertThat(result.rounds().get(5).identified()).isFalse();
        assertThat(result.rounds().get(5).cluesUsed()).isEqualTo(5);
        assertThat(result.rounds().get(5).points()).isZero();
        assertThat(result.rounds().get(5).wrongAttempts()).isEqualTo(5);
    }

    @Test
    void shouldNotBeAbleToAnswerOrReadARoundAfterTheGameFinished() {
        WhoAmIGame game = newGame();

        for (int round = 1; round <= 10; round++) {
            play(game, round, 0);
        }

        assertReason(() -> game.answer(10, 1, "a"), Reason.GAME_FINISHED);
        assertReason(game::currentRound, Reason.GAME_FINISHED);
        assertThat(game.result().score()).isEqualTo(1000);
    }

    @Test
    void shouldRefuseTheResultWhileTheGameIsOpen() {
        WhoAmIGame game = newGame();

        assertReason(game::result, Reason.GAME_NOT_FINISHED);

        play(game, 1, 0);

        assertReason(game::result, Reason.GAME_NOT_FINISHED);
    }

    @Test
    void shouldRefuseAnAnswerSentTwiceAndLeaveTheScoreAlone() {
        WhoAmIGame game = newGame();

        game.answer(1, 1, "a");

        // The same request again: round 1 is over, so it is stale, and nothing changes.
        assertReason(() -> game.answer(1, 1, "a"), Reason.STALE_SUBMISSION);
        assertThat(game.currentRound().score()).isEqualTo(100);
        assertThat(game.currentRound().roundNumber()).isEqualTo(2);
    }

    @Test
    void shouldAllowOnlyOneAttemptPerClue() {
        WhoAmIGame game = newGame();

        game.answer(1, 1, "b");

        // A second try at clue 1 is stale; the game waits at clue 2.
        assertReason(() -> game.answer(1, 1, "a"), Reason.STALE_SUBMISSION);
        assertThat(game.currentRound().clueNumber()).isEqualTo(2);
    }

    @Test
    void shouldRefuseTheWrongRoundOrClue() {
        WhoAmIGame game = newGame();

        assertReason(() -> game.answer(2, 1, "a"), Reason.STALE_SUBMISSION);
        assertReason(() -> game.answer(1, 2, "a"), Reason.STALE_SUBMISSION);
        assertReason(() -> game.answer(0, 1, "a"), Reason.STALE_SUBMISSION);
        assertThat(game.currentRound().score()).isZero();
    }

    @Test
    void shouldRefuseAnOptionThatWasNotOffered() {
        WhoAmIGame game = newGame();

        assertReason(() -> game.answer(1, 1, "z"), Reason.INVALID_OPTION);
        assertReason(() -> game.answer(1, 1, ""), Reason.INVALID_OPTION);
        assertThat(game.currentRound().clueNumber()).isEqualTo(1);
    }

    @Test
    void shouldNeverExposeTheCharacterOrLaterCluesInAView() {
        WhoAmIGame game = newGame();
        WhoAmIGame.RoundView view = game.currentRound();

        assertThat(view.clues()).hasSize(1);
        assertThat(view.options()).hasSize(4);
        // Alderon is an option here only because the test drawer always offers it; the view
        // itself has no field for the character, the correct option or later clues.
        assertThat(view.toString()).doesNotContain("Hint 2", "Hint 5", "correct", "characterId");
    }

    @Test
    void shouldRefuseAGameWithTheWrongNumberOfCharacters() {
        assertThatThrownBy(() -> new WhoAmIGame(
                WhoAmIDifficulty.EASY,
                List.of(WhoAmITestData.character(0, WhoAmIDifficulty.EASY)),
                new FixedDrawer(),
                START))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
