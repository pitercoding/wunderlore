package com.wunderlore.game.whichcharacter;

// Raised by the scoring service for answers that cannot be scored. The messages are internal:
// the controller never sends them to clients.
public class WhichCharacterInvalidAnswersException extends IllegalArgumentException {

    public WhichCharacterInvalidAnswersException(String message) {
        super(message);
    }
}
