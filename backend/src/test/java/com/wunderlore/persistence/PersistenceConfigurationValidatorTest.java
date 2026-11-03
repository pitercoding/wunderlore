package com.wunderlore.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PersistenceConfigurationValidatorTest {

    private static MockEnvironment enabled() {
        return new MockEnvironment().withProperty("wunderlore.persistence.enabled", "true");
    }

    @Test
    void shouldFailWithAnActionableMessageWhenEnabledAndThePasswordIsMissing() {
        assertThatThrownBy(() -> PersistenceConfigurationValidator.validate(enabled()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Persistence is enabled")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void shouldFailWhenEnabledAndThePasswordIsEmptyOrBlank() {
        assertThatThrownBy(() -> PersistenceConfigurationValidator.validate(
                enabled().withProperty("spring.datasource.password", "")))
                .hasMessageContaining("DB_PASSWORD");
        assertThatThrownBy(() -> PersistenceConfigurationValidator.validate(
                enabled().withProperty("spring.datasource.password", "   ")))
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void shouldFailWhenEnabledAndThePasswordIsAnUnresolvedPlaceholder() {
        assertThatThrownBy(() -> PersistenceConfigurationValidator.validate(
                enabled().withProperty("spring.datasource.password", "${DB_PASSWORD}")))
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void shouldNeverPutThePasswordInTheMessage() {
        // An unresolved placeholder embedded in a larger value stands in for a secret here.
        assertThatThrownBy(() -> PersistenceConfigurationValidator.validate(
                enabled().withProperty("spring.datasource.password", "hunter2-${MISSING}")))
                .hasMessageNotContaining("hunter2");
    }

    @Test
    void shouldAcceptAConfiguredPasswordWhenEnabled() {
        assertThatCode(() -> PersistenceConfigurationValidator.validate(
                enabled().withProperty("spring.datasource.password", "a-password")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldIgnoreAMissingOrUnresolvedPasswordWhenDisabledOrUnset() {
        assertThatCode(() -> PersistenceConfigurationValidator.validate(new MockEnvironment()))
                .doesNotThrowAnyException();
        assertThatCode(() -> PersistenceConfigurationValidator.validate(new MockEnvironment()
                .withProperty("wunderlore.persistence.enabled", "false")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}")))
                .doesNotThrowAnyException();
    }
}
