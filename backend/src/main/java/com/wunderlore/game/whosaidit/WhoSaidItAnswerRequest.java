package com.wunderlore.game.whosaidit;

import jakarta.validation.constraints.NotBlank;

public record WhoSaidItAnswerRequest(
        @NotBlank String characterId) {
}
