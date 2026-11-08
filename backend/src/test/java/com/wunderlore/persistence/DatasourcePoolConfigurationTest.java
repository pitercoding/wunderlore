package com.wunderlore.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.postgresql.PGProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import com.zaxxer.hikari.HikariConfig;

// Reads the shipped application.properties and binds the Hikari settings the way Spring Boot does,
// without starting the application or touching a database.
class DatasourcePoolConfigurationTest {

    private static HikariConfig load() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadAllProperties("application.properties");

        return new Binder(new MapConfigurationPropertySource(properties))
                .bind("spring.datasource.hikari", HikariConfig.class)
                .get();
    }

    @Test
    void shouldLimitThePoolToFiveConnections() throws IOException {
        assertThat(load().getMaximumPoolSize()).isEqualTo(5);
    }

    @Test
    void shouldWaitTenSecondsForAConnection() throws IOException {
        assertThat(load().getConnectionTimeout()).isEqualTo(10_000);
    }

    @Test
    void shouldBoundDriverReadsWithASocketTimeoutAboveTheAcquisitionTimeout() throws IOException {
        HikariConfig config = load();
        Map<Object, Object> driver = config.getDataSourceProperties();

        // The key must be one the PostgreSQL driver knows, or it would be silently ignored.
        assertThat(driver).containsKey(PGProperty.SOCKET_TIMEOUT.getName());

        long socketTimeoutMillis = Long.parseLong(driver.get(PGProperty.SOCKET_TIMEOUT.getName()).toString()) * 1000;

        assertThat(socketTimeoutMillis).isEqualTo(30_000).isGreaterThan(config.getConnectionTimeout());
    }

    @Test
    void shouldNotOverrideTheDriverConnectTimeout() throws IOException {
        assertThat(load().getDataSourceProperties()).doesNotContainKey(PGProperty.CONNECT_TIMEOUT.getName());
    }
}
