package com.wunderlore.game.whereinmiddleearth.dto;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthDifficulty;

import jakarta.validation.constraints.NotNull;

// The difficulty is the only thing a client chooses. An unknown value fails while the body is
// read, and is answered as a malformed request like any other bad body.
public record CreateGameRequest(@NotNull WhereInMiddleEarthDifficulty difficulty) {
}
