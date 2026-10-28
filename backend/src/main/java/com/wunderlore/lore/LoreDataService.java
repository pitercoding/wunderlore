package com.wunderlore.lore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;

@Service
public class LoreDataService {

    private static final int PAGE_SIZE = 100;

    private LoreData cachedLoreData;

    // Set after a failed load and cleared by a successful one; guarded by this.
    private Instant lastFailureAt;
    private RuntimeException lastFailure;

    private final TheOneApiClient theOneApiClient;
    private final Clock clock;
    private final Duration failureCooldown;
    private final int maxPages;

    // failureCooldown: how long calls fail fast after a failed load before a new
    // load may start (default 30s; PT0S disables it).
    // maxPages: upper bound on pages fetched per dataset (default 100, i.e. up to
    // 10,000 records each at PAGE_SIZE); a larger reported count fails the load.
    public LoreDataService(
            TheOneApiClient theOneApiClient,
            Clock clock,
            @Value("${wunderlore.lore.failure-cooldown:PT30S}") Duration failureCooldown,
            @Value("${wunderlore.lore.max-pages:100}") int maxPages) {

        if (failureCooldown.isNegative()) {
            throw new IllegalArgumentException("Failure cooldown must not be negative");
        }

        if (maxPages < 1) {
            throw new IllegalArgumentException("Maximum pages must be at least 1");
        }

        this.theOneApiClient = theOneApiClient;
        this.clock = clock;
        this.failureCooldown = failureCooldown;
        this.maxPages = maxPages;
    }

    // Loads the dataset on first use and caches it only once every page has loaded.
    // After an upstream failure, callers fail fast until the cooldown expires, so
    // threads queued on the lock do not start back-to-back full loads.
    public synchronized LoreData getData() {
        if (cachedLoreData != null) {
            return cachedLoreData;
        }

        if (lastFailureAt != null) {
            Instant retryAt = lastFailureAt.plus(failureCooldown);

            if (clock.instant().isBefore(retryAt)) {
                throw new LoreDataUnavailableException(
                        "The One API data load failed recently; next attempt allowed at " + retryAt,
                        lastFailure);
            }
        }

        try {
            cachedLoreData = loadData();
        } catch (RestClientException | LoreDataUnavailableException exception) {
            // Only upstream failures start a cooldown; configuration and programming
            // errors propagate without one.
            lastFailureAt = clock.instant();
            lastFailure = exception;
            throw exception;
        }

        lastFailureAt = null;
        lastFailure = null;

        return cachedLoreData;
    }

    private LoreData loadData() {
        List<ExternalQuote> quotes = loadAllQuotes();
        List<ExternalCharacter> characters = loadAllCharacters();

        Map<String, ExternalCharacter> charactersById = indexCharactersById(characters);

        List<ExternalQuote> attributedQuotes = filterValidQuotes(quotes, charactersById);

        return new LoreData(
                List.copyOf(quotes),
                Map.copyOf(charactersById),
                List.copyOf(attributedQuotes));
    }

    // Fetches all citation pages from the API.
    private List<ExternalQuote> loadAllQuotes() {
        List<ExternalQuote> quotes = new ArrayList<>();

        // Fetch the first page of quotes
        ExternalQuoteResponse response = theOneApiClient.getQuotes(1, PAGE_SIZE);

        // Add the results
        quotes.addAll(quoteDocs(response));

        int pages = validatedPageCount(response.pages(), quotes.isEmpty(), "quote");

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= pages; page++) {
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

        int pages = validatedPageCount(response.pages(), characters.isEmpty(), "character");

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= pages; page++) {
            ExternalCharacterResponse nextPage = theOneApiClient.getCharacters(page, PAGE_SIZE);

            characters.addAll(characterDocs(nextPage));
        }

        return characters;
    }

    // Rejects page counts that would truncate the dataset or exceed maxPages.
    // A count of 0 is accepted only for an empty first page, since a missing
    // "pages" field also deserializes to 0.
    private int validatedPageCount(int pages, boolean firstPageEmpty, String dataset) {
        if (pages < 0 || (pages == 0 && !firstPageEmpty)) {
            throw new LoreDataUnavailableException(
                    "The One API returned invalid " + dataset + " page count: " + pages);
        }

        if (pages > maxPages) {
            throw new LoreDataUnavailableException(
                    "The One API reported " + pages + " " + dataset
                            + " pages, above the limit of " + maxPages);
        }

        return pages;
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
