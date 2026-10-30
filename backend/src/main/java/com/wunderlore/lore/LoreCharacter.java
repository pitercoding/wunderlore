package com.wunderlore.lore;

// A character as the project uses it. id and name are only guaranteed to be nonblank
// for characters kept in LoreData.charactersById.
public record LoreCharacter(
        String id,
        String name) {
}
