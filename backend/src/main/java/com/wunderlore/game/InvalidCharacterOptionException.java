package com.wunderlore.game;

// If the submitted character is not among the four options for the round, the response will be "400 Bad Request." The attempt will remain available.
public class InvalidCharacterOptionException extends RuntimeException {

    public InvalidCharacterOptionException() {
        super("Character was not an option in this round");
    }
}
