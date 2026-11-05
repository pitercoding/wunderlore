package com.wunderlore.game.whoami.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// What the client says it is answering (round and clue) and the option it chose. There is no
// score, correctness or character field, so a client has nothing to claim. Whether the round and
// clue are the ones waiting is checked against the game itself.
public record AnswerRequest(
        @NotNull @Min(1) @Max(10) Integer roundNumber,
        @NotNull @Min(1) @Max(5) Integer clueNumber,
        @NotNull @Pattern(regexp = "[a-d]") String optionId) {
}
