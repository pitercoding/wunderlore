package com.wunderlore.game;

// Used when a player attempts to respond again to a round that has already been answered. The response will be 409 Conflict.
public class RoundAlreadyAnsweredException extends RuntimeException {

    public RoundAlreadyAnsweredException() {
        super("Round has already been answered");
    }
}
