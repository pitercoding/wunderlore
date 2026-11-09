package com.wunderlore.game.whosaidit;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import com.wunderlore.lore.LoreCharacter;
import com.wunderlore.lore.LoreData;
import com.wunderlore.lore.LoreDataService;
import com.wunderlore.lore.LoreQuote;

// Builds a competitive match from the same lore data the single-round game uses. It keeps no state.
@Service
public class WhoSaidItMatchService {

    private static final int WRONG_OPTIONS = WhoSaidItMatch.OPTIONS_PER_QUESTION - 1;

    // Upstream records that do not identify a character: both MINOR_CHARACTER entries (the generic
    // one owns 117 quotes) and Seers. They are never a correct answer and never an option. Matched by
    // exact id only; the lore data and the other game modes are left as they are.
    private static final Set<String> EXCLUDED_CHARACTER_IDS = Set.of(
            "5cdbe49b7ed9587226e794a0",
            "5cdbe47d7ed9587226e7949f",
            "5cdbdecb6dc0baeae48cfa43");

    private final LoreDataService loreDataService;
    private final RandomGenerator random;
    private final Clock clock;

    public WhoSaidItMatchService(LoreDataService loreDataService, RandomGenerator random, Clock clock) {
        this.loreDataService = loreDataService;
        this.random = random;
        this.clock = clock;
    }

    // Ten quotes with different dialogs (and different quote ids), each by a known character, in
    // random order. Quotes are never edited. Throws WhoSaidItInsufficientDataException rather
    // than building a shorter match or inventing data.
    public WhoSaidItMatch createMatch() {
        LoreData data = loreDataService.getData();

        List<LoreQuote> pool = new ArrayList<>(data.attributedQuotes());

        shuffle(pool);

        List<LoreCharacter> characters = data.charactersById().values().stream()
                .filter(character -> !EXCLUDED_CHARACTER_IDS.contains(character.id()))
                .toList();
        Set<String> dialogs = new HashSet<>();
        Set<String> quoteIds = new HashSet<>();
        List<WhoSaidItMatch.Question> questions = new ArrayList<>();

        for (LoreQuote quote : pool) {
            if (questions.size() == WhoSaidItMatch.TOTAL_QUESTIONS) {
                break;
            }

            LoreCharacter correct = data.charactersById().get(quote.characterId());

            if (correct == null || EXCLUDED_CHARACTER_IDS.contains(correct.id())
                    || quote.dialog() == null || quote.dialog().isBlank()) {
                continue;
            }

            String dialogKey = quote.dialog().strip().toLowerCase(Locale.ROOT);

            if (!dialogs.add(dialogKey) || (quote.id() != null && !quoteIds.add(quote.id()))) {
                continue;
            }

            questions.add(new WhoSaidItMatch.Question(
                    quote.dialog(), optionsFor(correct, characters), correct.id()));
        }

        if (questions.size() < WhoSaidItMatch.TOTAL_QUESTIONS) {
            throw new WhoSaidItInsufficientDataException(
                    "Not enough distinct quotes to start a match");
        }

        return new WhoSaidItMatch(questions, clock.instant());
    }

    // The correct character and three others, with different ids and different names, in random
    // positions.
    private List<WhoSaidItRound.CharacterOption> optionsFor(
            LoreCharacter correct, List<LoreCharacter> characters) {
        List<LoreCharacter> candidates = new ArrayList<>(characters);

        shuffle(candidates);

        List<WhoSaidItRound.CharacterOption> options = new ArrayList<>();
        Set<String> names = new HashSet<>();

        options.add(new WhoSaidItRound.CharacterOption(correct.id(), correct.name()));
        names.add(nameKey(correct));

        for (LoreCharacter candidate : candidates) {
            if (options.size() == WhoSaidItMatch.OPTIONS_PER_QUESTION) {
                break;
            }

            if (!candidate.id().equals(correct.id()) && names.add(nameKey(candidate))) {
                options.add(new WhoSaidItRound.CharacterOption(candidate.id(), candidate.name()));
            }
        }

        if (options.size() < WRONG_OPTIONS + 1) {
            throw new WhoSaidItInsufficientDataException(
                    "At least four distinct characters are required to play");
        }

        shuffle(options);

        return options;
    }

    private static String nameKey(LoreCharacter character) {
        return character.name().strip().toLowerCase(Locale.ROOT);
    }

    private <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            Collections.swap(items, i, random.nextInt(i + 1));
        }
    }
}
