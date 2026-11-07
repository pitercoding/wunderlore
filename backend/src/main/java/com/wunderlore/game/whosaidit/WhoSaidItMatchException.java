package com.wunderlore.game.whosaidit;

import org.springframework.http.HttpStatus;

// Every way a competitive Who Said It? match request can be refused. Each reason has a fixed
// status and a fixed public detail, so a response never echoes what the caller submitted.
public class WhoSaidItMatchException extends RuntimeException {

    public enum Reason {
        // 404: unknown id, or the match expired or was evicted.
        MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Match not found or no longer available"),
        // 409: the match is over; there is no current question.
        MATCH_FINISHED(HttpStatus.CONFLICT, "Match has already finished"),
        // 409: asked for the result of a match that still has questions left.
        MATCH_NOT_FINISHED(HttpStatus.CONFLICT, "Match has not finished yet"),
        // 409: the answer is for a question that is not the one awaiting an answer, or it
        // contradicts the answer already recorded for it. A repeat of the recorded answer is
        // not an error: it is accepted again without counting twice.
        STALE_SUBMISSION(HttpStatus.CONFLICT, "That question is not awaiting an answer"),
        // 400: the character id is not one of the four offered for that question.
        INVALID_OPTION(HttpStatus.BAD_REQUEST, "Invalid answer option"),
        // 503: too many matches are open at once.
        CAPACITY_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "Too many active matches, try again later");

        private final HttpStatus status;
        private final String detail;

        Reason(HttpStatus status, String detail) {
            this.status = status;
            this.detail = detail;
        }

        public HttpStatus status() {
            return status;
        }

        public String detail() {
            return detail;
        }
    }

    private final Reason reason;

    public WhoSaidItMatchException(Reason reason) {
        super(reason.detail());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
