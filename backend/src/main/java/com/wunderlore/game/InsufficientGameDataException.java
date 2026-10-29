package com.wunderlore.game;

// Used when the loaded lore data cannot form a valid round (no attributed quotes or fewer than
// four distinct characters). The controller will respond with 503 Service Unavailable.
public class InsufficientGameDataException extends RuntimeException {

    public InsufficientGameDataException(String message) {
        super(message);
    }
}
