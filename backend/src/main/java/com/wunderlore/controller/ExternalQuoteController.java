package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.lore.LoreCharacter;
import com.wunderlore.lore.LoreData;
import com.wunderlore.lore.LoreDataService;
import com.wunderlore.lore.LoreDataUnavailableException;
import com.wunderlore.lore.LoreQuote;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class ExternalQuoteController {

    private static final Logger log = LoggerFactory.getLogger(ExternalQuoteController.class);

    private static final String UNAVAILABLE_DETAIL = "Unable to load quotes right now";

    private final LoreDataService loreDataService;
    private final Clock clock;

    public ExternalQuoteController(LoreDataService loreDataService, Clock clock) {
        this.loreDataService = loreDataService;
        this.clock = clock;
    }

    @GetMapping("/quotes")
    public CatalogQuotePage getQuotes(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        LoreData data = loreDataService.getData();

        return paginate(data.quotes(), data.charactersById(), page, limit);
    }

    // Slices the in-memory quotes the same way The One API paginates them:
    // pages is at least 1, a page past the end returns an empty docs list,
    // and offset stays null because the request is page-based.
    private CatalogQuotePage paginate(
            List<LoreQuote> quotes,
            Map<String, LoreCharacter> charactersById,
            int page,
            int limit) {
        int total = quotes.size();
        int pages = Math.max(1, (total + limit - 1) / limit);

        // long avoids overflow when page is very large.
        long start = (long) (page - 1) * limit;
        List<CatalogQuote> docs = List.of();

        if (start < total) {
            int end = (int) Math.min(start + limit, total);
            docs = quotes.subList((int) start, end).stream()
                    .map(quote -> toCatalogQuote(quote, charactersById))
                    .toList();
        }

        return new CatalogQuotePage(docs, total, limit, null, page, pages);
    }

    // The name comes only from the loaded lore data; an unknown or missing
    // character id leaves characterName null rather than guessing one.
    private CatalogQuote toCatalogQuote(
            LoreQuote quote, Map<String, LoreCharacter> charactersById) {
        LoreCharacter character =
                quote.characterId() == null ? null : charactersById.get(quote.characterId());

        return new CatalogQuote(
                quote.id(),
                quote.dialog(),
                quote.movieId(),
                quote.characterId(),
                quote.alternateId(),
                character == null ? null : character.name());
    }

    @ExceptionHandler({
            RestClientException.class,
            TheOneApiNotConfiguredException.class
    })
    public ProblemDetail handleCatalogLoadFailure(Exception exception) {
        // LoreDataService already logs load failures at WARN; this only ties them to the request.
        log.debug("Catalog quotes request failed: {}", exception.getClass().getName());

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                UNAVAILABLE_DETAIL);
    }

    // Same 503 as above, plus Retry-After when the lore data load is cooling down.
    @ExceptionHandler(LoreDataUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleLoreDataUnavailable(
            LoreDataUnavailableException exception) {
        log.debug("Catalog quotes request failed: {}", exception.getClass().getName());

        return LoreUnavailableResponses.serviceUnavailable(exception, clock, UNAVAILABLE_DETAIL);
    }
}
