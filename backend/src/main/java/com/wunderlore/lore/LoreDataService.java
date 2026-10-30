package com.wunderlore.lore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;

@Service
public class LoreDataService {

    private static final Logger log = LoggerFactory.getLogger(LoreDataService.class);

    private static final int PAGE_SIZE = 100;

    // Published only after every page has loaded; volatile so cache hits skip the lock.
    private volatile LoreData cachedLoreData;

    // Guarded by this. The monitor protects only these fields and is never held
    // while The One API is being called.
    // inFlight: the load every concurrent caller waits on; null when none is running.
    // lastFailureAt / lastFailure: set by a failed load, cleared by a successful one.
    private CompletableFuture<LoreData> inFlight;
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
    // Concurrent callers share a single load: the first one runs it on its own thread
    // and the others wait for its outcome. After an upstream failure, callers fail
    // fast until the cooldown expires.
    public LoreData getData() {
        LoreData cached = cachedLoreData;

        if (cached != null) {
            return cached;
        }

        CompletableFuture<LoreData> operation;
        boolean runsLoad = false;

        synchronized (this) {
            if (cachedLoreData != null) {
                return cachedLoreData;
            }

            operation = inFlight;

            if (operation == null) {
                rejectDuringCooldown();

                operation = new CompletableFuture<>();
                inFlight = operation;
                runsLoad = true;
            }
        }

        if (runsLoad) {
            return runLoad(operation);
        }

        return awaitLoad(operation);
    }

    // Must be called while holding the monitor.
    private void rejectDuringCooldown() {
        if (lastFailureAt == null) {
            return;
        }

        Instant retryAt = lastFailureAt.plus(failureCooldown);

        if (clock.instant().isBefore(retryAt)) {
            // DEBUG only: every request during the cooldown lands here.
            log.debug("Lore data request rejected during cooldown; next attempt allowed at {}", retryAt);

            throw new LoreDataUnavailableException(
                    "The One API data load failed recently; next attempt allowed at " + retryAt,
                    lastFailure,
                    retryAt);
        }
    }

    // Runs the load for the caller that started the operation, outside the monitor.
    // Whatever happens, the operation is completed and inFlight is cleared, so the
    // callers waiting on it are released and a later call can start a new load.
    private LoreData runLoad(CompletableFuture<LoreData> operation) {
        LoadProgress progress = new LoadProgress();

        try {
            LoreData loaded = loadData(progress);
            boolean recovered;

            synchronized (this) {
                recovered = lastFailureAt != null;

                cachedLoreData = loaded;
                lastFailureAt = null;
                lastFailure = null;
                inFlight = null;
            }

            if (recovered) {
                log.info("Lore data loaded after a previous failure: quotes={}, characters={}",
                        loaded.quotes().size(), loaded.charactersById().size());
            }

            operation.complete(loaded);

            return loaded;
        } catch (RestClientException | LoreDataUnavailableException exception) {
            // Only upstream failures start a cooldown; configuration and programming
            // errors propagate without one.
            Instant failedAt = clock.instant();

            synchronized (this) {
                lastFailureAt = failedAt;
                lastFailure = exception;
                inFlight = null;
            }

            log.warn("Lore data load failed: dataset={}, page={}, error={}; next attempt allowed at {}",
                    progress.dataset, progress.page, describe(exception), failedAt.plus(failureCooldown));

            operation.completeExceptionally(exception);

            throw exception;
        } catch (RuntimeException | Error exception) {
            synchronized (this) {
                inFlight = null;
            }

            operation.completeExceptionally(exception);

            throw exception;
        }
    }

    // Waits for the load another caller is running and reports its outcome.
    private LoreData awaitLoad(CompletableFuture<LoreData> operation) {
        try {
            return operation.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new LoreDataUnavailableException(
                    "Interrupted while waiting for The One API data load", exception);
        } catch (ExecutionException exception) {
            Throwable failure = exception.getCause();

            // A new exception per waiter, as for calls rejected during the cooldown;
            // the starter of the load receives the original one.
            if (failure instanceof RestClientException || failure instanceof LoreDataUnavailableException) {
                throw new LoreDataUnavailableException(
                        "The One API data load failed", failure, currentRetryAt());
            }

            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (failure instanceof Error error) {
                throw error;
            }

            throw new IllegalStateException("Unexpected lore data load failure", failure);
        }
    }

    // When the cooldown started by the latest failure ends; null if there is none.
    private synchronized Instant currentRetryAt() {
        return lastFailureAt == null ? null : lastFailureAt.plus(failureCooldown);
    }

    private LoreData loadData(LoadProgress progress) {
        // The only place where The One API DTOs become the project's own lore models.
        List<LoreQuote> quotes = loadAllQuotes(progress).stream()
                .map(LoreDataService::toLoreQuote)
                .toList();
        List<LoreCharacter> characters = loadAllCharacters(progress).stream()
                .map(LoreDataService::toLoreCharacter)
                .toList();

        Map<String, LoreCharacter> charactersById = indexCharactersById(characters);

        List<LoreQuote> attributedQuotes = filterValidQuotes(quotes, charactersById);

        return new LoreData(
                List.copyOf(quotes),
                Map.copyOf(charactersById),
                List.copyOf(attributedQuotes));
    }

    private static LoreQuote toLoreQuote(ExternalQuote quote) {
        return new LoreQuote(
                quote._id(),
                quote.dialog(),
                quote.movie(),
                quote.character(),
                quote.id());
    }

    private static LoreCharacter toLoreCharacter(ExternalCharacter character) {
        return new LoreCharacter(character.id(), character.name());
    }

    // Fetches all citation pages from the API.
    private List<ExternalQuote> loadAllQuotes(LoadProgress progress) {
        List<ExternalQuote> quotes = new ArrayList<>();

        // Fetch the first page of quotes
        progress.at("quote", 1);
        ExternalQuoteResponse response = theOneApiClient.getQuotes(1, PAGE_SIZE);

        // Add the results
        quotes.addAll(quoteDocs(response));

        int pages = validatedPageCount(response.pages(), quotes.isEmpty(), "quote");

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= pages; page++) {
            progress.at("quote", page);
            ExternalQuoteResponse nextPage = theOneApiClient.getQuotes(page, PAGE_SIZE);

            quotes.addAll(quoteDocs(nextPage));
        }

        return quotes;
    }

    // Fetches all character pages.
    private List<ExternalCharacter> loadAllCharacters(LoadProgress progress) {
        List<ExternalCharacter> characters = new ArrayList<>();

        // Fetch the first page of characters
        progress.at("character", 1);
        ExternalCharacterResponse response = theOneApiClient.getCharacters(1, PAGE_SIZE);

        // Add the results
        characters.addAll(characterDocs(response));

        int pages = validatedPageCount(response.pages(), characters.isEmpty(), "character");

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= pages; page++) {
            progress.at("character", page);
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
    private Map<String, LoreCharacter> indexCharactersById(
            List<LoreCharacter> characters) {

        Map<String, LoreCharacter> charactersById = new HashMap<>();

        for (LoreCharacter character : characters) {
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
    private List<LoreQuote> filterValidQuotes(
            List<LoreQuote> quotes,
            Map<String, LoreCharacter> charactersById) {

        List<LoreQuote> validQuotes = new ArrayList<>();

        for (LoreQuote quote : quotes) {
            if (quote.dialog() == null || quote.dialog().isBlank()) {
                continue;
            }

            if (quote.characterId() == null || quote.characterId().isBlank()) {
                continue;
            }

            if (!charactersById.containsKey(quote.characterId())) {
                continue;
            }

            validQuotes.add(quote);
        }

        return validQuotes;
    }

    // Builds a log-safe description of a load failure. RestClient exception messages
    // can embed the request URL and the upstream response body, so only class names
    // and the HTTP status are kept; LoreDataUnavailableException messages are built
    // in this class from page counts and are safe to log.
    private static String describe(RuntimeException exception) {
        if (exception instanceof LoreDataUnavailableException) {
            return exception.getMessage();
        }

        String type = exception.getClass().getName();

        if (exception instanceof RestClientResponseException responseException) {
            return type + " (HTTP " + responseException.getStatusCode().value() + ")";
        }

        Throwable cause = exception.getCause();

        return cause == null ? type : type + " caused by " + cause.getClass().getName();
    }

    // The dataset and page being requested, so a failed load can report where it stopped.
    private static final class LoadProgress {

        private String dataset;
        private int page;

        void at(String dataset, int page) {
            this.dataset = dataset;
            this.page = page;
        }
    }
}
