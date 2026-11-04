package com.wunderlore.game.lorechallenge;

import org.springframework.http.HttpStatus;

// Every way a Lore Challenge request can be refused. Each reason has a fixed status and a fixed
// public detail, so a response never echoes what the caller submitted.
public class LoreChallengeException extends RuntimeException {

    public enum Reason {
        // 404: unknown id, or the match expired or was evicted.
        MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Match not found or no longer available"),
        // 409: the match is over; nothing more can be answered or asked.
        MATCH_FINISHED(HttpStatus.CONFLICT, "Match has already finished"),
        // 409: asked for the result of a match that still has questions left.
        MATCH_NOT_FINISHED(HttpStatus.CONFLICT, "Match has not finished yet"),
        // 400: the question id does not belong to this match.
        QUESTION_NOT_IN_MATCH(HttpStatus.BAD_REQUEST, "Question does not belong to this match"),
        // 409: the question belongs to the match but is not the one awaiting an answer
        // (typically an answer sent twice).
        NOT_CURRENT_QUESTION(HttpStatus.CONFLICT, "That is not the current question of this match"),
        // 400: the option id is not one of the four offered.
        INVALID_OPTION(HttpStatus.BAD_REQUEST, "Invalid answer option"),
        // 422: the chosen category and difficulty cannot form a full match.
        NOT_ENOUGH_QUESTIONS(HttpStatus.UNPROCESSABLE_ENTITY,
                "Not enough questions are available for that selection"),
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

    public LoreChallengeException(Reason reason) {
        super(reason.detail());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
