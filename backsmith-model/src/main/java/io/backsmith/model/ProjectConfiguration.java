package io.backsmith.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ProjectConfiguration(
        int schemaVersion,
        Project project,
        Runtime runtime,
        ArchitectureConfiguration architecture,
        Features features,
        List<String> modules,
        ApiConfiguration api,
        SecurityConfiguration security,
        MessagingConfiguration messaging,
        CacheConfiguration cache,
        ObservabilityConfiguration observability,
        ResilienceConfiguration resilience,
        TestingConfiguration testing,
        GenerationConfiguration generation,
        DeploymentConfiguration deployment,
        MultiTenancyConfiguration multiTenancy) {

    public ProjectConfiguration {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("schema_version: only version 1 is supported");
        }
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(architecture, "architecture");
        Objects.requireNonNull(features, "features");
        modules = modules == null ? List.of() : List.copyOf(modules);
        api = api == null ? ApiConfiguration.defaults(features.openapi()) : api;
        security =
                security == null ? SecurityConfiguration.defaults(features.security()) : security;
        messaging =
                messaging == null
                        ? MessagingConfiguration.defaults(features.messaging())
                        : messaging;
        cache = cache == null ? CacheConfiguration.defaults(features.cache()) : cache;
        observability =
                observability == null
                        ? ObservabilityConfiguration.defaults(features.observability())
                        : observability;
        resilience = resilience == null ? ResilienceConfiguration.defaults() : resilience;
        testing = testing == null ? TestingConfiguration.defaults(features.tests()) : testing;
        generation =
                generation == null
                        ? GenerationConfiguration.defaults(features.tests())
                        : generation;
        deployment =
                deployment == null
                        ? DeploymentConfiguration.defaults(features.docker())
                        : deployment;
        multiTenancy = multiTenancy == null ? MultiTenancyConfiguration.defaults() : multiTenancy;
        if (modules.contains("authentication") && !security.mode().equalsIgnoreCase("jwt")) {
            throw new IllegalArgumentException(
                    "security.mode: the authentication starter requires jwt");
        }
        if (messaging.outbox() && messaging.provider().equalsIgnoreCase("none")) {
            throw new IllegalArgumentException(
                    "messaging.outbox: requires a configured messaging provider");
        }
        if (cache.enabled() && cache.provider().equalsIgnoreCase("none")) {
            throw new IllegalArgumentException("cache.enabled: requires a cache provider");
        }
        if (!Set.of("none", "shared-schema").contains(multiTenancy.mode().toLowerCase())) {
            throw new IllegalArgumentException(
                    "multi_tenancy.mode: supported values are none and shared-schema");
        }
        if (multiTenancy.mode().equalsIgnoreCase("shared-schema")
                && !Set.of("jwt", "oauth2", "oidc").contains(security.mode().toLowerCase())) {
            throw new IllegalArgumentException(
                    "multi_tenancy.mode: shared-schema requires jwt, oauth2, or oidc security");
        }
    }

    public ProjectConfiguration(
            int schemaVersion,
            Project project,
            Runtime runtime,
            ArchitectureConfiguration architecture,
            Features features,
            List<String> modules) {
        this(
                schemaVersion,
                project,
                runtime,
                architecture,
                features,
                modules,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public record Project(
            String name,
            String groupId,
            String artifactId,
            String basePackage,
            String version,
            String description) {
        public Project {
            requireIdentifier(name, "project.name");
            requirePackage(basePackage);
        }
    }

    public record Runtime(
            String language,
            int javaVersion,
            String framework,
            String frameworkVersion,
            String buildTool) {}

    public record ArchitectureConfiguration(Architecture type, boolean enforceBoundaries) {}

    public record Features(
            String database,
            String persistence,
            String migrations,
            String security,
            String messaging,
            String cache,
            String observability,
            boolean openapi,
            boolean docker,
            boolean tests) {
        private static final Set<String> DATABASES =
                Set.of("postgresql", "mysql", "mariadb", "sqlserver", "oracle", "h2", "mongodb");

        public Features {
            database = normalized(database);
            persistence = normalized(persistence);
            migrations = normalized(migrations);
            if (!DATABASES.contains(database)) {
                throw new IllegalArgumentException(
                        "features.database: supported values are "
                                + String.join(", ", DATABASES.stream().sorted().toList()));
            }
            if (database.equals("mongodb")) {
                if (!persistence.equals("mongodb")) {
                    throw new IllegalArgumentException(
                            "features.persistence: mongodb requires mongodb persistence");
                }
                if (!migrations.equals("none")) {
                    throw new IllegalArgumentException(
                            "features.migrations: mongodb uses none; Flyway is for relational databases");
                }
            } else {
                if (!persistence.equals("jpa")) {
                    throw new IllegalArgumentException(
                            "features.persistence: relational databases require jpa");
                }
                if (!migrations.equals("flyway")) {
                    throw new IllegalArgumentException(
                            "features.migrations: relational databases require flyway");
                }
            }
        }

        private static String normalized(String value) {
            return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record ApiConfiguration(
            String style,
            String basePath,
            boolean openapi,
            boolean problemDetails,
            String pagination,
            boolean idempotency) {
        static ApiConfiguration defaults(boolean enabled) {
            return new ApiConfiguration("rest", "/api/v1", enabled, true, "cursor", true);
        }
    }

    public record SecurityConfiguration(
            String mode,
            String authorization,
            boolean cors,
            String csrf,
            boolean methodSecurity,
            boolean rateLimiting) {
        static SecurityConfiguration defaults(String mode) {
            return new SecurityConfiguration(
                    mode, "roles_and_permissions", true, "auto", true, true);
        }
    }

    public record MessagingConfiguration(
            String provider, boolean outbox, boolean deadLetter, boolean idempotentConsumers) {
        static MessagingConfiguration defaults(String provider) {
            boolean enabled = provider != null && !provider.equalsIgnoreCase("none");
            return new MessagingConfiguration(provider, enabled, enabled, enabled);
        }
    }

    public record CacheConfiguration(String provider, boolean enabled) {
        static CacheConfiguration defaults(String provider) {
            return new CacheConfiguration(
                    provider, provider != null && !provider.equalsIgnoreCase("none"));
        }
    }

    public record ObservabilityConfiguration(
            String level,
            boolean structuredLogging,
            boolean metrics,
            boolean tracing,
            boolean healthChecks,
            boolean correlationId,
            boolean auditLogging) {
        static ObservabilityConfiguration defaults(String level) {
            boolean standard = level != null && !level.equalsIgnoreCase("basic");
            boolean full = level != null && level.equalsIgnoreCase("full");
            return new ObservabilityConfiguration(
                    level, standard, standard, full, true, true, full);
        }
    }

    public record ResilienceConfiguration(
            boolean circuitBreaker,
            boolean retry,
            boolean timeout,
            boolean rateLimiter,
            boolean bulkhead) {
        static ResilienceConfiguration defaults() {
            return new ResilienceConfiguration(false, false, false, false, false);
        }
    }

    public record TestingConfiguration(
            boolean unit,
            boolean integration,
            boolean architecture,
            boolean testcontainers,
            boolean contract) {
        static TestingConfiguration defaults(boolean enabled) {
            return new TestingConfiguration(enabled, enabled, enabled, enabled, enabled);
        }
    }

    public record GenerationConfiguration(
            boolean generateTests,
            boolean formatAfterGeneration,
            boolean compileAfterGeneration,
            boolean failOnWarning,
            boolean protectExistingFiles) {
        static GenerationConfiguration defaults(boolean tests) {
            return new GenerationConfiguration(tests, true, true, false, true);
        }
    }

    public record DeploymentConfiguration(
            boolean docker, boolean dockerCompose, boolean kubernetes, boolean helm) {
        static DeploymentConfiguration defaults(boolean docker) {
            return new DeploymentConfiguration(docker, docker, false, false);
        }
    }

    public record MultiTenancyConfiguration(String mode, String resolver) {
        static MultiTenancyConfiguration defaults() {
            return new MultiTenancyConfiguration("none", "header");
        }
    }

    private static void requireIdentifier(String value, String path) {
        if (value == null || !value.matches("[a-zA-Z][a-zA-Z0-9-]*")) {
            throw new IllegalArgumentException(
                    path + ": must start with a letter and contain letters, digits, or hyphens");
        }
    }

    private static void requirePackage(String value) {
        if (value == null || !value.matches("[a-zA-Z_]\\w*(\\.[a-zA-Z_]\\w*)+")) {
            throw new IllegalArgumentException(
                    "project.base_package: must be a valid dotted Java package");
        }
    }

    public static ProjectConfiguration defaults(String name, Architecture architecture) {
        var artifact = name.toLowerCase();
        var packageSuffix = artifact.replaceAll("[^a-z0-9]", "");
        return new ProjectConfiguration(
                1,
                new Project(
                        name,
                        "com.example",
                        artifact,
                        "com.example." + packageSuffix,
                        "0.1.0-SNAPSHOT",
                        name + " service"),
                new Runtime("java", 21, "spring", "3.5.16", "maven"),
                new ArchitectureConfiguration(architecture, true),
                new Features(
                        "postgresql",
                        "jpa",
                        "flyway",
                        "none",
                        "none",
                        "none",
                        "standard",
                        true,
                        true,
                        true),
                List.of("sample"));
    }
}
