package com.wunderlore.game;

// Used when the maximum number of active rounds has been reached. The controller will respond with 503 Service Unavailable.
public class RoundCapacityExceededException extends RuntimeException {

    public RoundCapacityExceededException() {
        super("Maximum number of active rounds reached");
    }
}
