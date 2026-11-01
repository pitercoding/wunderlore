package com.wunderlore.game.whichcharacter;

public record QuizOption(
        String id,
        String text,
        CharacterId primaryCharacter,
        CharacterId secondaryCharacter) {
}
