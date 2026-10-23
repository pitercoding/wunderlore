package com.wunderlore.client.theone;

import com.wunderlore.client.theone.dto.ExternalBookResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TheOneApiClient {

    private final RestClient restClient;
    private final String accessToken;

    public TheOneApiClient(
            RestClient.Builder builder,
            @Value("${the-one-api.base-url}") String baseUrl,
            @Value("${the-one-api.access-token:}") String accessToken) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();

        this.accessToken = accessToken;
    }

    public ExternalBookResponse getBooks() {
        return restClient.get()
                .uri("/book")
                .retrieve()
                .body(ExternalBookResponse.class);
    }

    RestClient.RequestHeadersSpec<?> authenticatedGet(String uri) {
        if (accessToken.isBlank()) {
            throw new IllegalStateException(
                    "THE_ONE_API_ACCESS_TOKEN is not configured");
        }

        return restClient.get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(accessToken));
    }
}
