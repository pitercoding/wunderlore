package com.wunderlore.game.whosaidit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// The whole application on the real embedded Tomcat, with the real filter chain, the real
// controller and the real registry. Only WhoSaidItService is a mock, so creating a round never
// waits on The One API. Needs no database.
class RoundCreationRateLimitIntegrationTest {

    private static final String ROUNDS_PATH = "/api/games/who-said-it/rounds";

    private static final String ANSWER_PATH =
            ROUNDS_PATH + "/00000000-0000-4000-8000-000000000000/answer";

    private static final WhoSaidItRound ROUND = new WhoSaidItRound(
            "Fly, you fools!",
            List.of(
                    new WhoSaidItRound.CharacterOption("gandalf", "Gandalf"),
                    new WhoSaidItRound.CharacterOption("frodo", "Frodo Baggins")),
            "gandalf");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static HttpResponse<String> send(int port, String method, String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));

        if (method.equals("POST") && path.endsWith("/answer")) {
            builder.header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString("{\"characterId\":\"gandalf\"}"));
        } else if (method.equals("POST")) {
            builder.POST(BodyPublishers.noBody());
        } else {
            builder.GET();
        }

        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // A one-hour window, so no real time can pass inside a test and free a slot.
    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "wunderlore.rate-limit.round-creation.limit=2",
                    "wunderlore.rate-limit.round-creation.window=1h"
            })
    class Enabled {

        @Value("${local.server.port}")
        private int port;

        @MockitoBean
        private WhoSaidItService whoSaidItService;

        @BeforeEach
        void stubService() {
            when(whoSaidItService.generateRound()).thenReturn(ROUND);
        }

        @Test
        void shouldAnswer429AfterTheLimitWithRetryAfter() throws Exception {
            assertThat(send(port, "POST", ROUNDS_PATH).statusCode()).isEqualTo(200);
            assertThat(send(port, "POST", ROUNDS_PATH).statusCode()).isEqualTo(200);

            HttpResponse<String> limited = send(port, "POST", ROUNDS_PATH);

            assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(limited.headers().firstValue("Retry-After")).hasValueSatisfying(value ->
                    assertThat(Long.parseLong(value)).isBetween(1L, 3600L));
            assertThat(limited.headers().firstValue("Content-Type"))
                    .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
            assertThat(limited.body()).contains("Too many round requests. Please try again later.");

            // Still limited, and the ignored forwarding header changes nothing.
            HttpRequest spoofed = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ROUNDS_PATH))
                    .header("X-Forwarded-For", "203.0.113.7")
                    .POST(BodyPublishers.noBody())
                    .build();

            assertThat(CLIENT.send(spoofed, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .isEqualTo(429);
        }

        @Test
        void shouldLeaveAnswersAndOtherRoutesAlone() throws Exception {
            // Whatever order the tests run in, the quota of two is gone after this.
            for (int i = 0; i < 3; i++) {
                send(port, "POST", ROUNDS_PATH);
            }

            assertThat(send(port, "POST", ROUNDS_PATH).statusCode()).isEqualTo(429);

            for (int i = 0; i < 5; i++) {
                // An unknown round: a 404 from the registry, which was reached.
                assertThat(send(port, "POST", ANSWER_PATH).statusCode()).isEqualTo(404);
            }

            assertThat(send(port, "GET", "/health").statusCode()).isEqualTo(200);
        }
    }

    @Nested
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "wunderlore.rate-limit.round-creation.enabled=false",
                    "wunderlore.rate-limit.round-creation.limit=1",
                    "wunderlore.rate-limit.round-creation.window=1h"
            })
    class Disabled {

        @Value("${local.server.port}")
        private int port;

        @MockitoBean
        private WhoSaidItService whoSaidItService;

        @BeforeEach
        void stubService() {
            when(whoSaidItService.generateRound()).thenReturn(ROUND);
        }

        @Test
        void shouldNeverAnswer429WhenDisabled() throws Exception {
            for (int i = 0; i < 5; i++) {
                assertThat(send(port, "POST", ROUNDS_PATH).statusCode()).isEqualTo(200);
            }
        }
    }
}
