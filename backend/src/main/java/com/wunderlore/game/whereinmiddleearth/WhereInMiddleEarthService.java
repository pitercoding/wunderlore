package com.wunderlore.game.whereinmiddleearth;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthException.Reason;

// Builds a game from the curated places. Pure selection logic: it keeps no state of its own.
@Service
public class WhereInMiddleEarthService {

    private final WhereInMiddleEarthLocationBank bank;
    private final RandomGenerator random;
    private final Clock clock;

    public WhereInMiddleEarthService(
            WhereInMiddleEarthLocationBank bank, RandomGenerator random, Clock clock) {
        this.bank = bank;
        this.random = random;
        this.clock = clock;
    }

    // Ten different places of the difficulty, in random order, so none repeats in a game. Throws
    // NOT_ENOUGH_LOCATIONS rather than building a shorter game.
    public WhereInMiddleEarthGame createGame(WhereInMiddleEarthDifficulty difficulty) {
        List<WhereInMiddleEarthLocation> pool = new ArrayList<>(bank.find(difficulty));

        if (pool.size() < WhereInMiddleEarthGame.TOTAL_ROUNDS) {
            throw new WhereInMiddleEarthException(Reason.NOT_ENOUGH_LOCATIONS);
        }

        shuffle(pool);

        return new WhereInMiddleEarthGame(
                difficulty,
                List.copyOf(pool.subList(0, WhereInMiddleEarthGame.TOTAL_ROUNDS)),
                bank::regionName,
                clock.instant());
    }

    private <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            Collections.swap(items, i, random.nextInt(i + 1));
        }
    }
}
