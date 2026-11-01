package com.wunderlore.lore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

import jakarta.annotation.PreDestroy;

@Service
public class LoreDataService {

    private static final Logger log = LoggerFactory.getLogger(LoreDataService.class);

    private static final int PAGE_SIZE = 100;

    // Keeps deadline arithmetic (System.nanoTime() + timeout) clear of long overflow.
    private static final long MAX_TIMEOUT_NANOS = Long.MAX_VALUE / 2;

    // Published only after every page has loaded; volatile so cache hits skip the lock.
    private volatile LoreData cachedLoreData;

    // Guarded by this. The monitor protects only these fields and is never held
    // while The One API is being called or while a caller waits.
    // inFlight: the load every concurrent caller waits on. It is cleared only when its
    // worker has actually left runLoad(), never because the load was cancelled or
    // completed, so a new worker can never overlap an old one.
    // lastFailureAt / lastFailure: set by a failed load, cleared by a successful one.
    private LoadOperation inFlight;
    private Instant lastFailureAt;
    private RuntimeException lastFailure;

    private final TheOneApiClient theOneApiClient;
    private final Clock clock;
    private final Duration failureCooldown;
    private final int maxPages;
    private final Duration loadTimeout;
    private final long loadTimeoutNanos;

    // The only thread that loads the dataset. At most one operation is in flight, so
    // at most one task is ever running or queued.
    private final ThreadPoolExecutor loader = newLoaderExecutor();

    // failureCooldown: how long calls fail fast after a failed load before a new
    // load may start (default 30s; PT0S disables it).
    // maxPages: upper bound on pages fetched per dataset (default 100, i.e. up to
    // 10,000 records each at PAGE_SIZE); a larger reported count fails the load.
    // loadTimeout: total deadline for one complete load, across all of its pages
    // (default 60s; must be positive).
    public LoreDataService(
            TheOneApiClient theOneApiClient,
            Clock clock,
            @Value("${wunderlore.lore.failure-cooldown:PT30S}") Duration failureCooldown,
            @Value("${wunderlore.lore.max-pages:100}") int maxPages,
            @Value("${wunderlore.lore.load-timeout:PT60S}") Duration loadTimeout) {

        if (failureCooldown.isNegative()) {
            throw new IllegalArgumentException("Failure cooldown must not be negative");
        }

        if (maxPages < 1) {
            throw new IllegalArgumentException("Maximum pages must be at least 1");
        }

        if (loadTimeout.isNegative() || loadTimeout.isZero()) {
            throw new IllegalArgumentException("Load timeout must be positive");
        }

        this.theOneApiClient = theOneApiClient;
        this.clock = clock;
        this.failureCooldown = failureCooldown;
        this.maxPages = maxPages;
        this.loadTimeout = loadTimeout;
        this.loadTimeoutNanos = toBoundedNanos(loadTimeout);
    }

    private static long toBoundedNanos(Duration duration) {
        try {
            return Math.min(duration.toNanos(), MAX_TIMEOUT_NANOS);
        } catch (ArithmeticException exception) {
            return MAX_TIMEOUT_NANOS;
        }
    }

    private static ThreadPoolExecutor newLoaderExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "lore-data-loader");

                    thread.setDaemon(true);

                    return thread;
                });

        // An idle loader holds no thread.
        executor.allowCoreThreadTimeOut(true);

        return executor;
    }

    // Interrupts a load in progress; its worker ends without starting a cooldown.
    @PreDestroy
    void shutdown() {
        loader.shutdownNow();
    }

    // Loads the dataset on first use and caches it only once every page has loaded.
    // The load runs on the dedicated loader thread; every caller, including the one
    // that started it, waits for its outcome until the load's total deadline. Callers
    // arriving meanwhile share that load. After an upstream failure or a timeout,
    // callers fail fast until the cooldown expires.
    public LoreData getData() {
        LoreData cached = cachedLoreData;

        if (cached != null) {
            return cached;
        }

        LoadOperation operation;
        boolean startedByCaller = false;

        synchronized (this) {
            if (cachedLoreData != null) {
                return cachedLoreData;
            }

            operation = inFlight;

            if (operation == null) {
                rejectDuringCooldown();

                operation = startOperation();
                startedByCaller = true;
            } else if (operation.phase == Phase.EXPIRED) {
                throw rejectWhileWorkerStops(operation);
            }
        }

        return awaitLoad(operation, startedByCaller);
    }

    // Must be called while holding the monitor. Submitting only enqueues the task,
    // so no I/O happens under the lock.
    private LoadOperation startOperation() {
        LoadOperation operation = new LoadOperation(System.nanoTime() + loadTimeoutNanos);

        inFlight = operation;

        try {
            operation.task = loader.submit(() -> runLoad(operation));
        } catch (RejectedExecutionException exception) {
            inFlight = null;

            throw new LoreDataUnavailableException(
                    "The One API data load could not be started", exception);
        }

        return operation;
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

    // The load timed out but its worker has not left runLoad() yet, so no new load may
    // start, whatever the cooldown. Reports the timeout's own retryAt, which may already
    // be in the past once the cooldown has run out. Must be called while holding the monitor.
    private LoreDataUnavailableException rejectWhileWorkerStops(LoadOperation operation) {
        LoreDataUnavailableException timeout = operation.timeoutFailure;

        log.debug("Lore data request rejected: the timed-out load is still stopping");

        return new LoreDataUnavailableException(
                "The One API data load timed out and is still stopping", timeout, timeout.retryAt());
    }

    // The worker body, on the loader thread, outside the monitor except for its
    // bookkeeping. finish() always runs, so the operation is completed and inFlight
    // cleared only once the worker is really done.
    private void runLoad(LoadOperation operation) {
        synchronized (this) {
            if (operation.abandoned) {
                // Cancelled before it began; expireLocked() already released the operation.
                return;
            }

            operation.started = true;
        }

        LoreData loaded = null;
        Throwable failure = null;
        boolean interrupted = false;

        try {
            loaded = loadData(operation);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            interrupted = Thread.currentThread().isInterrupted();
        } finally {
            finish(operation, loaded, failure, interrupted);
        }

        if (failure instanceof Error error) {
            throw error;
        }
    }

    // Applies the worker's outcome and releases the operation in one critical section.
    // Anyone who sees the operation finished or inFlight cleared sees both done. Outcomes
    // are logged before the result is completed, so a caller released by it can rely on
    // the log line already being written; the lock is held only for these local steps.
    private void finish(LoadOperation operation, LoreData loaded, Throwable failure, boolean interrupted) {
        synchronized (this) {
            try {
                if (operation.phase != Phase.RUNNING) {
                    // Expired while the worker was running: callers already got the timeout.
                    log.debug("Lore data worker ended after its deadline; its outcome was discarded");
                } else if (failure instanceof DeadlineExceededException
                        || (failure == null && deadlinePassed(operation))) {
                    // A late success is never published.
                    expireLocked(operation);
                } else if (failure == null) {
                    boolean recovered = lastFailureAt != null;

                    cachedLoreData = loaded;
                    lastFailureAt = null;
                    lastFailure = null;
                    operation.phase = Phase.FINISHED;

                    if (recovered) {
                        log.info("Lore data loaded after a previous failure: quotes={}, characters={}",
                                loaded.quotes().size(), loaded.charactersById().size());
                    }

                    operation.result.complete(loaded);
                } else if (interrupted) {
                    // Interrupted without a deadline expiry: the application is shutting
                    // down. Not an upstream failure, so no cooldown.
                    operation.phase = Phase.FINISHED;
                    operation.result.completeExceptionally(new LoreDataUnavailableException(
                            "The One API data load was interrupted", failure));
                } else if (failure instanceof RestClientException
                        || failure instanceof LoreDataUnavailableException) {
                    // Only upstream failures start a cooldown; configuration and programming
                    // errors propagate without one.
                    Instant failedAt = clock.instant();

                    lastFailureAt = failedAt;
                    lastFailure = (RuntimeException) failure;
                    operation.phase = Phase.FINISHED;

                    log.warn("Lore data load failed: dataset={}, page={}, error={}; next attempt allowed at {}",
                            operation.progress.dataset, operation.progress.page,
                            describe((RuntimeException) failure), failedAt.plus(failureCooldown));

                    operation.result.completeExceptionally(failure);
                } else {
                    operation.phase = Phase.FINISHED;
                    operation.result.completeExceptionally(failure);
                }
            } finally {
                if (inFlight == operation) {
                    inFlight = null;
                }
            }
        }
    }

    private static boolean deadlinePassed(LoadOperation operation) {
        return System.nanoTime() - operation.deadlineNanos >= 0;
    }

    // Records the timeout once, completes the shared result with it and starts the
    // cooldown. Afterwards it only returns the recorded failure, or null if the
    // operation finished first. Must be called while holding the monitor.
    private LoreDataUnavailableException expireLocked(LoadOperation operation) {
        if (operation.phase != Phase.RUNNING) {
            return operation.timeoutFailure;
        }

        Instant failedAt = clock.instant();
        Instant retryAt = failedAt.plus(failureCooldown);
        LoreDataUnavailableException timeout = new LoreDataUnavailableException(
                "The One API data load exceeded its " + loadTimeout + " deadline; next attempt allowed at "
                        + retryAt,
                null,
                retryAt);

        operation.phase = Phase.EXPIRED;
        operation.timeoutFailure = timeout;
        lastFailureAt = failedAt;
        lastFailure = timeout;

        log.warn("Lore data load timed out: dataset={}, page={}, timeout={}; next attempt allowed at {}",
                operation.progress.dataset, operation.progress.page, loadTimeout, retryAt);

        operation.result.completeExceptionally(timeout);

        if (!operation.started) {
            // Cancelling the task stops it from ever running, so nothing else would
            // clear inFlight.
            operation.abandoned = true;

            if (inFlight == operation) {
                inFlight = null;
            }
        }

        return timeout;
    }

    // Expires the operation on behalf of a caller that reached the deadline and asks
    // the worker to stop. inFlight stays set until the worker really exits.
    // Returns null if the operation finished on its own first.
    private LoreDataUnavailableException expire(LoadOperation operation) {
        LoreDataUnavailableException timeout;
        boolean recorded;

        synchronized (this) {
            recorded = operation.phase == Phase.RUNNING;
            timeout = expireLocked(operation);
        }

        if (recorded) {
            // Best effort: the worker may keep running briefly after this returns.
            operation.task.cancel(true);
        }

        return timeout;
    }

    // Waits for the operation until its deadline and reports its outcome.
    private LoreData awaitLoad(LoadOperation operation, boolean startedByCaller) {
        try {
            long remaining = operation.deadlineNanos - System.nanoTime();

            return operation.result.get(Math.max(remaining, 0), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            LoreDataUnavailableException timeout = expire(operation);

            if (timeout != null) {
                throw timeout;
            }

            // The load finished at the deadline; its outcome is already complete.
            return awaitLoad(operation, startedByCaller);
        } catch (InterruptedException exception) {
            // This caller's own interruption: the shared load carries on for the others.
            Thread.currentThread().interrupt();

            throw new LoreDataUnavailableException(
                    "Interrupted while waiting for The One API data load", exception);
        } catch (CancellationException exception) {
            throw new LoreDataUnavailableException("The One API data load was cancelled", exception);
        } catch (ExecutionException exception) {
            Throwable failure = exception.getCause();

            // Every caller gets the one recorded timeout.
            if (failure != null && failure == operation.timeoutFailure) {
                throw operation.timeoutFailure;
            }

            // The starter of the load receives the original exception.
            if (startedByCaller) {
                if (failure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                if (failure instanceof Error error) {
                    throw error;
                }
            }

            // A new exception per waiter, as for calls rejected during the cooldown.
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

    private LoreData loadData(LoadOperation operation) {
        // The only place where The One API DTOs become the project's own lore models.
        List<LoreQuote> quotes = loadAllQuotes(operation).stream()
                .map(LoreDataService::toLoreQuote)
                .toList();
        List<LoreCharacter> characters = loadAllCharacters(operation).stream()
                .map(LoreDataService::toLoreCharacter)
                .toList();

        Map<String, LoreCharacter> charactersById = indexCharactersById(characters);

        List<LoreQuote> attributedQuotes = filterValidQuotes(quotes, charactersById);

        return new LoreData(
                List.copyOf(quotes),
                Map.copyOf(charactersById),
                List.copyOf(attributedQuotes));
    }

    // Stops the load before any upstream request once its total deadline has passed
    // or a caller has expired it.
    private static void checkDeadline(LoadOperation operation) {
        if (operation.phase != Phase.RUNNING || deadlinePassed(operation)) {
            throw new DeadlineExceededException();
        }
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
    private List<ExternalQuote> loadAllQuotes(LoadOperation operation) {
        List<ExternalQuote> quotes = new ArrayList<>();

        // Fetch the first page of quotes
        checkDeadline(operation);
        operation.progress.at("quote", 1);
        ExternalQuoteResponse response = theOneApiClient.getQuotes(1, PAGE_SIZE);

        // Add the results
        quotes.addAll(quoteDocs(response));

        int pages = validatedPageCount(response.pages(), quotes.isEmpty(), "quote");

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= pages; page++) {
            checkDeadline(operation);
            operation.progress.at("quote", page);
            ExternalQuoteResponse nextPage = theOneApiClient.getQuotes(page, PAGE_SIZE);

            quotes.addAll(quoteDocs(nextPage));
        }

        return quotes;
    }

    // Fetches all character pages.
    private List<ExternalCharacter> loadAllCharacters(LoadOperation operation) {
        List<ExternalCharacter> characters = new ArrayList<>();

        // Fetch the first page of characters
        checkDeadline(operation);
        operation.progress.at("character", 1);
        ExternalCharacterResponse response = theOneApiClient.getCharacters(1, PAGE_SIZE);

        // Add the results
        characters.addAll(characterDocs(response));

        int pages = validatedPageCount(response.pages(), characters.isEmpty(), "character");

        // Fetch the remaining pages, starting from page 2
        for (int page = 2; page <= pages; page++) {
            checkDeadline(operation);
            operation.progress.at("character", page);
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

    private enum Phase {
        // The worker may still be loading and nothing has been decided.
        RUNNING,
        // The deadline passed: the timeout is recorded and the result completed, but the
        // worker may still be running until it leaves runLoad().
        EXPIRED,
        // The worker's outcome is applied, the result completed and inFlight cleared.
        FINISHED
    }

    // One complete load shared by every caller that arrives while it runs.
    private static final class LoadOperation {

        // Completed exactly once, under the service monitor.
        private final CompletableFuture<LoreData> result = new CompletableFuture<>();
        private final long deadlineNanos;
        private final LoadProgress progress = new LoadProgress();

        // The fields below are guarded by the service monitor; phase is volatile so the
        // worker can check it between requests without taking the lock.
        private volatile Phase phase = Phase.RUNNING;
        private Future<?> task;
        private boolean started;
        private boolean abandoned;
        private LoreDataUnavailableException timeoutFailure;

        private LoadOperation(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }
    }

    // Raised inside the worker when the deadline has passed; never leaves the service.
    private static final class DeadlineExceededException extends RuntimeException {

        private DeadlineExceededException() {
            super("Lore data load deadline exceeded", null, false, false);
        }
    }

    // The dataset and page being requested, so a failed load can report where it stopped.
    private static final class LoadProgress {

        private volatile String dataset;
        private volatile int page;

        void at(String dataset, int page) {
            this.dataset = dataset;
            this.page = page;
        }
    }
}
