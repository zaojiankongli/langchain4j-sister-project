package com.zjkl.common.config.properties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthPropertiesValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void authProperties_shouldRejectPlaceholderSecret() {
        AuthProperties properties = validProperties();
        properties.setSecret("change-me-in-production");

        assertFalse(validator.validate(properties).isEmpty(), "JWT placeholder secret must fail validation");
    }

    @Test
    void authProperties_shouldRejectShortSecret() {
        AuthProperties properties = validProperties();
        properties.setSecret("short-secret");

        assertFalse(validator.validate(properties).isEmpty(), "JWT secret shorter than 32 bytes must fail validation");
    }

    private AuthProperties validProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef");
        properties.setAccessTokenExpiration(7_200_000L);
        properties.setRefreshTokenExpiration(604_800_000L);
        return properties;
    }
}
