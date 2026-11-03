package com.wunderlore.game.whosaidit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// characterId must be one of the round's option ids, so it is an id from the lore data: The One
// API issues 24-character hex ids, and a round never offers anything else. The limit leaves
// room for any id scheme up to a SHA-256 hex digest (and a UUID) while bounding what is
// compared and stored; it is not a format check, which belongs to the round's own options.
public record WhoSaidItAnswerRequest(
        @NotBlank @Size(max = WhoSaidItAnswerRequest.MAX_CHARACTER_ID_LENGTH) String characterId) {

    public static final int MAX_CHARACTER_ID_LENGTH = 64;
}
