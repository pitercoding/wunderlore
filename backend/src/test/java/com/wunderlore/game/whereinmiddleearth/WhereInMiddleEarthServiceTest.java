package com.wunderlore.game.whereinmiddleearth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthException.Reason;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame.AnswerOutcome;

class WhereInMiddleEarthServiceTest {

    private final WhereInMiddleEarthLocationBank bank = new WhereInMiddleEarthLocationBank();

    private WhereInMiddleEarthService service(long seed) {
        return new WhereInMiddleEarthService(bank, new Random(seed), Clock.systemUTC());
    }

    // Plays every round at the real position of the place, found by name in the bank.
    private List<String> askedNames(WhereInMiddleEarthGame game, WhereInMiddleEarthDifficulty difficulty) {
        List<String> names = new java.util.ArrayList<>();

        for (int round = 1; round <= WhereInMiddleEarthGame.TOTAL_ROUNDS; round++) {
            String name = game.currentRound().locationName();
            WhereInMiddleEarthLocation place = bank.find(difficulty).stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst().orElseThrow();
            AnswerOutcome outcome = game.answer(round, place.x(), place.y());

            assertThat(outcome.accuracy()).isEqualTo(WhereInMiddleEarthAccuracy.EXCELLENT);
            names.add(name);
        }

        return names;
    }

    @Test
    void shouldAskTenDifferentPlacesOfTheChosenDifficulty() {
        for (WhereInMiddleEarthDifficulty difficulty : WhereInMiddleEarthDifficulty.values()) {
            for (long seed = 0; seed < 25; seed++) {
                List<String> names = askedNames(service(seed).createGame(difficulty), difficulty);
                Set<String> allowed = new HashSet<>();

                bank.find(difficulty).forEach(place -> allowed.add(place.name()));

                assertThat(names).hasSize(10).doesNotHaveDuplicates();
                assertThat(allowed).containsAll(names);
            }
        }
    }

    @Test
    void shouldVaryTheSelectionAndTheOrderBetweenGames() {
        Set<List<String>> sequences = new HashSet<>();
        Set<String> seenPlaces = new HashSet<>();

        for (long seed = 0; seed < 40; seed++) {
            List<String> names = askedNames(
                    service(seed).createGame(WhereInMiddleEarthDifficulty.EASY),
                    WhereInMiddleEarthDifficulty.EASY);

            sequences.add(names);
            seenPlaces.addAll(names);
        }

        assertThat(sequences.size()).isGreaterThan(30);
        // Ten out of fifteen per game: over forty games every place is asked.
        assertThat(seenPlaces).hasSize(15);
    }

    @Test
    void shouldBeRepeatableForTheSameSeed() {
        List<String> first = askedNames(service(7).createGame(WhereInMiddleEarthDifficulty.HARD),
                WhereInMiddleEarthDifficulty.HARD);
        List<String> second = askedNames(service(7).createGame(WhereInMiddleEarthDifficulty.HARD),
                WhereInMiddleEarthDifficulty.HARD);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldStartTheGameAtRoundOneWithTheCreationTime() {
        WhereInMiddleEarthGame game = service(1).createGame(WhereInMiddleEarthDifficulty.MEDIUM);

        assertThat(game.currentRound().roundNumber()).isEqualTo(1);
        assertThat(game.currentRound().difficulty()).isEqualTo(WhereInMiddleEarthDifficulty.MEDIUM);
        assertThat(game.createdAt()).isNotNull();
    }

    @Test
    void shouldRefuseToBuildAShorterGame() {
        WhereInMiddleEarthLocationBank small = new WhereInMiddleEarthLocationBank() {
            @Override
            public List<WhereInMiddleEarthLocation> find(WhereInMiddleEarthDifficulty difficulty) {
                return super.find(difficulty).subList(0, 9);
            }
        };

        assertThatThrownBy(() -> new WhereInMiddleEarthService(small, new Random(1), Clock.systemUTC())
                .createGame(WhereInMiddleEarthDifficulty.EASY))
                .isInstanceOfSatisfying(WhereInMiddleEarthException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.NOT_ENOUGH_LOCATIONS));
    }
}
