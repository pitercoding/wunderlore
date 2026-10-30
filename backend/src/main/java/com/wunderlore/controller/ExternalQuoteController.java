package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import com.wunderlore.lore.LoreDataService;
import com.wunderlore.lore.LoreDataUnavailableException;
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
    public ExternalQuoteResponse getQuotes(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        List<ExternalQuote> quotes = loreDataService.getData().quotes();

        return paginate(quotes, page, limit);
    }

    // Slices the in-memory quotes the same way The One API paginates them:
    // pages is at least 1, a page past the end returns an empty docs list,
    // and offset stays null because the request is page-based.
    private ExternalQuoteResponse paginate(List<ExternalQuote> quotes, int page, int limit) {
        int total = quotes.size();
        int pages = Math.max(1, (total + limit - 1) / limit);

        // long avoids overflow when page is very large.
        long start = (long) (page - 1) * limit;
        List<ExternalQuote> docs = List.of();

        if (start < total) {
            int end = (int) Math.min(start + limit, total);
            docs = quotes.subList((int) start, end);
        }

        return new ExternalQuoteResponse(docs, total, limit, null, page, pages);
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
