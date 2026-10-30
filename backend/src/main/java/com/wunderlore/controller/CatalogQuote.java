package com.wunderlore.controller;

// A quote as served by the catalog: the upstream fields plus the resolved character.
// characterName is null when the character is not in the loaded lore data.
public record CatalogQuote(
        String _id,
        String dialog,
        String movie,
        String character,
        String id,
        String characterName) {
}
