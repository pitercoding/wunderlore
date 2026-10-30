package com.wunderlore.lore;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Starts loading the lore data once the application is ready, so the first request
// does not pay for the full load. The load runs on Spring Boot's application task
// executor, which the context shuts down, and goes through LoreDataService.getData(),
// so a request arriving meanwhile waits on this load instead of starting another.
@Component
public class LoreDataPreloader {

    private static final Logger log = LoggerFactory.getLogger(LoreDataPreloader.class);

    private final LoreDataService loreDataService;
    private final Executor executor;

    public LoreDataPreloader(
            LoreDataService loreDataService,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.loreDataService = loreDataService;
        this.executor = executor;
    }

    // Returns as soon as the load is scheduled; it never waits for The One API
    // and never throws, so a failed preload cannot stop the application.
    @EventListener(ApplicationReadyEvent.class)
    public void preload() {
        try {
            executor.execute(this::load);
        } catch (RejectedExecutionException exception) {
            log.debug("Lore data preload was not scheduled: {}", exception.getClass().getName());
        }
    }

    private void load() {
        try {
            loreDataService.getData();
        } catch (RuntimeException exception) {
            // Upstream failures are already logged at WARN by LoreDataService, and a
            // missing token by TheOneApiClient at startup. The next request retries
            // once the cooldown has expired.
            log.debug("Lore data preload failed: {}", exception.getClass().getName());
        }
    }
}
