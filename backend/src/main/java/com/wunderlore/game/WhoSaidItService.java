package com.wunderlore.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.lore.LoreData;
import com.wunderlore.lore.LoreDataService;

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

    public GameRound generateRound() {
        LoreData loreData = loreDataService.getData();

        List<ExternalQuote> quotes = loreData.attributedQuotes();

        if (quotes.isEmpty()) {
            throw new InsufficientGameDataException(
                    "No valid quotes available to start the game");
        }

        ExternalQuote selectedQuote = quotes.get(random.nextInt(quotes.size()));

        ExternalCharacter correctCharacter = loreData.charactersById().get(selectedQuote.character());

        List<ExternalCharacter> wrongCharacters = new ArrayList<>();

        for (ExternalCharacter character : loreData.charactersById().values()) {

            if (character.id().equals(correctCharacter.id())) {
                continue;
            }

            wrongCharacters.add(character);
        }

        if (wrongCharacters.size() < 3) {
            throw new InsufficientGameDataException(
                    "At least four distinct characters are required to play");
        }

        shuffle(wrongCharacters);

        List<GameRound.CharacterOption> options = new ArrayList<>();

        options.add(new GameRound.CharacterOption(
                correctCharacter.id(),
                correctCharacter.name()));

        for (int i = 0; i < 3; i++) {
            ExternalCharacter character = wrongCharacters.get(i);

            options.add(new GameRound.CharacterOption(
                    character.id(),
                    character.name()));
        }

        shuffle(options);

        return new GameRound(
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
