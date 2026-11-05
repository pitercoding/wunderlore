package com.wunderlore.lore;

import java.util.List;
import java.util.Map;

// quotes: every loaded quote, in upstream order, unfiltered.
// charactersById: characters with a nonblank id and name.
// attributedQuotes: quotes with a nonblank dialog and a known character.
// catalogQuotes: the only quotes the public catalog may serve, at most
// CatalogQuoteSelection.MAX_QUOTES, chosen from attributedQuotes.
public record LoreData(
        List<LoreQuote> quotes,
        Map<String, LoreCharacter> charactersById,
        List<LoreQuote> attributedQuotes,
        List<LoreQuote> catalogQuotes) {

    public LoreData {
        if (catalogQuotes.size() > CatalogQuoteSelection.MAX_QUOTES) {
            throw new IllegalArgumentException(
                    "The public catalog holds at most " + CatalogQuoteSelection.MAX_QUOTES + " quotes");
        }
    }

    // Derives the public catalog from attributedQuotes with CatalogQuoteSelection.
    public LoreData(
            List<LoreQuote> quotes,
            Map<String, LoreCharacter> charactersById,
            List<LoreQuote> attributedQuotes) {
        this(quotes, charactersById, attributedQuotes, CatalogQuoteSelection.select(attributedQuotes));
    }
}
