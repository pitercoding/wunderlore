package com.wunderlore.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;

@Service
public class WhoSaidItService {

    private static final int PAGE_SIZE = 100;

    private GameData cachedGameData;

    private final TheOneApiClient theOneApiClient;
    private final RandomGenerator random;

    // Constructor
    public WhoSaidItService(
            TheOneApiClient theOneApiClient,
            RandomGenerator random) {
        this.theOneApiClient = theOneApiClient;
        this.random = random;
    }

    // Fetches all citation pages from the API.
    private List<ExternalQuote> loadAllQuotes() {
        List<ExternalQuote> quotes = new ArrayList<>();

        // Fetch the first page of quotes
        ExternalQuoteResponse response = theOneApiClient.getQuotes(1, PAGE_SIZE);

        // Add the results
        quotes.addAll(response.docs());

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= response.pages(); page++) {
            ExternalQuoteResponse nextPage = theOneApiClient.getQuotes(page, PAGE_SIZE);

            quotes.addAll(nextPage.docs());
        }

        return quotes;
    }

    // Fetches all character pages.
    private List<ExternalCharacter> loadAllCharacters() {
        List<ExternalCharacter> characters = new ArrayList<>();

        // Fetch the first page of characters
        ExternalCharacterResponse response = theOneApiClient.getCharacters(1, PAGE_SIZE);

        // Add the results
        characters.addAll(response.docs());

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= response.pages(); page++) {
            ExternalCharacterResponse nextPage = theOneApiClient.getCharacters(page, PAGE_SIZE);

            characters.addAll(nextPage.docs());
        }

        return characters;
    }

    // Organize the characters into a Map, using the ID as the key.
    private Map<String, ExternalCharacter> indexCharactersById(
            List<ExternalCharacter> characters) {

        Map<String, ExternalCharacter> charactersById = new HashMap<>();

        for (ExternalCharacter character : characters) {
            if (character.id() == null || character.id().isBlank()) {
                continue;
            }

            if (character.name() == null || character.name().isBlank()) {
                continue;
            }

            charactersById.put(character.id(), character);
        }

        return charactersById;
    }

    // Filters valid quotes
    private List<ExternalQuote> filterValidQuotes(
            List<ExternalQuote> quotes,
            Map<String, ExternalCharacter> charactersById) {

        List<ExternalQuote> validQuotes = new ArrayList<>();

        for (ExternalQuote quote : quotes) {
            if (quote.dialog() == null || quote.dialog().isBlank()) {
                continue;
            }

            if (quote.character() == null || quote.character().isBlank()) {
                continue;
            }

            if (!charactersById.containsKey(quote.character())) {
                continue;
            }

            validQuotes.add(quote);
        }

        return validQuotes;
    }

    private synchronized GameData loadGameData() {
        if (cachedGameData != null) {
            return cachedGameData;
        }

        List<ExternalQuote> quotes = loadAllQuotes();
        List<ExternalCharacter> characters = loadAllCharacters();

        Map<String, ExternalCharacter> charactersById = indexCharactersById(characters);

        List<ExternalQuote> validQuotes = filterValidQuotes(quotes, charactersById);

        if (validQuotes.isEmpty()) {
            throw new IllegalStateException(
                    "No valid quotes available to start the game");
        }

        cachedGameData = new GameData(
                List.copyOf(validQuotes),
                Map.copyOf(charactersById));

        return cachedGameData;
    }

    public GameRound generateRound() {
        GameData gameData = loadGameData();

        List<ExternalQuote> quotes = gameData.quotes();

        ExternalQuote selectedQuote = quotes.get(random.nextInt(quotes.size()));

        ExternalCharacter correctCharacter = gameData.charactersById().get(selectedQuote.character());

        List<ExternalCharacter> wrongCharacters = new ArrayList<>();

        for (ExternalCharacter character : gameData.charactersById().values()) {

            if (character.id().equals(correctCharacter.id())) {
                continue;
            }

            wrongCharacters.add(character);
        }

        if (wrongCharacters.size() < 3) {
            throw new IllegalStateException(
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
