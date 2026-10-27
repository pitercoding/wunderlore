package com.wunderlore.lore;

import java.util.List;
import java.util.Map;

import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalQuote;

// quotes: every loaded quote, in upstream order, unfiltered.
// charactersById: characters with a nonblank id and name.
// attributedQuotes: quotes with a nonblank dialog and a known character.
public record LoreData(
        List<ExternalQuote> quotes,
        Map<String, ExternalCharacter> charactersById,
        List<ExternalQuote> attributedQuotes) {
}
