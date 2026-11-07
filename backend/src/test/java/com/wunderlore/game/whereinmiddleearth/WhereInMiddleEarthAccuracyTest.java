package com.wunderlore.game.whereinmiddleearth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WhereInMiddleEarthAccuracyTest {

    @ParameterizedTest
    @CsvSource({
            "0, EXCELLENT, 100",
            "15, EXCELLENT, 100",
            "15.01, VERY_GOOD, 80",
            "30, VERY_GOOD, 80",
            "30.01, GOOD, 60",
            "55, GOOD, 60",
            "55.01, CLOSE, 40",
            "90, CLOSE, 40",
            "90.01, FAR, 0",
            "700, FAR, 0",
            "1220.6, FAR, 0"
    })
    void shouldScoreEveryBandAndItsBoundaries(
            double distance, WhereInMiddleEarthAccuracy expected, int points) {
        WhereInMiddleEarthAccuracy accuracy = WhereInMiddleEarthAccuracy.forDistance(distance);

        assertThat(accuracy).isEqualTo(expected);
        assertThat(accuracy.points()).isEqualTo(points);
    }

    @Test
    void shouldGetWorthLessAsTheDistanceGrows() {
        double previousPoints = Double.MAX_VALUE;
        double previousRadius = 0;

        for (WhereInMiddleEarthAccuracy accuracy : WhereInMiddleEarthAccuracy.values()) {
            assertThat((double) accuracy.points()).isLessThan(previousPoints);
            assertThat(accuracy.maxDistance()).isGreaterThan(previousRadius);

            previousPoints = accuracy.points();
            previousRadius = accuracy.maxDistance();
        }
    }

    @Test
    void shouldKeepTheBestBandWorthTheRoundMaximum() {
        assertThat(WhereInMiddleEarthAccuracy.MAX_POINTS).isEqualTo(100);
        assertThat(WhereInMiddleEarthAccuracy.EXCELLENT_RADIUS).isEqualTo(15.0);
    }

    @Test
    void shouldKeepTheWidestBandUnderTenPercentOfTheMapWidth() {
        assertThat(WhereInMiddleEarthAccuracy.CLOSE.maxDistance())
                .isLessThan(WhereInMiddleEarthLocationBank.MAP_WIDTH * 0.10);
    }
}
