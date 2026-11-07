package com.wunderlore.game.whosaidit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// What the client says it is answering (the question number) and the character it chose. There is
// no score or correctness field, so a client has nothing to claim.
public record MatchAnswerRequest(
        @NotNull @Min(1) @Max(10) Integer questionNumber,
        @NotBlank @Size(max = 64) String characterId) {
}
