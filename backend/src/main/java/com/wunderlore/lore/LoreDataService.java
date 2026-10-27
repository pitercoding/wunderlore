package com.wunderlore.lore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;

@Service
public class LoreDataService {

    private static final int PAGE_SIZE = 100;

    private LoreData cachedLoreData;

    private final TheOneApiClient theOneApiClient;

    // Constructor
    public LoreDataService(TheOneApiClient theOneApiClient) {
        this.theOneApiClient = theOneApiClient;
    }

    // Loads the dataset on first use and caches it only once every page has loaded.
    public synchronized LoreData getData() {
        if (cachedLoreData != null) {
            return cachedLoreData;
        }

        List<ExternalQuote> quotes = loadAllQuotes();
        List<ExternalCharacter> characters = loadAllCharacters();

        Map<String, ExternalCharacter> charactersById = indexCharactersById(characters);

        List<ExternalQuote> attributedQuotes = filterValidQuotes(quotes, charactersById);

        cachedLoreData = new LoreData(
                List.copyOf(quotes),
                Map.copyOf(charactersById),
                List.copyOf(attributedQuotes));

        return cachedLoreData;
    }

    // Fetches all citation pages from the API.
    private List<ExternalQuote> loadAllQuotes() {
        List<ExternalQuote> quotes = new ArrayList<>();

        // Fetch the first page of quotes
        ExternalQuoteResponse response = theOneApiClient.getQuotes(1, PAGE_SIZE);

        // Add the results
        quotes.addAll(quoteDocs(response));

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= response.pages(); page++) {
            ExternalQuoteResponse nextPage = theOneApiClient.getQuotes(page, PAGE_SIZE);

            quotes.addAll(quoteDocs(nextPage));
        }

        return quotes;
    }

    // Fetches all character pages.
    private List<ExternalCharacter> loadAllCharacters() {
        List<ExternalCharacter> characters = new ArrayList<>();

        // Fetch the first page of characters
        ExternalCharacterResponse response = theOneApiClient.getCharacters(1, PAGE_SIZE);

        // Add the results
        characters.addAll(characterDocs(response));

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= response.pages(); page++) {
            ExternalCharacterResponse nextPage = theOneApiClient.getCharacters(page, PAGE_SIZE);

            characters.addAll(characterDocs(nextPage));
        }

        return characters;
    }

    private List<ExternalQuote> quoteDocs(ExternalQuoteResponse response) {
        if (response == null || response.docs() == null) {
            throw new LoreDataUnavailableException(
                    "The One API returned an empty quote page");
        }

        return response.docs();
    }

    private List<ExternalCharacter> characterDocs(ExternalCharacterResponse response) {
        if (response == null || response.docs() == null) {
            throw new LoreDataUnavailableException(
                    "The One API returned an empty character page");
        }

        return response.docs();
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
}
