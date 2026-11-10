package com.wunderlore.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.wunderlore.ranking.RankingException.Reason;

@SuppressWarnings("unchecked")
class RankingServiceTest {

    private static final Instant PLAYED_AT = Instant.parse("2026-03-01T10:15:30Z");

    private RankingEntryRepository repository;
    private FinishedGameLookup lookup;
    private ObjectProvider<RankingEntryRepository> provider;
    private RankingService service;

    @BeforeEach
    void setUp() {
        repository = mock(RankingEntryRepository.class);
        lookup = mock(FinishedGameLookup.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.saveAndFlush(any(RankingEntry.class))).thenAnswer(call -> call.getArgument(0));
        service = new RankingService(provider, lookup);
    }

    private static Reason reasonOf(Throwable throwable) {
        return ((RankingException) throwable).reason();
    }

    @Test
    void shouldPublishWithTheScoreTheServerKeptForEachOfTheFourGames() {
        for (GameType type : GameType.values()) {
            when(lookup.find(type, "game-1")).thenReturn(new FinishedGame(70, 100, PLAYED_AT, RankingDivision.of(type).get(0)));

            RankingService.Published published = service.publish(type.slug(), "game-1", "Frodo");

            assertThat(published.created()).isTrue();
            assertThat(published.entry().getGameType()).isEqualTo(type);
            assertThat(published.entry().getScore()).isEqualTo(70);
            assertThat(published.entry().getMaxScore()).isEqualTo(100);
            assertThat(published.entry().getPercentage()).isEqualByComparingTo("70");
            assertThat(published.entry().getPlayedAt()).isEqualTo(PLAYED_AT);
            assertThat(published.entry().getDivision()).isEqualTo(RankingDivision.of(type).get(0));
        }
    }

    @Test
    void shouldStoreTheDivisionTheGameReports() {
        when(lookup.find(GameType.LORE_CHALLENGE, "g"))
                .thenReturn(new FinishedGame(10, 30, PLAYED_AT, RankingDivision.HARD));
        when(lookup.find(GameType.WHO_SAID_IT, "g"))
                .thenReturn(new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));

        assertThat(service.publish("lore-challenge", "g", "Frodo").entry().getDivision())
                .isEqualTo(RankingDivision.HARD);
        assertThat(service.publish("who-said-it", "g", "Frodo").entry().getDivision())
                .isEqualTo(RankingDivision.STANDARD);
    }

    @Test
    void shouldNotSaveAnEntryWhoseDivisionDoesNotExistInTheGame() {
        when(lookup.find(GameType.WHO_SAID_IT, "g"))
                .thenReturn(new FinishedGame(50, 100, PLAYED_AT, RankingDivision.HARD));
        when(lookup.find(GameType.LORE_CHALLENGE, "g"))
                .thenReturn(new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));

        assertThatThrownBy(() -> service.publish("who-said-it", "g", "Frodo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.publish("lore-challenge", "g", "Frodo"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldComputeThePercentageFromScoreAndMaximumWithFullPrecision() {
        when(lookup.find(GameType.LORE_CHALLENGE, "g")).thenReturn(new FinishedGame(10, 30, PLAYED_AT, RankingDivision.EASY));
        when(lookup.find(GameType.WHERE_IN_MIDDLE_EARTH, "g")).thenReturn(new FinishedGame(2, 3, PLAYED_AT, RankingDivision.MEDIUM));

        assertThat(service.publish("lore-challenge", "g", "Frodo").entry().getPercentage())
                .isEqualByComparingTo(new BigDecimal("33.333333"));
        assertThat(service.publish("where-in-middle-earth", "g", "Frodo").entry().getPercentage())
                .isEqualByComparingTo(new BigDecimal("66.666667"));
    }

    @Test
    void shouldNotSortByRoundedPercentages() {
        // Both show as 33.33 with two decimals, yet they differ, and the stored values must too.
        assertThat(RankingEntry.percentageOf(1000, 3000)).isEqualByComparingTo("33.333333");
        assertThat(RankingEntry.percentageOf(3333, 10000)).isEqualByComparingTo("33.330000");
        assertThat(RankingEntry.percentageOf(1000, 3000)).isGreaterThan(RankingEntry.percentageOf(3333, 10000));
    }

    @Test
    void shouldStoreTheNormalizedNickname() {
        when(lookup.find(GameType.WHO_SAID_IT, "g")).thenReturn(new FinishedGame(100, 100, PLAYED_AT, RankingDivision.STANDARD));

        assertThat(service.publish("who-said-it", "g", "  Frodo   Baggins ").entry().getNickname())
                .isEqualTo("Frodo Baggins");
    }

    @Test
    void shouldRejectAnUnknownGameWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.publish("which-character", "g", "Frodo"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.INVALID_GAME));
        assertThatThrownBy(() -> service.publish("WHO_SAID_IT", "g", "Frodo"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.INVALID_GAME));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectABadGameId() {
        for (String id : new String[] {null, "", "  ", "x".repeat(65)}) {
            assertThatThrownBy(() -> service.publish("who-said-it", id, "Frodo"))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.INVALID_GAME_ID));
        }
    }

    @Test
    void shouldRejectInvalidAndReservedNicknamesBeforeLookingAtTheGame() {
        assertThatThrownBy(() -> service.publish("who-said-it", "g", "ab"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.INVALID_NICKNAME));
        assertThatThrownBy(() -> service.publish("who-said-it", "g", "Admin"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.RESERVED_NICKNAME));
        verify(lookup, never()).find(any(), any());
    }

    @Test
    void shouldRefuseAGameThatDoesNotExistOrIsNotFinished() {
        when(lookup.find(GameType.WHO_AM_I, "missing")).thenThrow(new RankingException(Reason.GAME_NOT_FOUND));
        when(lookup.find(GameType.WHO_AM_I, "open")).thenThrow(new RankingException(Reason.GAME_NOT_FINISHED));

        assertThatThrownBy(() -> service.publish("who-am-i", "missing", "Frodo"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.GAME_NOT_FOUND));
        assertThatThrownBy(() -> service.publish("who-am-i", "open", "Frodo"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.GAME_NOT_FINISHED));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReturnTheExistingEntryWhenTheGameIsAlreadyPublished() {
        when(lookup.find(GameType.WHO_SAID_IT, "g")).thenReturn(new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));

        RankingEntry existing = new RankingEntry(
                GameType.WHO_SAID_IT, "g", "First Name", new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));
        when(repository.findByGameTypeAndGameId(GameType.WHO_SAID_IT, "g")).thenReturn(Optional.of(existing));

        RankingService.Published published = service.publish("who-said-it", "g", "Other Name");

        assertThat(published.created()).isFalse();
        assertThat(published.entry().getNickname()).isEqualTo("First Name");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldTreatALostRaceAsAlreadyPublished() {
        when(lookup.find(GameType.WHO_SAID_IT, "g")).thenReturn(new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));

        RankingEntry winner = new RankingEntry(
                GameType.WHO_SAID_IT, "g", "Winner", new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));
        when(repository.findByGameTypeAndGameId(GameType.WHO_SAID_IT, "g"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.saveAndFlush(any(RankingEntry.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        RankingService.Published published = service.publish("who-said-it", "g", "Loser");

        assertThat(published.created()).isFalse();
        assertThat(published.entry().getNickname()).isEqualTo("Winner");
    }

    @Test
    void shouldReportUnavailableWhenAnIntegrityErrorLeavesNoEntryBehind() {
        when(lookup.find(GameType.WHO_SAID_IT, "g")).thenReturn(new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));
        when(repository.findByGameTypeAndGameId(GameType.WHO_SAID_IT, "g")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(RankingEntry.class)))
                .thenThrow(new DataIntegrityViolationException("not a duplicate"));

        assertThatThrownBy(() -> service.publish("who-said-it", "g", "Frodo"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.UNAVAILABLE));
    }

    @Test
    void shouldReportUnavailableWhenTheDatabaseFailsAndNeverClaimPublication() {
        when(lookup.find(GameType.WHO_SAID_IT, "g")).thenReturn(new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));
        when(repository.saveAndFlush(any(RankingEntry.class)))
                .thenThrow(new CannotCreateTransactionException("connection refused"));

        assertThatThrownBy(() -> service.publish("who-said-it", "g", "Frodo"))
                .satisfies(e -> {
                    assertThat(reasonOf(e)).isEqualTo(Reason.UNAVAILABLE);
                    assertThat(e.getMessage()).doesNotContain("connection");
                });

        // The failure is not sticky: the same request goes through once the database is back.
        when(repository.saveAndFlush(any(RankingEntry.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.publish("who-said-it", "g", "Frodo").created()).isTrue();
    }

    @Test
    void shouldReportUnavailableWhenReadFailsWhileCheckingForAnExistingEntry() {
        when(lookup.find(GameType.WHO_SAID_IT, "g")).thenReturn(new FinishedGame(50, 100, PLAYED_AT, RankingDivision.STANDARD));
        when(repository.findByGameTypeAndGameId(eq(GameType.WHO_SAID_IT), eq("g")))
                .thenThrow(new QueryTimeoutException("slow"));

        assertThatThrownBy(() -> service.publish("who-said-it", "g", "Frodo"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.UNAVAILABLE));
    }

    @Test
    void shouldReportUnavailableWhenPersistenceIsNotConfigured() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.publish("who-said-it", "g", "Frodo"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.UNAVAILABLE));
        assertThatThrownBy(() -> service.top("who-am-i", "easy"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.UNAVAILABLE));
        verify(lookup, never()).find(any(), any());
    }

    @Test
    void shouldReadTheTopFiftyOfEveryValidGameAndDivision() {
        for (GameType type : GameType.values()) {
            for (RankingDivision division : RankingDivision.of(type)) {
                service.top(type.slug(), division.slug());

                verify(repository).findTopByGameTypeAndDivision(type.name(), division.name(), 50);
            }
        }

        // 3 games x 3 difficulties + Who Said It? = 10 divisions, and nothing else was queried.
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldReturnWhatTheRepositoryFoundAndAnEmptyListForADivisionWithoutResults() {
        when(repository.findTopByGameTypeAndDivision("LORE_CHALLENGE", "HARD", 50)).thenReturn(List.of());

        assertThat(service.top("lore-challenge", "hard")).isEmpty();
    }

    @Test
    void shouldRefuseAnUnknownGameWithoutTouchingTheDatabase() {
        for (String game : new String[] {"nonsense", "WHO_SAID_IT", "which-character", "", "  ", null}) {
            assertThatThrownBy(() -> service.top(game, "easy"))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.INVALID_GAME));
        }

        verify(repository, never()).findTopByGameTypeAndDivision(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldRefuseAMissingOrUnknownDivisionWithoutTouchingTheDatabase() {
        for (String division : new String[] {"nonsense", "EASY", "expert", "", "  ", null}) {
            assertThatThrownBy(() -> service.top("lore-challenge", division))
                    .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.INVALID_DIVISION));
        }

        verify(repository, never()).findTopByGameTypeAndDivision(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldRefuseEveryDivisionThatDoesNotExistInTheGame() {
        int refused = 0;

        for (GameType type : GameType.values()) {
            for (RankingDivision division : RankingDivision.values()) {
                if (division.isValidFor(type)) {
                    continue;
                }

                assertThatThrownBy(() -> service.top(type.slug(), division.slug()))
                        .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.INVALID_DIVISION));
                refused++;
            }
        }

        assertThat(refused).isEqualTo(6);
        verify(repository, never()).findTopByGameTypeAndDivision(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldReportUnavailableWhenTheRankingReadFails() {
        when(repository.findTopByGameTypeAndDivision("WHO_AM_I", "EASY", 50))
                .thenThrow(new QueryTimeoutException("slow"));

        assertThatThrownBy(() -> service.top("who-am-i", "easy"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(Reason.UNAVAILABLE));
    }
}
