package com.wunderlore.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.mock.env.MockEnvironment;

class PersistenceAutoConfigurationFilterTest {

    private static final String[] PERSISTENCE = {
            DataSourceAutoConfiguration.class.getName(),
            HibernateJpaAutoConfiguration.class.getName(),
            DataJpaRepositoriesAutoConfiguration.class.getName(),
            FlywayAutoConfiguration.class.getName()
    };

    private static boolean[] match(MockEnvironment environment, String... classes) {
        PersistenceAutoConfigurationFilter filter = new PersistenceAutoConfigurationFilter();
        filter.setEnvironment(environment);

        return filter.match(classes, null);
    }

    @Test
    void shouldFilterEveryDatabaseAutoConfigurationByDefault() {
        assertThat(match(new MockEnvironment(), PERSISTENCE)).containsOnly(false);
    }

    @Test
    void shouldFilterThemWhenExplicitlyDisabled() {
        MockEnvironment environment = new MockEnvironment().withProperty("wunderlore.persistence.enabled", "false");

        assertThat(match(environment, PERSISTENCE)).containsOnly(false);
    }

    @Test
    void shouldKeepThemWhenEnabled() {
        MockEnvironment environment = new MockEnvironment().withProperty("wunderlore.persistence.enabled", "true");

        assertThat(match(environment, PERSISTENCE)).containsOnly(true);
    }

    @Test
    void shouldNeverFilterUnrelatedAutoConfigurations() {
        boolean[] matches = match(new MockEnvironment(),
                TransactionAutoConfiguration.class.getName(), "org.springframework.boot.webmvc.autoconfigure.X");

        assertThat(matches).containsOnly(true);
    }

    // Guards against a Boot upgrade moving the database auto-configurations to other packages,
    // which would let them start silently again.
    @Test
    void shouldHaveEveryFilteredPackageMatchAnAutoConfigurationOnTheClasspath() {
        List<String> candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();

        for (String prefix : PersistenceAutoConfigurationFilter.PERSISTENCE_PACKAGES) {
            assertThat(candidates).as("auto-configurations under " + prefix).anyMatch(c -> c.startsWith(prefix));
        }
    }

    @Test
    void shouldFilterEveryDatabaseAutoConfigurationOnTheClasspathWhenDisabled() {
        List<String> candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();

        boolean[] matches = match(new MockEnvironment(), candidates.toArray(String[]::new));

        for (int i = 0; i < matches.length; i++) {
            String name = candidates.get(i).toLowerCase();
            boolean databaseRelated = name.contains(".jdbc.") || name.contains(".hibernate.")
                    || name.contains(".jpa.") || name.contains(".flyway.");
            assertThat(matches[i]).as(candidates.get(i)).isEqualTo(!databaseRelated);
        }
    }
}
