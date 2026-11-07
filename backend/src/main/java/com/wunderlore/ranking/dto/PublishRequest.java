package com.wunderlore.ranking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Which game to publish and under which nickname. There is no score, maximum or percentage field:
// the backend reads them from the game itself. Unknown properties are ignored, so a client that
// sends a score gets no effect from it. The nickname rules live in NicknamePolicy.
public record PublishRequest(
        @NotBlank @Size(max = 32) String gameType,
        @NotBlank @Size(max = 64) String gameId,
        @NotBlank @Size(max = 64) String nickname) {
}
