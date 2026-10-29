package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalBookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/api/catalog")
public class ExternalBookController {

    private static final Logger log = LoggerFactory.getLogger(ExternalBookController.class);

    private final TheOneApiClient theOneApiClient;

    public ExternalBookController(TheOneApiClient theOneApiClient) {
        this.theOneApiClient = theOneApiClient;
    }

    @GetMapping("/books")
    public ExternalBookResponse getBooks() {
        ExternalBookResponse response = theOneApiClient.getBooks();

        // A missing body or docs list would otherwise be sent as a 200 with no books.
        if (response == null || response.docs() == null) {
            throw new EmptyBooksResponseException();
        }

        return response;
    }

    @ExceptionHandler({
            RestClientException.class,
            EmptyBooksResponseException.class
    })
    public ProblemDetail handleCatalogLoadFailure(Exception exception) {
        // Books are fetched directly from The One API rather than through LoreDataService,
        // so this is the only place the failure is logged.
        log.warn("Catalog books request failed: error={}", describe(exception));

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to load books right now");
    }

    // Builds a log-safe description of a failure. RestClient exception messages can
    // embed the request URL and the upstream response body, so only class names and
    // the HTTP status are kept.
    private static String describe(Exception exception) {
        if (exception instanceof EmptyBooksResponseException) {
            return exception.getMessage();
        }

        String type = exception.getClass().getName();

        if (exception instanceof RestClientResponseException responseException) {
            return type + " (HTTP " + responseException.getStatusCode().value() + ")";
        }

        Throwable cause = exception.getCause();

        return cause == null ? type : type + " caused by " + cause.getClass().getName();
    }

    // Raised when The One API answers without a body or docs list.
    private static final class EmptyBooksResponseException extends RuntimeException {

        EmptyBooksResponseException() {
            super("The One API returned an empty book response");
        }
    }
}
