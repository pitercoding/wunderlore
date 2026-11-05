package com.wunderlore.game.whoami;

import org.springframework.http.HttpStatus;

// Every way a Who Am I? request can be refused. Each reason has a fixed status and a fixed public
// detail, so a response never echoes what the caller submitted.
public class WhoAmIException extends RuntimeException {

    public enum Reason {
        // 404: unknown id, or the game expired or was evicted.
        GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "Game not found or no longer available"),
        // 409: the game is over; nothing more can be answered or asked.
        GAME_FINISHED(HttpStatus.CONFLICT, "Game has already finished"),
        // 409: asked for the result of a game that still has rounds left.
        GAME_NOT_FINISHED(HttpStatus.CONFLICT, "Game has not finished yet"),
        // 409: the answer is for a round or clue that is not the one awaiting an answer
        // (typically an answer sent twice).
        STALE_SUBMISSION(HttpStatus.CONFLICT, "That clue is not awaiting an answer"),
        // 400: the option id is not one of the four offered.
        INVALID_OPTION(HttpStatus.BAD_REQUEST, "Invalid answer option"),
        // 422: the difficulty has too few characters to form a full game.
        NOT_ENOUGH_CHARACTERS(HttpStatus.UNPROCESSABLE_CONTENT,
                "Not enough characters are available for that difficulty"),
        // 503: too many games are open at once.
        CAPACITY_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "Too many active games, try again later");

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

    public WhoAmIException(Reason reason) {
        super(reason.detail());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
