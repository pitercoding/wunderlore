package com.wunderlore.lore;

import java.time.Instant;

// Used when The One API answers with a null body, a null docs list or invalid paging metadata,
// so the dataset cannot be loaded, and while a recent load failure is cooling down.
public class LoreDataUnavailableException extends RuntimeException {

    // When a new load may be attempted; null when the failure carries no cooldown.
    private final Instant retryAt;

    public LoreDataUnavailableException(String message) {
        this(message, null, null);
    }

    public LoreDataUnavailableException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public LoreDataUnavailableException(String message, Throwable cause, Instant retryAt) {
        super(message, cause);
        this.retryAt = retryAt;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
