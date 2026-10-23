package com.wunderlore.client.theone;

import com.wunderlore.client.theone.dto.ExternalBookResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TheOneApiClient {

    private final RestClient restClient;

    public TheOneApiClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://the-one-api.dev/v2")
                .build();
    }

    public ExternalBookResponse getBooks() {
        return restClient.get()
                .uri("/book")
                .retrieve()
                .body(ExternalBookResponse.class);
    }
}
