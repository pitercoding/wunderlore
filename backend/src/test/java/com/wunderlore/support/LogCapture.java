package com.wunderlore.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

// Records the events of one class's logger at DEBUG and above; close() restores the logger.
// The level is forced because a Spring context started earlier in the same JVM may have
// raised it to INFO.
// Events are stored in a thread-safe list: the code under test logs from worker threads while
// the test thread reads, and logback's ListAppender uses a plain ArrayList.
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final SafeListAppender appender = new SafeListAppender();

    private LogCapture(Class<?> type) {
        this.logger = (Logger) LoggerFactory.getLogger(type);
        this.previousLevel = logger.getLevel();

        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    public static LogCapture of(Class<?> type) {
        return new LogCapture(type);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public List<ILoggingEvent> events(Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .toList();
    }

    // Formatted messages plus any attached stack trace, for checking that nothing sensitive leaks.
    public String allOutput() {
        StringBuilder output = new StringBuilder();

        for (ILoggingEvent event : appender.list) {
            output.append(event.getFormattedMessage()).append('\n');

            if (event.getThrowableProxy() != null) {
                output.append(event.getThrowableProxy().getMessage()).append('\n');
            }
        }

        return output.toString();
    }

    private static final class SafeListAppender extends AppenderBase<ILoggingEvent> {

        private final List<ILoggingEvent> list = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            list.add(event);
        }
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }
}
