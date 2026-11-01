package com.wunderlore.game.whichcharacter.dto;

import com.wunderlore.game.whichcharacter.CharacterId;
import com.wunderlore.game.whichcharacter.CharacterResult;

public record CharacterResultResponse(
        CharacterId id,
        String name,
        String description) {

    public static CharacterResultResponse from(CharacterResult result) {
        return new CharacterResultResponse(result.id(), result.name(), result.description());
    }
}
