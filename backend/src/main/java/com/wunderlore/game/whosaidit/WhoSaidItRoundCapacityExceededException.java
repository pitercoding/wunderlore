package com.wunderlore.game.whosaidit;

// Used when the maximum number of active rounds has been reached. The controller will respond with 503 Service Unavailable.
public class WhoSaidItRoundCapacityExceededException extends RuntimeException {

    public WhoSaidItRoundCapacityExceededException() {
        super("Maximum number of active rounds reached");
    }
}
