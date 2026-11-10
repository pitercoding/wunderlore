package com.wunderlore.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.wunderlore.game.lorechallenge.LoreChallengeDifficulty;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch;
import com.wunderlore.game.lorechallenge.LoreChallengeMatchRegistry;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthDifficulty;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGameRegistry;
import com.wunderlore.game.whoami.WhoAmIDifficulty;
import com.wunderlore.game.whoami.WhoAmIGame;
import com.wunderlore.game.whoami.WhoAmIGameRegistry;
import com.wunderlore.game.whosaidit.WhoSaidItMatch;
import com.wunderlore.game.whosaidit.WhoSaidItMatchRegistry;

// The division the lookup reports for each game, read from the finished game itself.
class RegistryFinishedGameLookupTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private LoreChallengeMatchRegistry loreChallenge;
    private WhoAmIGameRegistry whoAmI;
    private WhereInMiddleEarthGameRegistry whereInMiddleEarth;
    private WhoSaidItMatchRegistry whoSaidIt;
    private RegistryFinishedGameLookup lookup;

    @BeforeEach
    void setUp() {
        loreChallenge = mock(LoreChallengeMatchRegistry.class);
        whoAmI = mock(WhoAmIGameRegistry.class);
        whereInMiddleEarth = mock(WhereInMiddleEarthGameRegistry.class);
        whoSaidIt = mock(WhoSaidItMatchRegistry.class);
        lookup = new RegistryFinishedGameLookup(loreChallenge, whoAmI, whereInMiddleEarth, whoSaidIt);
    }

    // Spelled out per constant, independent of the mapping in the lookup.
    private static RankingDivision expected(String difficultyName) {
        return switch (difficultyName) {
            case "EASY" -> RankingDivision.EASY;
            case "MEDIUM" -> RankingDivision.MEDIUM;
            case "HARD" -> RankingDivision.HARD;
            default -> throw new AssertionError(difficultyName);
        };
    }

    @ParameterizedTest
    @EnumSource(LoreChallengeDifficulty.class)
    void shouldReportTheDivisionOfALoreChallengeMatch(LoreChallengeDifficulty difficulty) {
        when(loreChallenge.result("m")).thenReturn(new LoreChallengeMatch.Result(
                "ALL", difficulty, 10, 7, 3, 70, 100, 70, 60, "rank"));
        when(loreChallenge.createdAt("m")).thenReturn(CREATED_AT);

        FinishedGame game = lookup.find(GameType.LORE_CHALLENGE, "m");

        assertThat(game.division()).isEqualTo(expected(difficulty.name()));
        assertThat(game.division().isValidFor(GameType.LORE_CHALLENGE)).isTrue();
        assertThat(game.score()).isEqualTo(70);
        assertThat(game.maxScore()).isEqualTo(100);
        assertThat(game.playedAt()).isEqualTo(CREATED_AT);
    }

    @ParameterizedTest
    @EnumSource(WhoAmIDifficulty.class)
    void shouldReportTheDivisionOfAWhoAmIGame(WhoAmIDifficulty difficulty) {
        when(whoAmI.result("g")).thenReturn(new WhoAmIGame.Result(
                difficulty, 10, 800, 1000, 80, 8, 2, 2, List.of()));
        when(whoAmI.createdAt("g")).thenReturn(CREATED_AT);

        FinishedGame game = lookup.find(GameType.WHO_AM_I, "g");

        assertThat(game.division()).isEqualTo(expected(difficulty.name()));
        assertThat(game.division().isValidFor(GameType.WHO_AM_I)).isTrue();
        assertThat(game.score()).isEqualTo(800);
        assertThat(game.maxScore()).isEqualTo(1000);
    }

    @ParameterizedTest
    @EnumSource(WhereInMiddleEarthDifficulty.class)
    void shouldReportTheDivisionOfAWhereInMiddleEarthGame(WhereInMiddleEarthDifficulty difficulty) {
        when(whereInMiddleEarth.result("g")).thenReturn(new WhereInMiddleEarthGame.Result(
                difficulty, 10, 400, 1000, 40, 4, 55.5, List.of()));
        when(whereInMiddleEarth.createdAt("g")).thenReturn(CREATED_AT);

        FinishedGame game = lookup.find(GameType.WHERE_IN_MIDDLE_EARTH, "g");

        assertThat(game.division()).isEqualTo(expected(difficulty.name()));
        assertThat(game.division().isValidFor(GameType.WHERE_IN_MIDDLE_EARTH)).isTrue();
        assertThat(game.score()).isEqualTo(400);
        assertThat(game.maxScore()).isEqualTo(1000);
    }

    @Test
    void shouldReportTheCompetitiveWhoSaidItMatchAsStandard() {
        when(whoSaidIt.result("m")).thenReturn(new WhoSaidItMatch.Result(10, 6, 4, 60, 100, 60, List.of()));
        when(whoSaidIt.createdAt("m")).thenReturn(CREATED_AT);

        FinishedGame game = lookup.find(GameType.WHO_SAID_IT, "m");

        assertThat(game.division()).isEqualTo(RankingDivision.STANDARD);
        assertThat(game.division().isValidFor(GameType.WHO_SAID_IT)).isTrue();
        assertThat(game.score()).isEqualTo(60);
        assertThat(game.maxScore()).isEqualTo(100);
    }
}
