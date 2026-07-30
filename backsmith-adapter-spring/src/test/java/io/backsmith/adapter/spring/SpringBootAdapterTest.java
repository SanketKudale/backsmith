package io.backsmith.adapter.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpringBootAdapterTest {
    @Test
    void rendersHexagonalProjectDeterministically() {
        var configuration = ProjectConfiguration.defaults("orders", Architecture.HEXAGONAL);
        var first = new SpringBootAdapter().createProject(configuration);
        var second = new SpringBootAdapter().createProject(configuration);
        assertEquals(first, second);
        assertTrue(first.containsKey(Path.of("src/main/java/com/example/orders/Application.java")));
        assertTrue(
                first.keySet().stream().anyMatch(path -> path.toString().contains("application")));
    }

    @ParameterizedTest
    @EnumSource(Architecture.class)
    void rendersEverySupportedArchitectureWithEnforcementTests(Architecture architecture) {
        var generated =
                new SpringBootAdapter()
                        .createProject(ProjectConfiguration.defaults("matrix", architecture));

        assertTrue(generated.containsKey(Path.of("pom.xml")));
        assertTrue(
                generated.keySet().stream()
                        .anyMatch(path -> path.endsWith("ArchitectureTest.java")));
        assertTrue(
                generated.values().stream()
                        .anyMatch(
                                source ->
                                        source.contains(
                                                "package com.example.matrix.modules.sample")));
        assertTrue(
                generated.values().stream()
                        .flatMap(source -> source.lines())
                        .noneMatch(
                                line ->
                                        !line.isEmpty()
                                                && Character.isWhitespace(
                                                        line.charAt(line.length() - 1))));
    }

    @Test
    void entityGeneratorOnlyImportsRequestedFieldTypes() {
        var generated =
                new SpringComponentGenerator()
                        .generate(
                                ProjectConfiguration.defaults("billing", Architecture.CQRS),
                                "entity",
                                "Invoice",
                                "payment",
                                List.of(
                                        "externalReference:string:required:unique",
                                        "amount:decimal:required:precision=19:scale=4"),
                                null);

        String domain =
                generated.values().stream()
                        .filter(source -> source.contains("public record Invoice("))
                        .findFirst()
                        .orElseThrow();
        String persistence =
                generated.values().stream()
                        .filter(source -> source.contains("public class InvoiceJpaEntity"))
                        .findFirst()
                        .orElseThrow();

        assertTrue(domain.contains("import java.math.BigDecimal;"));
        assertFalse(domain.contains("import java.time.Instant;"));
        assertFalse(domain.contains("import java.time.LocalDate;"));
        assertFalse(domain.contains("import java.util.List;"));
        assertFalse(domain.contains("import java.util.UUID;"));
        assertTrue(persistence.contains("import java.math.BigDecimal;"));
        assertTrue(persistence.contains("import java.util.UUID;"));
        assertFalse(persistence.contains("import java.time.Instant;"));
        assertFalse(persistence.contains("import java.time.LocalDate;"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"postgresql", "mysql", "mariadb", "sqlserver", "oracle", "h2", "mongodb"})
    void rendersEverySupportedDatabase(String database) {
        ProjectConfiguration configuration = databaseConfiguration(database);
        var generated = new SpringBootAdapter().createProject(configuration);
        String pom = generated.get(Path.of("pom.xml"));
        String application = generated.get(Path.of("src/main/resources/application.yml"));

        assertTrue(
                pom.contains(
                        database.equals("mongodb") ? "data-mongodb" : databaseDriver(database)));
        assertFalse(application.contains("{{artifactId}}"));
        assertFalse(application.contains("&#"));
        if (database.equals("mongodb")) {
            assertTrue(application.contains("mongodb:"));
            assertFalse(
                    generated.keySet().stream()
                            .anyMatch(
                                    path ->
                                            path.startsWith(
                                                    Path.of("src/main/resources/db/migration"))));
            assertTrue(
                    generated.keySet().stream()
                            .anyMatch(path -> path.endsWith("MongoDbIntegrationTest.java")));
        } else {
            assertTrue(application.contains("datasource:"));
            assertTrue(
                    generated.containsKey(
                            Path.of("src/main/resources/db/migration/V1__initial_schema.sql")));
        }
    }

    @Test
    void mongodbEntityGeneratorUsesDocumentsInsteadOfJpa() {
        var generated =
                new SpringComponentGenerator()
                        .generate(
                                databaseConfiguration("mongodb"),
                                "entity",
                                "Invoice",
                                "payment",
                                List.of(
                                        "reference:string:required",
                                        "amount:decimal:required:precision=19:scale=4"),
                                null);

        assertTrue(generated.values().stream().anyMatch(source -> source.contains("@Document(")));
        assertTrue(
                generated.values().stream().anyMatch(source -> source.contains("MongoRepository")));
        assertFalse(
                generated.values().stream()
                        .anyMatch(source -> source.contains("jakarta.persistence")));
        assertFalse(
                generated.keySet().stream()
                        .anyMatch(
                                path ->
                                        path.startsWith(
                                                Path.of("src/main/resources/db/migration"))));
    }

    @Test
    void rendersSecuredRateLimitedApiGateway() {
        ProjectConfiguration defaults =
                ProjectConfiguration.defaults("edge-service", Architecture.MICROSERVICE);
        var security =
                new ProjectConfiguration.SecurityConfiguration(
                        "jwt", "roles_and_permissions", true, "auto", true, true);
        var gateway =
                new ProjectConfiguration.ApiGatewayConfiguration(
                        true,
                        "/edge/**",
                        "http://orders:8080",
                        true,
                        true,
                        true,
                        240,
                        5_242_880,
                        16_384,
                        "10\\.0\\.0\\.0/8");
        var configuration =
                new ProjectConfiguration(
                        defaults.schemaVersion(),
                        defaults.project(),
                        defaults.runtime(),
                        defaults.architecture(),
                        defaults.features(),
                        defaults.modules(),
                        defaults.api(),
                        security,
                        defaults.messaging(),
                        defaults.cache(),
                        defaults.observability(),
                        defaults.resilience(),
                        defaults.testing(),
                        defaults.generation(),
                        defaults.deployment(),
                        defaults.multiTenancy(),
                        gateway);

        var generated = new SpringBootAdapter().createProject(configuration);
        String pom = generated.get(Path.of("pom.xml"));
        String application = generated.get(Path.of("src/main/resources/application.yml"));
        String route =
                generated.get(
                        Path.of(
                                "src/main/java/com/example/edgeservice/shared/gateway/ApiGatewayConfiguration.java"));
        String securityConfiguration =
                generated.get(
                        Path.of(
                                "src/main/java/com/example/edgeservice/shared/security/SecurityConfiguration.java"));

        assertTrue(pom.contains("spring-cloud-starter-gateway-server-webmvc"));
        assertTrue(pom.contains("bucket4j_jdk17-caffeine"));
        assertTrue(application.contains("GATEWAY_UPSTREAM_URI:http://orders:8080"));
        assertTrue(application.contains("GATEWAY_REQUESTS_PER_MINUTE:240"));
        assertTrue(route.contains(".setCapacity(properties.requestsPerMinute())"));
        assertTrue(route.contains(".before(removeRequestHeader(\"Cookie\"))"));
        assertTrue(securityConfiguration.contains(".anyRequest().authenticated()"));
        assertTrue(
                generated.containsKey(
                        Path.of(
                                "src/test/java/com/example/edgeservice/shared/gateway/GatewaySecurityHeadersFilterTest.java")));
    }

    private ProjectConfiguration databaseConfiguration(String database) {
        ProjectConfiguration defaults =
                ProjectConfiguration.defaults("database-matrix", Architecture.LAYERED);
        boolean mongo = database.equals("mongodb");
        var features =
                new ProjectConfiguration.Features(
                        database,
                        mongo ? "mongodb" : "jpa",
                        mongo ? "none" : "flyway",
                        "none",
                        "none",
                        "none",
                        "standard",
                        true,
                        true,
                        true);
        return new ProjectConfiguration(
                defaults.schemaVersion(),
                defaults.project(),
                defaults.runtime(),
                defaults.architecture(),
                features,
                defaults.modules());
    }

    private String databaseDriver(String database) {
        return switch (database) {
            case "postgresql" -> "org.postgresql";
            case "mysql" -> "mysql-connector-j";
            case "mariadb" -> "mariadb-java-client";
            case "sqlserver" -> "mssql-jdbc";
            case "oracle" -> "ojdbc11";
            case "h2" -> "<artifactId>h2</artifactId>";
            default -> throw new IllegalArgumentException(database);
        };
    }
}
