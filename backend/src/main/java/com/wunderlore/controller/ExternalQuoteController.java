package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class ExternalQuoteController {

    private final TheOneApiClient theOneApiClient;

    public ExternalQuoteController(TheOneApiClient theOneApiClient) {
        this.theOneApiClient = theOneApiClient;
    }

    @GetMapping("/quotes")
    public ExternalQuoteResponse getQuotes() {
        return theOneApiClient.getQuotes();
    }
}
