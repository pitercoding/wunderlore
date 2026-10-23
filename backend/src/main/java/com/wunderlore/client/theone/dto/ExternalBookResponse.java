package com.wunderlore.client.theone.dto;

import java.util.List;

public record ExternalBookResponse(
        List<ExternalBook> docs,
        int total,
        int limit,
        int offset,
        int page,
        int pages
    ) {
}
