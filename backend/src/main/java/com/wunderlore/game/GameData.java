package com.wunderlore.game;

import java.util.List;
import java.util.Map;

import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalQuote;

public record GameData(
        List<ExternalQuote> quotes,
        Map<String, ExternalCharacter> charactersById) {
}
