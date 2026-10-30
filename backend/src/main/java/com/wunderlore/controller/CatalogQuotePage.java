package com.wunderlore.controller;

import java.util.List;

public record CatalogQuotePage(
        List<CatalogQuote> docs,
        int total,
        int limit,
        Integer offset,
        int page,
        int pages
) {
}
