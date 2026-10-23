package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalBookResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class ExternalBookController {

    private final TheOneApiClient theOneApiClient;

    public ExternalBookController(TheOneApiClient theOneApiClient) {
        this.theOneApiClient = theOneApiClient;
    }

    @GetMapping("/books")
    public ExternalBookResponse getBooks() {
        return theOneApiClient.getBooks();
    }
}
