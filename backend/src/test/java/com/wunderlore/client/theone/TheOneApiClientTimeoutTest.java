package com.wunderlore.client.theone;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheOneApiClientTimeoutTest {

    private static final String ACCESS_TOKEN = "secret-test-token";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpClientAutoConfiguration.class,
                    ImperativeHttpClientAutoConfiguration.class,
                    RestClientAutoConfiguration.class));

    // Requests are accepted but never answered until the test releases them,
    // so the client can only finish by hitting its read timeout.
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch releaseResponse = new CountDownLatch(1);

    // /body answers 200 and writes the first chunk, then stalls until released.
    // A valid JSON prefix (exactly 10 bytes), so a JSON reader is left waiting for the rest.
    private static final byte[] FIRST_CHUNK = "{\"docs\": [".getBytes(StandardCharsets.UTF_8);
    private static final int FIRST_CHUNK_SIZE = FIRST_CHUNK.length;
    private static final int DECLARED_BODY_SIZE = 1024;
    private static final long BOUND_SECONDS = 5;

    private final CountDownLatch bodyFirstChunkSent = new CountDownLatch(1);
    private final CountDownLatch releaseBody = new CountDownLatch(1);
    private final List<ClientCall> clientCalls = new ArrayList<>();

    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void startUnresponsiveServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        server.createContext("/", exchange -> {
            requestReceived.countDown();

            try {
                releaseResponse.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        server.createContext("/body", exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, DECLARED_BODY_SIZE);
                exchange.getResponseBody().write(FIRST_CHUNK);
                exchange.getResponseBody().flush();
                bodyFirstChunkSent.countDown();

                // Bounded so a failed test can never leave this handler blocked.
                releaseBody.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // The client went away mid-body, which is exactly what is being provoked.
            } finally {
                exchange.close();
            }
        });

        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        releaseResponse.countDown();
        releaseBody.countDown();

        for (ClientCall call : clientCalls) {
            call.thread.interrupt();
            call.thread.join(TimeUnit.SECONDS.toMillis(BOUND_SECONDS));
            assertFalse(call.thread.isAlive(), "client thread must not outlive the test");
        }

        server.stop(0);
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(BOUND_SECONDS, TimeUnit.SECONDS),
                "server handler threads must terminate");
    }

    @Test
    void shouldApplyDefaultTimeoutsFromApplicationProperties() {
        contextRunner
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    HttpClientSettings settings = context.getBean(HttpClientSettings.class);

                    assertEquals(Duration.ofSeconds(5), settings.connectTimeout());
                    assertEquals(Duration.ofSeconds(10), settings.readTimeout());
                });
    }

    @Test
    void shouldUseJdkHttpClient() {
        contextRunner.run(context -> assertInstanceOf(
                JdkClientHttpRequestFactoryBuilder.class,
                context.getBean(ClientHttpRequestFactoryBuilder.class)));
    }

    @Test
    void shouldFailWithTimeoutWhenUpstreamDoesNotRespond() {
        contextRunner
                .withPropertyValues(
                        "spring.http.clients.read-timeout=200ms",
                        "the-one-api.base-url=http://"
                                + server.getAddress().getHostString()
                                + ":" + server.getAddress().getPort(),
                        "the-one-api.access-token=" + ACCESS_TOKEN)
                .withBean(TheOneApiClient.class)
                .run(context -> {
                    TheOneApiClient client = context.getBean(TheOneApiClient.class);

                    // Upper bound only guards against the test hanging; the
                    // client itself must give up after the 200ms read timeout.
                    ResourceAccessException exception = assertTimeoutPreemptively(
                            Duration.ofSeconds(5),
                            () -> assertThrows(
                                    ResourceAccessException.class,
                                    () -> client.getQuotes(1, 10)));

                    // The request reached the server, so this is a read timeout
                    // rather than a connection failure.
                    assertEquals(0, requestReceived.getCount());
                    assertTrue(hasCause(exception, HttpTimeoutException.class));
                    assertFalse(messagesContain(exception, ACCESS_TOKEN));
                });
    }

    @Test
    void shouldReleaseCallerWhenInterruptedWhileWaitingForHeaders() throws Exception {
        contextRunner
                .withPropertyValues(
                        "spring.http.clients.read-timeout=30s",
                        "the-one-api.base-url=" + baseUrl(),
                        "the-one-api.access-token=" + ACCESS_TOKEN)
                .withBean(TheOneApiClient.class)
                .run(context -> {
                    TheOneApiClient client = context.getBean(TheOneApiClient.class);
                    ClientCall call = startCall(() -> client.getQuotes(1, 10));

                    // "/" never answers, so the call is parked waiting for headers.
                    assertTrue(requestReceived.await(BOUND_SECONDS, TimeUnit.SECONDS));

                    long interruptedAt = call.interrupt();
                    call.awaitEnd();

                    System.out.println("[B2.2 headers] " + call.describe(interruptedAt));

                    assertInstanceOf(ResourceAccessException.class, call.failure);
                    assertTrue(hasCause(call.failure, IOException.class));
                    assertFalse(hasCause(call.failure, HttpTimeoutException.class),
                            "ended by the interrupt, not by a timeout");
                    assertFalse(messagesContain(call.failure, ACCESS_TOKEN));
                    // Spring restores the flag before throwing; it must reach the caller.
                    assertTrue(call.interruptedAtEnd);
                });
    }

    @Test
    void shouldReleaseCallerWhenInterruptedWhileReadingTheBody() throws Exception {
        contextRunner
                .withPropertyValues("spring.http.clients.read-timeout=30s")
                .run(context -> {
                    CountDownLatch clientConsumedFirstChunk = new CountDownLatch(1);
                    RestClient restClient = context.getBean(RestClient.Builder.class)
                            .baseUrl(baseUrl())
                            .build();

                    // The client itself reads the first chunk and only then signals, so the
                    // test knows for certain that the next read is the one that is parked.
                    ClientCall call = startCall(() -> restClient.get()
                            .uri("/body")
                            .exchange((request, response) -> {
                                InputStream body = response.getBody();

                                assertEquals(FIRST_CHUNK_SIZE, body.readNBytes(FIRST_CHUNK_SIZE).length);
                                clientConsumedFirstChunk.countDown();

                                return body.readAllBytes();
                            }));

                    assertTrue(bodyFirstChunkSent.await(BOUND_SECONDS, TimeUnit.SECONDS));
                    assertTrue(clientConsumedFirstChunk.await(BOUND_SECONDS, TimeUnit.SECONDS));

                    long interruptedAt = call.interrupt();
                    call.awaitEnd();

                    System.out.println("[B2.2 body-interrupt] " + call.describe(interruptedAt));

                    assertNotNull(call.failure, "the parked body read must fail");
                    assertFalse(hasCause(call.failure, HttpTimeoutException.class),
                            "ended by the interrupt, not by a timeout");
                });
    }

    @Test
    void shouldApplyReadTimeoutWhileReadingTheBody() throws Exception {
        contextRunner
                .withPropertyValues("spring.http.clients.read-timeout=500ms")
                .run(context -> {
                    CountDownLatch clientConsumedFirstChunk = new CountDownLatch(1);
                    RestClient restClient = context.getBean(RestClient.Builder.class)
                            .baseUrl(baseUrl())
                            .build();

                    ClientCall call = startCall(() -> restClient.get()
                            .uri("/body")
                            .exchange((request, response) -> {
                                InputStream body = response.getBody();

                                body.readNBytes(FIRST_CHUNK_SIZE);
                                clientConsumedFirstChunk.countDown();

                                return body.readAllBytes();
                            }));

                    assertTrue(clientConsumedFirstChunk.await(BOUND_SECONDS, TimeUnit.SECONDS));

                    // No interrupt: only the read timeout can end this call.
                    call.awaitEnd();

                    System.out.println("[B2.2 body-timeout] " + call.describe(call.startNanos));

                    assertNotNull(call.failure, "the stalled body read must fail");
                    assertFalse(call.interruptedAtEnd);
                    // The timer started at send, so the call lasts about the read timeout.
                    assertTrue(call.endNanos - call.startNanos >= TimeUnit.MILLISECONDS.toNanos(400));
                    assertTrue(call.endNanos - call.startNanos < TimeUnit.SECONDS.toNanos(BOUND_SECONDS));
                });
    }

    @Test
    void shouldApplyReadTimeoutToTheOneApiClientWhileReadingTheBody() throws Exception {
        contextRunner
                .withPropertyValues(
                        "spring.http.clients.read-timeout=500ms",
                        "the-one-api.base-url=" + baseUrl() + "/body",
                        "the-one-api.access-token=" + ACCESS_TOKEN)
                .withBean(TheOneApiClient.class)
                .run(context -> {
                    TheOneApiClient client = context.getBean(TheOneApiClient.class);
                    ClientCall call = startCall(() -> client.getQuotes(1, 10));

                    call.awaitEnd();

                    System.out.println("[B2.2 client-body-timeout] " + call.describe(call.startNanos));

                    assertInstanceOf(RestClientException.class, call.failure);
                    assertFalse(messagesContain(call.failure, ACCESS_TOKEN));
                    assertTrue(call.endNanos - call.startNanos < TimeUnit.SECONDS.toNanos(BOUND_SECONDS));
                });
    }

    private String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    private ClientCall startCall(Callable<?> action) {
        ClientCall call = new ClientCall(action);

        clientCalls.add(call);
        call.thread.start();

        return call;
    }

    // Runs a client call on its own thread so the test can interrupt it and measure how
    // it ends. Only proves what the Java caller thread does, not what the JDK client
    // does internally with the connection.
    private static final class ClientCall {

        final Thread thread;
        final long startNanos = System.nanoTime();
        volatile Throwable failure;
        volatile boolean interruptedAtEnd;
        volatile long endNanos;

        ClientCall(Callable<?> action) {
            this.thread = new Thread(() -> {
                try {
                    action.call();
                } catch (Throwable throwable) {
                    failure = throwable;
                } finally {
                    interruptedAtEnd = Thread.currentThread().isInterrupted();
                    endNanos = System.nanoTime();
                }
            }, "b22-client-call");
            this.thread.setDaemon(true);
        }

        long interrupt() {
            long at = System.nanoTime();

            thread.interrupt();

            return at;
        }

        void awaitEnd() throws InterruptedException {
            thread.join(TimeUnit.SECONDS.toMillis(BOUND_SECONDS));
            assertFalse(thread.isAlive(), "client thread must end within the bound");
        }

        String describe(long since) {
            StringBuilder chain = new StringBuilder();

            for (Throwable current = failure; current != null; current = current.getCause()) {
                chain.append(chain.isEmpty() ? "" : " <- ").append(current.getClass().getName());
            }

            return "terminatedAfterMs=" + TimeUnit.NANOSECONDS.toMillis(endNanos - since)
                    + " interruptFlagAtEnd=" + interruptedAtEnd
                    + " chain=" + (chain.isEmpty() ? "none" : chain);
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }

        return false;
    }

    private static boolean messagesContain(Throwable throwable, String text) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return true;
            }
        }

        return false;
    }
}
