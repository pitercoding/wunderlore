package com.wunderlore.game.whereinmiddleearth.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// What the client says it is answering (the round) and the point it chose, in the map's own
// coordinates (a 1000 x 700 viewBox). There is no score, distance or place field, so a client has
// nothing to claim. Whether the round is the one waiting is checked against the game itself.
public record AnswerRequest(
        @NotNull @Min(1) @Max(10) Integer roundNumber,
        @NotNull @DecimalMin("0") @DecimalMax("1000") Double x,
        @NotNull @DecimalMin("0") @DecimalMax("700") Double y) {
}
