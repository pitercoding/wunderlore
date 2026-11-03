package com.wunderlore.persistence;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

// Fails startup with an actionable message when persistence is enabled but the database
// password is missing. Spring Boot does not fail on a missing ${DB_PASSWORD}: its property
// binding keeps an unresolved placeholder as literal text, so the database would be asked
// to log in with "${DB_PASSWORD}" and the error would point at authentication, not at the
// missing setting. Registered in META-INF/spring.factories.
//
// Runs on ApplicationPreparedEvent: the environment is complete by then, including values
// loaded from the .env file, and no bean (DataSource, Flyway) has been created yet. Does
// nothing while persistence is disabled. The password itself is never read into a message
// or a log line, only checked.
public class PersistenceConfigurationValidator implements ApplicationListener<ApplicationPreparedEvent> {

    static final String PASSWORD_PROPERTY = "spring.datasource.password";

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        validate(event.getApplicationContext().getEnvironment());
    }

    static void validate(Environment environment) {
        if (!environment.getProperty(PersistenceAutoConfigurationFilter.ENABLED_PROPERTY, Boolean.class, false)) {
            return;
        }

        if (!hasPassword(environment)) {
            throw new IllegalStateException(
                    "Persistence is enabled (" + PersistenceAutoConfigurationFilter.ENABLED_PROPERTY
                            + "=true) but the database password is not configured. Set DB_PASSWORD to a non-empty "
                            + "value (environment variable or the root .env file), or set "
                            + PASSWORD_PROPERTY + ", or disable persistence.");
        }
    }

    private static boolean hasPassword(Environment environment) {
        String password;

        try {
            password = environment.getProperty(PASSWORD_PROPERTY);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return false;
        }

        // A placeholder that could not be resolved stays in the value as literal text.
        return password != null && !password.isBlank() && !password.contains("${");
    }
}
