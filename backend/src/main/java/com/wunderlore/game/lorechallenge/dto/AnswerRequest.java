package com.wunderlore.game.lorechallenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Only ids are accepted: the question being answered and the option chosen. There is no score
// or correctness field, so a client has nothing to claim. Whether the ids are the right ones is
// checked against the match itself.
public record AnswerRequest(
        @NotBlank @Size(max = 64) String questionId,
        @NotBlank @Size(max = 8) String optionId) {
}
