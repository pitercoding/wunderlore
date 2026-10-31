package com.wunderlore.game.whosaidit;

// Used when the loaded lore data cannot form a valid round (no attributed quotes or fewer than
// four distinct characters). The controller will respond with 503 Service Unavailable.
public class WhoSaidItInsufficientDataException extends RuntimeException {

    public WhoSaidItInsufficientDataException(String message) {
        super(message);
    }
}
