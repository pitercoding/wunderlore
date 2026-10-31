package com.wunderlore.game.whosaidit;

// It will be used when the round does not exist or has already expired. The controller will respond with 404 Not Found.
public class WhoSaidItRoundNotFoundException extends RuntimeException {
    
    public WhoSaidItRoundNotFoundException() {
        super("Round not found or expired");
    }
}
