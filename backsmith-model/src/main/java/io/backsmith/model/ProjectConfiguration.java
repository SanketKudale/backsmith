package io.backsmith.model;

import java.util.List;
import java.util.Objects;

public record ProjectConfiguration(
        int schemaVersion,
        Project project,
        Runtime runtime,
        ArchitectureConfiguration architecture,
        Features features,
        List<String> modules) {

    public ProjectConfiguration {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("schema_version: only version 1 is supported");
        }
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(architecture, "architecture");
        Objects.requireNonNull(features, "features");
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    public record Project(String name, String groupId, String artifactId, String basePackage,
                          String version, String description) {
        public Project {
            requireIdentifier(name, "project.name");
            requirePackage(basePackage);
        }
    }

    public record Runtime(String language, int javaVersion, String framework, String frameworkVersion,
                          String buildTool) {}

    public record ArchitectureConfiguration(Architecture type, boolean enforceBoundaries) {}

    public record Features(String database, String persistence, String migrations, String security,
                           String messaging, String cache, String observability, boolean openapi,
                           boolean docker, boolean tests) {}

    private static void requireIdentifier(String value, String path) {
        if (value == null || !value.matches("[a-zA-Z][a-zA-Z0-9-]*")) {
            throw new IllegalArgumentException(path + ": must start with a letter and contain letters, digits, or hyphens");
        }
    }

    private static void requirePackage(String value) {
        if (value == null || !value.matches("[a-zA-Z_]\\w*(\\.[a-zA-Z_]\\w*)+")) {
            throw new IllegalArgumentException("project.base_package: must be a valid dotted Java package");
        }
    }

    public static ProjectConfiguration defaults(String name, Architecture architecture) {
        var artifact = name.toLowerCase();
        var packageSuffix = artifact.replaceAll("[^a-z0-9]", "");
        return new ProjectConfiguration(
                1,
                new Project(name, "com.example", artifact, "com.example." + packageSuffix,
                        "0.1.0-SNAPSHOT", name + " service"),
                new Runtime("java", 21, "spring", "3.5.4", "maven"),
                new ArchitectureConfiguration(architecture, true),
                new Features("postgresql", "jpa", "flyway", "none", "none", "none",
                        "standard", true, true, true),
                List.of("sample"));
    }
}
