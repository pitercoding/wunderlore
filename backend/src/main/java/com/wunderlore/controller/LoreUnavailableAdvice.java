package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.game.whosaidit.WhoSaidItController;
import com.wunderlore.lore.LoreDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.HandlerMethod;

import java.time.Clock;

// Turns lore data being unavailable into the 503 sent by the controllers that depend on it.
// Scoped to those controllers on purpose: ExternalBookController reads The One API directly
// and keeps its own handler, so a RestClientException from it must never reach this advice.
// The public detail and the log line differ per controller, so both follow the controller
// that raised the exception. Logging stays on that controller's logger, at DEBUG: upstream
// failures are already logged at WARN by LoreDataService, and only the exception class is
// written, since RestClient messages can embed URLs and response bodies.
@RestControllerAdvice(assignableTypes = { WhoSaidItController.class, ExternalQuoteController.class })
class LoreUnavailableAdvice {

    private static final Logger GAME_LOG = LoggerFactory.getLogger(WhoSaidItController.class);
    private static final Logger CATALOG_LOG = LoggerFactory.getLogger(ExternalQuoteController.class);

    private final Clock clock;

    LoreUnavailableAdvice(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({
            RestClientException.class,
            TheOneApiNotConfiguredException.class
    })
    ProblemDetail handleUpstreamFailure(Exception exception, HandlerMethod handler) {
        Source source = sourceOf(handler);

        source.log().debug("{}: {}", source.failure(), exception.getClass().getName());

        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, source.detail());
    }

    // Same 503 as above, plus Retry-After when the lore data load is cooling down.
    @ExceptionHandler(LoreDataUnavailableException.class)
    ResponseEntity<ProblemDetail> handleLoreDataUnavailable(
            LoreDataUnavailableException exception, HandlerMethod handler) {
        Source source = sourceOf(handler);

        source.log().debug("{}: {}", source.failure(), exception.getClass().getName());

        return LoreUnavailableResponses.serviceUnavailable(exception, clock, source.detail());
    }

    private static Source sourceOf(HandlerMethod handler) {
        if (WhoSaidItController.class.isAssignableFrom(handler.getBeanType())) {
            return new Source(
                    GAME_LOG, "Game round generation failed", WhoSaidItController.ROUND_UNAVAILABLE_DETAIL);
        }

        return new Source(
                CATALOG_LOG, "Catalog quotes request failed", ExternalQuoteController.UNAVAILABLE_DETAIL);
    }

    private record Source(Logger log, String failure, String detail) {
    }
}
