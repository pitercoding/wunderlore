package com.wunderlore.game.whereinmiddleearth;

import static com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthTestData.TARGET_Y;
import static com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthTestData.targetX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthException.Reason;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.AnswerOutcome;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.Result;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.RoundView;

class WhereInMiddleEarthGameTest {

    private static void assertReason(Runnable action, Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(WhereInMiddleEarthException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    @Test
    void shouldStartAtTheFirstRoundWithNoScore() {
        RoundView round = WhereInMiddleEarthTestData.game().currentRound();

        assertThat(round.roundNumber()).isEqualTo(1);
        assertThat(round.totalRounds()).isEqualTo(10);
        assertThat(round.score()).isZero();
        assertThat(round.maxScore()).isEqualTo(1000);
        assertThat(round.locationName()).isEqualTo("Place A");
        assertThat(round.description()).isNotBlank();
    }

    @Test
    void shouldNeverShowWhereTheAskedPlaceIsBeforeItIsAnswered() {
        // By construction: the view has no field that could carry a position.
        List<String> fields = Arrays.stream(RoundView.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertThat(fields).containsExactly(
                "difficulty", "roundNumber", "totalRounds", "locationName", "description",
                "score", "maxScore");
    }

    @Test
    void shouldScoreAPerfectClickAndRevealThePlace() {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();

        AnswerOutcome outcome = game.answer(1, targetX(1), TARGET_Y);

        assertThat(outcome.distance()).isZero();
        assertThat(outcome.accuracy()).isEqualTo(WhereInMiddleEarthAccuracy.EXCELLENT);
        assertThat(outcome.pointsAwarded()).isEqualTo(100);
        assertThat(outcome.score()).isEqualTo(100);
        assertThat(outcome.reveal().locationName()).isEqualTo("Place A");
        assertThat(outcome.reveal().x()).isEqualTo(targetX(1));
        assertThat(outcome.reveal().y()).isEqualTo(TARGET_Y);
        assertThat(outcome.reveal().regionName()).isEqualTo("Test Region");
        assertThat(outcome.reveal().explanation()).isNotBlank();
        assertThat(outcome.submitted().x()).isEqualTo(targetX(1));
        assertThat(outcome.gameFinished()).isFalse();
        assertThat(outcome.result()).isNull();
        assertThat(game.currentRound().roundNumber()).isEqualTo(2);
        assertThat(game.currentRound().score()).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, EXCELLENT, 100",
            "15, 0, EXCELLENT, 100",
            "0, 25, VERY_GOOD, 80",
            "-30, 0, VERY_GOOD, 80",
            "-40, 0, GOOD, 60",
            "0, -55, GOOD, 60",
            "0, -70, CLOSE, 40",
            "90, 0, CLOSE, 40",
            "0, 91, FAR, 0",
            "300, 300, FAR, 0"
    })
    void shouldScoreFromTheDistanceOnTheServer(
            double dx, double dy, WhereInMiddleEarthAccuracy expected, int points) {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();

        // Round 2 asks about (140, 200), far enough from every border for every offset.
        game.answer(1, targetX(1), TARGET_Y);

        AnswerOutcome outcome = game.answer(2, targetX(2) + dx, TARGET_Y + dy);

        assertThat(outcome.accuracy()).isEqualTo(expected);
        assertThat(outcome.pointsAwarded()).isEqualTo(points);
        assertThat(outcome.score()).isEqualTo(100 + points);
    }

    @Test
    void shouldMeasureDistanceInMapUnitsAndRoundItToATenth() {
        assertThat(WhereInMiddleEarthTestData.game().answer(1, 53.0, 204.0).distance())
                .isEqualTo(5.0);
        assertThat(WhereInMiddleEarthTestData.game().answer(1, 51.0, 201.0).distance())
                .isEqualTo(1.4);
    }

    @Test
    void shouldAcceptThePointsOnTheBordersOfTheMap() {
        assertThat(WhereInMiddleEarthTestData.game().answer(1, 0.0, 0.0).accuracy())
                .isEqualTo(WhereInMiddleEarthAccuracy.FAR);
        assertThat(WhereInMiddleEarthTestData.game().answer(1, 1000.0, 700.0).accuracy())
                .isEqualTo(WhereInMiddleEarthAccuracy.FAR);
    }

    @ParameterizedTest
    @CsvSource({
            "-0.1, 100",
            "1000.1, 100",
            "100, -0.1",
            "100, 700.1",
            "NaN, 100",
            "100, NaN",
            "Infinity, 100",
            "100, -Infinity"
    })
    void shouldRefuseAPositionOutsideTheMapWithoutConsumingTheRound(double x, double y) {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();

        assertReason(() -> game.answer(1, x, y), Reason.INVALID_COORDINATES);

        assertThat(game.currentRound().roundNumber()).isEqualTo(1);
        assertThat(game.currentRound().score()).isZero();
    }

    @Test
    void shouldRefuseTheSameRoundAnsweredTwiceAndScoreItOnce() {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();

        game.answer(1, targetX(1), TARGET_Y);

        assertReason(() -> game.answer(1, targetX(1), TARGET_Y), Reason.STALE_SUBMISSION);
        assertThat(game.currentRound().score()).isEqualTo(100);
        assertThat(game.currentRound().roundNumber()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"2", "3", "10", "0", "-1", "11"})
    void shouldRefuseARoundThatIsNotTheOneWaiting(int roundNumber) {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();

        assertReason(() -> game.answer(roundNumber, targetX(1), TARGET_Y), Reason.STALE_SUBMISSION);
        assertThat(game.currentRound().roundNumber()).isEqualTo(1);
        assertThat(game.currentRound().score()).isZero();
    }

    @Test
    void shouldPlayTenRoundsAndBuildTheResult() {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();
        AnswerOutcome last = null;

        // Rounds 1-4 perfect, 5 very good, 6 good, 7 close, 8-10 far.
        double[] offsets = {0, 0, 0, 0, 20, 40, 80, 400, 400, 400};

        for (int round = 1; round <= 10; round++) {
            last = game.answer(round, targetX(round), TARGET_Y + offsets[round - 1]);
        }

        assertThat(last.gameFinished()).isTrue();

        Result result = last.result();

        assertThat(result.score()).isEqualTo(4 * 100 + 80 + 60 + 40);
        assertThat(result.maxScore()).isEqualTo(1000);
        assertThat(result.percentage()).isEqualTo(58);
        assertThat(result.closeHits()).isEqualTo(7);
        assertThat(result.averageDistance()).isEqualTo(134.0);
        assertThat(result.rounds()).hasSize(10);
        assertThat(result.rounds().get(0).locationName()).isEqualTo("Place A");
        assertThat(result.rounds().get(4).accuracy()).isEqualTo(WhereInMiddleEarthAccuracy.VERY_GOOD);
        assertThat(result.rounds().get(9).points()).isZero();
        assertThat(game.result().score()).isEqualTo(result.score());
        assertThat(game.isFinished()).isTrue();
    }

    @Test
    void shouldScoreAPerfectGameAsTheMaximum() {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();

        for (int round = 1; round <= 10; round++) {
            game.answer(round, targetX(round), TARGET_Y);
        }

        assertThat(game.result().score()).isEqualTo(WhereInMiddleEarthGame.MAX_SCORE);
        assertThat(game.result().percentage()).isEqualTo(100);
    }

    @Test
    void shouldRefuseAnythingOnceTheGameIsOver() {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();

        for (int round = 1; round <= 10; round++) {
            game.answer(round, targetX(round), TARGET_Y);
        }

        assertReason(game::currentRound, Reason.GAME_FINISHED);
        assertReason(() -> game.answer(10, targetX(10), TARGET_Y), Reason.GAME_FINISHED);
    }

    @Test
    void shouldRefuseTheResultBeforeTheGameIsOver() {
        WhereInMiddleEarthGame game = WhereInMiddleEarthTestData.game();

        game.answer(1, targetX(1), TARGET_Y);

        assertReason(game::result, Reason.GAME_NOT_FINISHED);
    }

    @Test
    void shouldAskForExactlyTenPlaces() {
        List<WhereInMiddleEarthLocation> nine = WhereInMiddleEarthTestData
                .locations(WhereInMiddleEarthDifficulty.EASY).subList(0, 9);

        assertThatThrownBy(() -> new WhereInMiddleEarthGame(
                WhereInMiddleEarthDifficulty.EASY,
                nine,
                location -> "Test Region",
                WhereInMiddleEarthTestData.START))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
