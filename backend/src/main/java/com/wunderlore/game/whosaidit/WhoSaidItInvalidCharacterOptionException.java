package com.wunderlore.game.whosaidit;

// If the submitted character is not among the four options for the round, the response will be "400 Bad Request." The attempt will remain available.
public class WhoSaidItInvalidCharacterOptionException extends RuntimeException {

    public WhoSaidItInvalidCharacterOptionException() {
        super("Character was not an option in this round");
    }
}
