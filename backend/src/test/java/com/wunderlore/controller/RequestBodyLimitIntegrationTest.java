package com.wunderlore.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against the real embedded Tomcat, so a body can be sent with a declared Content-Length
// or chunked, with no length the server can trust. Needs the same PostgreSQL as the context test.
// The target is the Who Said It answer endpoint with a round id that was never issued: a body
// that gets past the limit is a 400 (invalid) or a 404 (unknown round), never a 413.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestBodyLimitIntegrationTest {

    // The default limit: 4 KB.
    private static final int LIMIT = 4096;

    private static final String ANSWER_PATH =
            "/api/games/who-said-it/rounds/00000000-0000-4000-8000-000000000000/answer";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    // {"characterId":"aaa..."} with exactly the given size in bytes.
    private static byte[] jsonBody(int totalBytes) {
        String prefix = "{\"characterId\":\"";
        String suffix = "\"}";
        int padding = totalBytes - prefix.length() - suffix.length();

        return (prefix + "a".repeat(padding) + suffix).getBytes(StandardCharsets.UTF_8);
    }

    // Content-Length is declared.
    private static BodyPublisher declaredLength(byte[] body) {
        return BodyPublishers.ofByteArray(body);
    }

    // No length is declared, so the request is sent with chunked transfer encoding.
    private static BodyPublisher chunked(byte[] body) {
        return BodyPublishers.fromPublisher(BodyPublishers.ofByteArray(body));
    }

    private HttpResponse<String> post(BodyPublisher body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ANSWER_PATH))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void shouldLetASmallBodyThroughWithADeclaredLength() throws Exception {
        HttpResponse<String> response = post(declaredLength(jsonBody(60)));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Round not found or expired");
    }

    @Test
    void shouldLetASmallChunkedBodyThroughAndStillReadItInTheController() throws Exception {
        // A 404 about the round means the controller parsed the body that was read ahead.
        HttpResponse<String> response = post(chunked(jsonBody(60)));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Round not found or expired");
    }

    @Test
    void shouldLetABodyOfExactlyTheLimitReachValidation() throws Exception {
        // Past the size limit and into validation, where the oversized id is a 400.
        assertThat(post(declaredLength(jsonBody(LIMIT))).statusCode()).isEqualTo(400);
        assertThat(post(chunked(jsonBody(LIMIT))).statusCode()).isEqualTo(400);
    }

    @Test
    void shouldRefuseABodyOneByteOverTheLimitWithADeclaredLength() throws Exception {
        HttpResponse<String> response = post(declaredLength(jsonBody(LIMIT + 1)));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).doesNotContain("Round not found");
    }

    @Test
    void shouldRefuseABodyOneByteOverTheLimitWithoutADeclaredLength() throws Exception {
        HttpResponse<String> response = post(chunked(jsonBody(LIMIT + 1)));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).doesNotContain("Round not found");
    }

    @Test
    void shouldRefuseAMuchLargerBodyEitherWay() throws Exception {
        byte[] large = jsonBody(64 * 1024);

        assertThat(post(declaredLength(large)).statusCode()).isEqualTo(413);
        assertThat(post(chunked(large)).statusCode()).isEqualTo(413);
    }

    @Test
    void shouldNotAffectRequestsWithoutABody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
