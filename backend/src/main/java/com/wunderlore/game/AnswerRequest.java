package com.wunderlore.game;

import jakarta.validation.constraints.NotBlank;

public record AnswerRequest(
        @NotBlank String roundId,
        @NotBlank String characterId) {
}
