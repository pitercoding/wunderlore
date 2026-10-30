package com.wunderlore.lore;

import java.util.List;
import java.util.Map;

// quotes: every loaded quote, in upstream order, unfiltered.
// charactersById: characters with a nonblank id and name.
// attributedQuotes: quotes with a nonblank dialog and a known character.
public record LoreData(
        List<LoreQuote> quotes,
        Map<String, LoreCharacter> charactersById,
        List<LoreQuote> attributedQuotes) {
}
