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
}
