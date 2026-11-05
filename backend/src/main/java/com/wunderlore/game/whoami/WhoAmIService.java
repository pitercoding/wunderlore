package com.wunderlore.game.whoami;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import com.wunderlore.game.whoami.WhoAmIException.Reason;
import com.wunderlore.game.whoami.WhoAmIGame.Option;
import com.wunderlore.game.whoami.WhoAmIGame.Options;

// Builds a game from the curated characters. Pure selection logic: it keeps no state of its own.
@Service
public class WhoAmIService {

    private static final String[] OPTION_IDS = {"a", "b", "c", "d"};

    private static final int WRONG_OPTIONS = OPTION_IDS.length - 1;

    private final WhoAmICharacterBank bank;
    private final RandomGenerator random;
    private final Clock clock;

    public WhoAmIService(WhoAmICharacterBank bank, RandomGenerator random, Clock clock) {
        this.bank = bank;
        this.random = random;
        this.clock = clock;
    }

    // Ten different characters of the difficulty, in random order, so none repeats in a game.
    // Throws NOT_ENOUGH_CHARACTERS rather than building a shorter game.
    public WhoAmIGame createGame(WhoAmIDifficulty difficulty) {
        List<WhoAmICharacter> pool = new ArrayList<>(bank.find(difficulty));

        if (pool.size() < WhoAmIGame.TOTAL_ROUNDS) {
            throw new WhoAmIException(Reason.NOT_ENOUGH_CHARACTERS);
        }

        shuffle(pool);

        return new WhoAmIGame(
                difficulty,
                List.copyOf(pool.subList(0, WhoAmIGame.TOTAL_ROUNDS)),
                this::drawOptions,
                clock.instant());
    }

    // The correct character and three wrong ones from the same difficulty, in random positions.
    // Wrong answers come from bank.wrongAnswerCandidates (never an alias of the right one, never
    // one declared incompatible), skipping the ones the player already ruled out. One of them is
    // preferably of the same kind as the right answer, so the options are plausible. The wrong
    // ones are drawn again for every clue, which is what makes later clues worth using; the
    // right one is of course always there.
    Options drawOptions(WhoAmICharacter correct, Set<String> excludedCharacterIds) {
        List<WhoAmICharacter> candidates = new ArrayList<>();

        for (WhoAmICharacter candidate : bank.wrongAnswerCandidates(correct)) {
            if (!excludedCharacterIds.contains(candidate.id())) {
                candidates.add(candidate);
            }
        }

        if (candidates.size() < WRONG_OPTIONS) {
            throw new IllegalStateException("Not enough wrong answers for " + correct.id());
        }

        shuffle(candidates);

        List<WhoAmICharacter> chosen = new ArrayList<>();

        candidates.stream()
                .filter(candidate -> candidate.group().equals(correct.group()))
                .findFirst()
                .ifPresent(chosen::add);

        for (WhoAmICharacter candidate : candidates) {
            if (chosen.size() == WRONG_OPTIONS) {
                break;
            }

            if (!chosen.contains(candidate)) {
                chosen.add(candidate);
            }
        }

        List<WhoAmICharacter> shown = new ArrayList<>(chosen);

        shown.add(correct);
        shuffle(shown);

        List<Option> options = new ArrayList<>();
        Map<String, String> characterIdByOption = new HashMap<>();
        String correctOptionId = null;

        for (int i = 0; i < shown.size(); i++) {
            WhoAmICharacter character = shown.get(i);

            options.add(new Option(OPTION_IDS[i], character.name()));
            characterIdByOption.put(OPTION_IDS[i], character.id());

            if (character.id().equals(correct.id())) {
                correctOptionId = OPTION_IDS[i];
            }
        }

        return new Options(options, correctOptionId, characterIdByOption);
    }

    private <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            Collections.swap(items, i, random.nextInt(i + 1));
        }
    }
}
