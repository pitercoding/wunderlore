package com.wunderlore.game.whosaidit;

// Used when a player attempts to respond again to a round that has already been answered. The response will be 409 Conflict.
public class WhoSaidItRoundAlreadyAnsweredException extends RuntimeException {

    public WhoSaidItRoundAlreadyAnsweredException() {
        super("Round has already been answered");
    }
}
