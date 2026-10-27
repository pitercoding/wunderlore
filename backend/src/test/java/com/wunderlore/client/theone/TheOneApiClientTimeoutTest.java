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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        releaseResponse.countDown();
        server.stop(0);
        executor.shutdownNow();
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
