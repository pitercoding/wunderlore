package com.wunderlore.client.theone.dto;

import java.util.List;

public record ExternalCharacterResponse(
        List<ExternalCharacter> docs,
        int total,
        int limit,
        Integer offset,
        int page,
        int pages
    ) {
}