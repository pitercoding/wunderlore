package com.wunderlore.controller;

import com.wunderlore.lore.LoreDataUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Builds the 503 sent while lore data is unavailable. Retry-After is added only when the
// exception knows when a new load may start, so other 503s never carry it.
final class LoreUnavailableResponses {

    private LoreUnavailableResponses() {
    }

    static ResponseEntity<ProblemDetail> serviceUnavailable(
            LoreDataUnavailableException exception, Clock clock, String detail) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
        Instant retryAt = exception.retryAt();

        if (retryAt != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(secondsUntil(retryAt, clock)));
        }

        return response.body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail));
    }

    // Delta-seconds rounded up, so the client never retries before the cooldown ends;
    // 0 once the retry is already available.
    static long secondsUntil(Instant retryAt, Clock clock) {
        Duration remaining = Duration.between(clock.instant(), retryAt);

        if (remaining.isNegative() || remaining.isZero()) {
            return 0;
        }

        return remaining.getNano() > 0 ? remaining.getSeconds() + 1 : remaining.getSeconds();
    }
}
