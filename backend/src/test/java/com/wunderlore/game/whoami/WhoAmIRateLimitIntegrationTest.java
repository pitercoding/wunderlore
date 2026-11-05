package com.wunderlore.game.whoami;

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
// curated content. Needs no database and no network: Who Am I? never calls The One API.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "wunderlore.rate-limit.who-am-i-game-creation.limit=2",
                "wunderlore.rate-limit.who-am-i-game-creation.window=1h"
        })
class WhoAmIRateLimitIntegrationTest {

    private static final String GAMES_PATH = "/api/games/who-am-i/games";

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
    void shouldAnswer429AfterTheGameCreationLimitAndLeaveOtherRoutesAlone() throws Exception {
        String body = "{\"difficulty\":\"EASY\"}";

        assertThat(post(GAMES_PATH, body).statusCode()).isEqualTo(200);
        assertThat(post(GAMES_PATH, body).statusCode()).isEqualTo(200);

        HttpResponse<String> limited = post(GAMES_PATH, body);

        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).hasValueSatisfying(value ->
                assertThat(Long.parseLong(value)).isBetween(1L, 3600L));
        assertThat(limited.body()).contains("Too many game requests. Please try again later.");

        // Answering is not limited: an unknown game is a 404 from the registry, which was reached.
        for (int i = 0; i < 4; i++) {
            assertThat(post(GAMES_PATH + "/00000000-0000-4000-8000-000000000000/answer",
                    "{\"roundNumber\":1,\"clueNumber\":1,\"optionId\":\"a\"}").statusCode())
                    .isEqualTo(404);
        }
    }
}
