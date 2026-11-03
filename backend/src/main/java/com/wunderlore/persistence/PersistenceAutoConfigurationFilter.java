package com.wunderlore.persistence;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

// Keeps the database auto-configurations out of the application unless
// wunderlore.persistence.enabled is true. Registered in META-INF/spring.factories.
//
// The starters stay on the classpath for the future player accounts and rankings, but
// without a filter Boot would build the DataSource, Hibernate's EntityManagerFactory and
// run Flyway at startup, and each of those opens a connection. A filtered configuration
// is never loaded, so none of its beans or properties are ever created or bound: no
// connection is attempted and the datasource properties, including the DB_PASSWORD
// placeholder, are never resolved.
//
// Whole packages are filtered rather than listed class names, so a renamed or added
// configuration in these modules cannot silently slip through. PersistenceAutoConfigurationFilterTest
// fails if a prefix no longer matches any auto-configuration on the classpath.
public class PersistenceAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    static final String ENABLED_PROPERTY = "wunderlore.persistence.enabled";

    static final List<String> PERSISTENCE_PACKAGES = List.of(
            "org.springframework.boot.jdbc.",
            "org.springframework.boot.hibernate.",
            "org.springframework.boot.data.jpa.",
            "org.springframework.boot.flyway.");

    private boolean enabled;

    @Override
    public void setEnvironment(Environment environment) {
        this.enabled = environment.getProperty(ENABLED_PROPERTY, Boolean.class, false);
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];

        for (int i = 0; i < matches.length; i++) {
            matches[i] = enabled || !isPersistenceConfiguration(autoConfigurationClasses[i]);
        }

        return matches;
    }

    static boolean isPersistenceConfiguration(String className) {
        return className != null && PERSISTENCE_PACKAGES.stream().anyMatch(className::startsWith);
    }
}
