package com.wunderlore.lore;

// Used when The One API answers with a null body, a null docs list or invalid paging metadata,
// so the dataset cannot be loaded, and while a recent load failure is cooling down.
public class LoreDataUnavailableException extends RuntimeException {

    public LoreDataUnavailableException(String message) {
        super(message);
    }

    public LoreDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
