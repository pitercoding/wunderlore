package com.wunderlore.game;

// It will be used when the round does not exist or has already expired. The controller will respond with 404 Not Found.
public class RoundNotFoundException extends RuntimeException {
    
    public RoundNotFoundException() {
        super("Round not found or expired");
    }
}
