package com.wunderlore.game.whosaidit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import com.wunderlore.lore.LoreCharacter;
import com.wunderlore.lore.LoreData;
import com.wunderlore.lore.LoreDataService;
import com.wunderlore.lore.LoreQuote;

@Service
public class WhoSaidItService {

    private final LoreDataService loreDataService;
    private final RandomGenerator random;

    // Constructor
    public WhoSaidItService(
            LoreDataService loreDataService,
            RandomGenerator random) {
        this.loreDataService = loreDataService;
        this.random = random;
    }

    public WhoSaidItRound generateRound() {
        LoreData loreData = loreDataService.getData();

        List<LoreQuote> quotes = loreData.attributedQuotes();

        if (quotes.isEmpty()) {
            throw new WhoSaidItInsufficientDataException(
                    "No valid quotes available to start the game");
        }

        LoreQuote selectedQuote = quotes.get(random.nextInt(quotes.size()));

        LoreCharacter correctCharacter = loreData.charactersById().get(selectedQuote.characterId());

        List<LoreCharacter> wrongCharacters = new ArrayList<>();

        for (LoreCharacter character : loreData.charactersById().values()) {

            if (character.id().equals(correctCharacter.id())) {
                continue;
            }

            wrongCharacters.add(character);
        }

        if (wrongCharacters.size() < 3) {
            throw new WhoSaidItInsufficientDataException(
                    "At least four distinct characters are required to play");
        }

        shuffle(wrongCharacters);

        List<WhoSaidItRound.CharacterOption> options = new ArrayList<>();

        options.add(new WhoSaidItRound.CharacterOption(
                correctCharacter.id(),
                correctCharacter.name()));

        for (int i = 0; i < 3; i++) {
            LoreCharacter character = wrongCharacters.get(i);

            options.add(new WhoSaidItRound.CharacterOption(
                    character.id(),
                    character.name()));
        }

        shuffle(options);

        return new WhoSaidItRound(
                selectedQuote.dialog(),
                List.copyOf(options),
                correctCharacter.id());
    }

    private <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);

            Collections.swap(items, i, j);
        }
    }
}
