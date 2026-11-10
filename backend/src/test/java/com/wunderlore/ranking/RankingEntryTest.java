package com.wunderlore.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wunderlore.ranking.dto.PublishRequest;

class RankingEntryTest {

    private static final Instant PLAYED_AT = Instant.parse("2026-01-01T00:00:00Z");

    static Stream<Arguments> validCombinations() {
        return Stream.of(GameType.values()).flatMap(game -> RankingDivision.of(game).stream()
                .map(division -> Arguments.of(game, division)));
    }

    static Stream<Arguments> invalidCombinations() {
        return Stream.of(GameType.values()).flatMap(game -> Stream.of(RankingDivision.values())
                .filter(division -> !division.isValidFor(game))
                .map(division -> Arguments.of(game, division)));
    }

    @ParameterizedTest
    @MethodSource("validCombinations")
    void shouldKeepTheDivisionOfAValidCombination(GameType game, RankingDivision division) {
        RankingEntry entry = new RankingEntry(game, "g", "Frodo", new FinishedGame(70, 100, PLAYED_AT, division));

        assertThat(entry.getDivision()).isEqualTo(division);
        assertThat(entry.getGameType()).isEqualTo(game);
    }

    @ParameterizedTest
    @MethodSource("invalidCombinations")
    void shouldNotCreateAnEntryForAnInvalidCombination(GameType game, RankingDivision division) {
        assertThatThrownBy(() -> new RankingEntry(
                game, "g", "Frodo", new FinishedGame(70, 100, PLAYED_AT, division)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotBuildAFinishedGameWithoutADivision() {
        assertThatThrownBy(() -> new FinishedGame(70, 100, PLAYED_AT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldNotLetTheClientSendADivisionWhenPublishing() {
        assertThat(Arrays.stream(PublishRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("gameType", "gameId", "nickname");
    }
}
