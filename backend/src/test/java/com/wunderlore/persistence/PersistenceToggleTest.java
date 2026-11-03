package com.wunderlore.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.wunderlore.WunderloreApplication;

import jakarta.persistence.EntityManagerFactory;

// Starts the real application with a database configuration that can never work, to show
// that it is ignored while persistence is disabled and used, and reported, once enabled.
// Port 1 on the loopback is never a database, and no existing PostgreSQL is touched.
class PersistenceToggleTest {

    private static final String UNREACHABLE_URL = "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unreachable";

    // The same shape as application.properties' ${DB_PASSWORD}, with a name nothing defines.
    private static final String MISSING_PASSWORD = "spring.datasource.password=${WUNDERLORE_TEST_UNDEFINED_PASSWORD}";

    // Command-line arguments, not builder properties: the latter are defaults that
    // application.properties overrides, so they could not turn persistence on.
    private static ConfigurableApplicationContext run(String... properties) {
        String[] arguments = Stream.concat(
                        Stream.of("spring.main.banner-mode=off", "spring.datasource.username=nobody"),
                        Arrays.stream(properties))
                .map(property -> "--" + property)
                .toArray(String[]::new);

        return new SpringApplicationBuilder(WunderloreApplication.class)
                .web(WebApplicationType.NONE)
                .run(arguments);
    }

    @Test
    void shouldStartWithoutADatabaseWhenPersistenceIsDisabled() {
        // A datasource URL that would fail on any connection attempt and a password that
        // cannot be resolved: neither can matter while the database layer is left out.
        try (ConfigurableApplicationContext context = run(
                "wunderlore.persistence.enabled=false", UNREACHABLE_URL, MISSING_PASSWORD)) {

            assertThat(context.isRunning()).isTrue();
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
            assertThat(context.getBeanNamesForType(EntityManagerFactory.class)).isEmpty();
            assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
        }
    }

    @Test
    void shouldStartWithoutADatabaseWhenThePropertyIsNotSet() {
        try (ConfigurableApplicationContext context = run(UNREACHABLE_URL, MISSING_PASSWORD)) {
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        }
    }

    @Test
    void shouldFailClearlyWhenEnabledAndTheDatabaseIsUnreachable() {
        assertThatThrownBy(() -> run("wunderlore.persistence.enabled=true", UNREACHABLE_URL,
                "spring.datasource.password=unused", "spring.datasource.hikari.connection-timeout=1000").close())
                .hasStackTraceContaining("127.0.0.1:1");
    }

    @Test
    void shouldFailClearlyWhenEnabledAndThePasswordIsNotConfigured() {
        assertThatThrownBy(() -> run("wunderlore.persistence.enabled=true", UNREACHABLE_URL, MISSING_PASSWORD).close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Persistence is enabled")
                .hasMessageContaining("password is not configured")
                .hasMessageNotContaining("127.0.0.1:1 refused");
    }
}
