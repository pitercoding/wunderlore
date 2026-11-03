package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.lore.LoreBacked;
import com.wunderlore.lore.LoreDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.HandlerMethod;

import java.time.Clock;

// Turns lore data being unavailable into a 503 for any controller, with no list of controllers
// to maintain. The public detail and the log line come from the controller's @LoreBacked
// annotation, or from generic wording when it has none. Controller-local handlers still win,
// so ExternalBookController, which reads The One API directly, keeps its own response.
// Logging stays on the controller's logger, at DEBUG: upstream failures are already logged at
// WARN by LoreDataService, and only the exception class is written, since RestClient messages
// can embed URLs and response bodies.
@RestControllerAdvice
class LoreUnavailableAdvice {

    static final String DEFAULT_DETAIL = "Lore data is unavailable right now";
    private static final String DEFAULT_FAILURE = "Lore data request failed";
    private static final Logger DEFAULT_LOG = LoggerFactory.getLogger(LoreUnavailableAdvice.class);

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

    // handler is null when the exception was not raised by a controller method.
    private static Source sourceOf(HandlerMethod handler) {
        if (handler == null) {
            return new Source(DEFAULT_LOG, DEFAULT_FAILURE, DEFAULT_DETAIL);
        }

        Class<?> controller = handler.getBeanType();
        LoreBacked loreBacked = AnnotatedElementUtils.findMergedAnnotation(controller, LoreBacked.class);
        Logger log = LoggerFactory.getLogger(controller);

        if (loreBacked == null) {
            return new Source(log, DEFAULT_FAILURE, DEFAULT_DETAIL);
        }

        return new Source(log, loreBacked.failure(), loreBacked.unavailableDetail());
    }

    private record Source(Logger log, String failure, String detail) {
    }
}
