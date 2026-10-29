package com.wunderlore.client.theone;

import com.wunderlore.client.theone.dto.ExternalBookResponse;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TheOneApiClient {

    private static final Logger log = LoggerFactory.getLogger(TheOneApiClient.class);

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

        // Logged once at startup instead of per request, since a missing token
        // fails every authenticated call without starting a cooldown.
        if (accessToken.isBlank()) {
            log.warn("THE_ONE_API_ACCESS_TOKEN is not configured; quote and character requests will fail");
        }
    }

    public ExternalBookResponse getBooks() {
        return restClient.get()
                .uri("/book")
                .retrieve()
                .body(ExternalBookResponse.class);
    }

    public ExternalQuoteResponse getQuotes(int page, int limit) {
        return authenticatedGet("/quote?page={page}&limit={limit}", page, limit)
                .retrieve()
                .body(ExternalQuoteResponse.class);
    }

    public ExternalCharacterResponse getCharacters(int page, int limit) {
        return authenticatedGet("/character?page={page}&limit={limit}", page, limit)
            .retrieve()
            .body(ExternalCharacterResponse.class);
    }

    RestClient.RequestHeadersSpec<?> authenticatedGet(
            String uri,
            Object... uriVariables) {
        if (accessToken.isBlank()) {
            throw new TheOneApiNotConfiguredException();
        }

        return restClient.get()
                .uri(uri, uriVariables)
                .headers(headers -> headers.setBearerAuth(accessToken));
    }
}
