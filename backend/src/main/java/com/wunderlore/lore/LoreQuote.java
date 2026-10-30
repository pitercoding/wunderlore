package com.wunderlore.lore;

// A quote as the project uses it. Every field except id may be null: LoreData.quotes
// keeps quotes unfiltered, and only attributedQuotes guarantees a dialog and a known character.
// id: the upstream quote identifier.
// alternateId: the upstream's second identifier field, served by the catalog as received.
// movieId: the upstream movie identifier; no movie data is loaded.
// characterId: key into LoreData.charactersById.
public record LoreQuote(
        String id,
        String dialog,
        String movieId,
        String characterId,
        String alternateId) {
}
