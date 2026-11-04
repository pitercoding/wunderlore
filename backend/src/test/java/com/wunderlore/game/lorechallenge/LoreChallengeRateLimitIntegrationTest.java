package com.wunderlore.game.lorechallenge;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

// The whole application on the real embedded Tomcat, with the real filter chain and the real
// curated content. Needs no database and no network: Lore Challenge never calls The One API.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "wunderlore.rate-limit.match-creation.limit=2",
                "wunderlore.rate-limit.match-creation.window=1h"
        })
class LoreChallengeRateLimitIntegrationTest {

    private static final String MATCHES_PATH = "/api/games/lore-challenge/matches";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> post(String path, String body) throws Exception {
        return CLIENT.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void shouldAnswer429AfterTheMatchCreationLimitAndLeaveOtherRoutesAlone() throws Exception {
        String body = "{\"category\":\"ALL\",\"difficulty\":\"EASY\"}";

        assertThat(post(MATCHES_PATH, body).statusCode()).isEqualTo(200);
        assertThat(post(MATCHES_PATH, body).statusCode()).isEqualTo(200);

        HttpResponse<String> limited = post(MATCHES_PATH, body);

        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).hasValueSatisfying(value ->
                assertThat(Long.parseLong(value)).isBetween(1L, 3600L));
        assertThat(limited.body()).contains("Too many match requests. Please try again later.");

        // Answering is not limited: an unknown match is a 404 from the registry, which was reached.
        for (int i = 0; i < 4; i++) {
            assertThat(post(MATCHES_PATH + "/00000000-0000-4000-8000-000000000000/answer",
                    "{\"questionId\":\"characters-easy-01\",\"optionId\":\"a\"}").statusCode())
                    .isEqualTo(404);
        }
    }
}
