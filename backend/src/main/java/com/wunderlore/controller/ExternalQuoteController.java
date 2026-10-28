package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import com.wunderlore.lore.LoreDataService;
import com.wunderlore.lore.LoreDataUnavailableException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class ExternalQuoteController {

    private final LoreDataService loreDataService;

    public ExternalQuoteController(LoreDataService loreDataService) {
        this.loreDataService = loreDataService;
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
            LoreDataUnavailableException.class,
            TheOneApiNotConfiguredException.class
    })
    public ProblemDetail handleCatalogLoadFailure(Exception exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to load quotes right now");
    }
}
