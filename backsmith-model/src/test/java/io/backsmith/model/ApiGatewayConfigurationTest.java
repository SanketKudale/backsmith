package io.backsmith.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApiGatewayConfigurationTest {
    @Test
    void acceptsAConstrainedGatewayOrigin() {
        assertDoesNotThrow(
                () ->
                        new ProjectConfiguration.ApiGatewayConfiguration(
                                true,
                                "/gateway/**",
                                "https://orders.internal:8443",
                                true,
                                true,
                                true,
                                120,
                                10_485_760,
                                16_384,
                                "10\\..*"));
    }

    @Test
    void rejectsCredentialsInUpstreamUri() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProjectConfiguration.ApiGatewayConfiguration(
                                true,
                                "/gateway/**",
                                "https://user:secret@example.com",
                                true,
                                true,
                                true,
                                120,
                                10_485_760,
                                16_384,
                                "127\\.0\\.0\\.1"));
    }

    @Test
    void rejectsUnsafeRouteAndLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProjectConfiguration.ApiGatewayConfiguration(
                                true,
                                "/**",
                                "https://example.com",
                                true,
                                false,
                                true,
                                0,
                                0,
                                0,
                                "127\\.0\\.0\\.1"));
    }

    @Test
    void authenticatedGatewayRequiresSecurityMode() {
        ProjectConfiguration defaults =
                ProjectConfiguration.defaults("gateway", Architecture.MICROSERVICE);
        var gateway =
                new ProjectConfiguration.ApiGatewayConfiguration(
                        true,
                        "/gateway/**",
                        "http://localhost:8081",
                        true,
                        true,
                        true,
                        120,
                        10_485_760,
                        16_384,
                        "127\\.0\\.0\\.1");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProjectConfiguration(
                                defaults.schemaVersion(),
                                defaults.project(),
                                defaults.runtime(),
                                defaults.architecture(),
                                defaults.features(),
                                defaults.modules(),
                                defaults.api(),
                                defaults.security(),
                                defaults.messaging(),
                                defaults.cache(),
                                defaults.observability(),
                                defaults.resilience(),
                                defaults.testing(),
                                defaults.generation(),
                                defaults.deployment(),
                                defaults.multiTenancy(),
                                gateway));
    }
}
