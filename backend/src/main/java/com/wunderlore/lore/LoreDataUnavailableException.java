package com.wunderlore.lore;

// Used when The One API answers with a null body or a null docs list, so the dataset cannot be loaded.
public class LoreDataUnavailableException extends RuntimeException {

    public LoreDataUnavailableException(String message) {
        super(message);
    }
}
