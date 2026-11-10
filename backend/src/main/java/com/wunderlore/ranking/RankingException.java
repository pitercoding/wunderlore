package com.wunderlore.ranking;

import org.springframework.http.HttpStatus;

// Every way a ranking request can be refused. Each reason has a fixed status and a fixed public
// detail, so a response never echoes what the caller submitted.
public class RankingException extends RuntimeException {

    public enum Reason {
        // 400: the game is not one of the four that can be published.
        INVALID_GAME(HttpStatus.BAD_REQUEST, "Unknown game"),
        // 400: the division is missing, unknown, or does not exist in that game.
        INVALID_DIVISION(HttpStatus.BAD_REQUEST, "Unknown division for this game"),
        // 400: the game id is missing or malformed.
        INVALID_GAME_ID(HttpStatus.BAD_REQUEST, "Invalid game id"),
        // 400: the nickname breaks the rules in NicknamePolicy.
        INVALID_NICKNAME(HttpStatus.BAD_REQUEST,
                "Nickname must be 3 to 16 characters: letters, numbers, spaces, underscores or hyphens"),
        // 400: the nickname is reserved.
        RESERVED_NICKNAME(HttpStatus.BAD_REQUEST, "That nickname is not available"),
        // 404: unknown id, or the game expired or was evicted.
        GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "Game not found or no longer available"),
        // 409: the game has questions or rounds left.
        GAME_NOT_FINISHED(HttpStatus.CONFLICT, "Game has not finished yet"),
        // 503: the ranking database is not configured or cannot be reached. The game itself is
        // unaffected, and the same request can be repeated later.
        UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The leaderboard is unavailable right now");

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

    public RankingException(Reason reason) {
        super(reason.detail());
        this.reason = reason;
    }

    public RankingException(Reason reason, Throwable cause) {
        super(reason.detail(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
