package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class ExternalQuoteController {

    private final TheOneApiClient theOneApiClient;

    public ExternalQuoteController(TheOneApiClient theOneApiClient) {
        this.theOneApiClient = theOneApiClient;
    }

    @GetMapping("/quotes")
    public ExternalQuoteResponse getQuotes(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return theOneApiClient.getQuotes(page, limit);
    }
}
