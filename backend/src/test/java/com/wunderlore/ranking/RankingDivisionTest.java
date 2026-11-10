package com.wunderlore.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class RankingDivisionTest {

    static Stream<Arguments> validCombinations() {
        Stream<Arguments> difficultyGames = Stream
                .of(GameType.LORE_CHALLENGE, GameType.WHO_AM_I, GameType.WHERE_IN_MIDDLE_EARTH)
                .flatMap(game -> Stream.of(RankingDivision.EASY, RankingDivision.MEDIUM, RankingDivision.HARD)
                        .map(division -> Arguments.of(game, division)));

        return Stream.concat(
                difficultyGames,
                Stream.of(Arguments.of(GameType.WHO_SAID_IT, RankingDivision.STANDARD)));
    }

    static Stream<Arguments> invalidCombinations() {
        return Stream.of(GameType.values()).flatMap(game -> Stream.of(RankingDivision.values())
                .filter(division -> !division.isValidFor(game))
                .map(division -> Arguments.of(game, division)));
    }

    @ParameterizedTest
    @MethodSource("validCombinations")
    void shouldAcceptEveryValidCombination(GameType game, RankingDivision division) {
        assertThat(division.isValidFor(game)).isTrue();
        assertThat(RankingDivision.of(game)).contains(division);
    }

    @Test
    void shouldRejectEveryOtherCombination() {
        // 4 games x 4 divisions = 16, of which 3 x 3 + 1 = 10 are valid.
        assertThat(invalidCombinations().count()).isEqualTo(6);
    }

    @ParameterizedTest
    @MethodSource("invalidCombinations")
    void shouldReportInvalidCombinationsAsInvalid(GameType game, RankingDivision division) {
        assertThat(division.isValidFor(game)).isFalse();
        assertThat(RankingDivision.of(game)).doesNotContain(division);
    }

    @Test
    void shouldGiveWhoSaidItOnlyTheStandardDivision() {
        assertThat(RankingDivision.of(GameType.WHO_SAID_IT)).containsExactly(RankingDivision.STANDARD);
    }

    @Test
    void shouldGiveTheOtherGamesTheThreeDifficultiesInDisplayOrder() {
        for (GameType game : EnumSet.complementOf(EnumSet.of(GameType.WHO_SAID_IT))) {
            assertThat(RankingDivision.of(game))
                    .containsExactly(RankingDivision.EASY, RankingDivision.MEDIUM, RankingDivision.HARD);
        }
    }

    @Test
    void shouldGiveEveryGameAtLeastOneDivisionAndEveryDivisionAGame() {
        for (GameType game : GameType.values()) {
            assertThat(RankingDivision.of(game)).isNotEmpty();
        }

        for (RankingDivision division : RankingDivision.values()) {
            assertThat(EnumSet.allOf(GameType.class)).anyMatch(division::isValidFor);
        }
    }

    @ParameterizedTest
    @EnumSource(RankingDivision.class)
    void shouldRoundTripTheSlug(RankingDivision division) {
        assertThat(division.slug()).isEqualTo(division.name().toLowerCase());
        assertThat(RankingDivision.fromSlug(division.slug())).contains(division);
    }

    @Test
    void shouldNotParseUnknownOrDifferentlyCasedSlugs() {
        assertThat(RankingDivision.fromSlug("EASY")).isEmpty();
        assertThat(RankingDivision.fromSlug("expert")).isEmpty();
        assertThat(RankingDivision.fromSlug("")).isEmpty();
        assertThat(RankingDivision.fromSlug(null)).isEmpty();
    }
}
