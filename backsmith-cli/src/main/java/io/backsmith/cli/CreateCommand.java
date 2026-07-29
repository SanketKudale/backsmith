package io.backsmith.cli;

import io.backsmith.adapter.spring.SpringBootAdapter;
import io.backsmith.core.ConfigurationCodec;
import io.backsmith.core.GenerationPlanner;
import io.backsmith.core.PlanApplier;
import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

@Command(
        name = "create",
        mixinStandardHelpOptions = true,
        description = "Create a new Spring Boot backend application.")
public final class CreateCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Project name.")
    String name;

    @Option(
            names = "--config",
            description = "Create from an existing Backsmith YAML configuration.")
    Path configPath;

    @Option(names = "--project", description = "Parent output directory.", defaultValue = ".")
    Path parent;

    @Option(names = "--group-id")
    String groupId;

    @Option(names = "--artifact-id")
    String artifactId;

    @Option(names = "--base-package")
    String basePackage;

    @Option(names = "--java-version", defaultValue = "21")
    int javaVersion;

    @Option(names = "--framework", defaultValue = "spring")
    String framework;

    @Option(names = "--architecture", defaultValue = "layered")
    String architecture;

    @Option(names = "--build-tool", defaultValue = "maven")
    String buildTool;

    @Option(names = "--database", defaultValue = "postgresql")
    String database;

    @Option(names = "--persistence", defaultValue = "jpa")
    String persistence;

    @Option(names = "--migration", defaultValue = "flyway")
    String migration;

    @Option(names = "--security", defaultValue = "none")
    String security;

    @Option(names = "--authorization", defaultValue = "roles_and_permissions")
    String authorization;

    @Option(names = "--messaging", defaultValue = "none")
    String messaging;

    @Option(names = "--cache", defaultValue = "none")
    String cache;

    @Option(names = "--observability", defaultValue = "standard")
    String observability;

    @Option(names = "--circuit-breaker")
    boolean circuitBreaker;

    @Option(names = "--retry")
    boolean retry;

    @Option(names = "--timeout")
    boolean timeout;

    @Option(names = "--rate-limiter")
    boolean rateLimiter;

    @Option(names = "--bulkhead")
    boolean bulkhead;

    @Option(names = "--openapi", defaultValue = "true", fallbackValue = "true")
    boolean openapi;

    @Option(names = "--docker", defaultValue = "true", fallbackValue = "true")
    boolean docker;

    @Option(names = "--tests", negatable = true, defaultValue = "true", fallbackValue = "true")
    boolean tests;

    @Option(names = "--starter-auth")
    boolean starterAuth;

    @Option(
            names = "--starter-module",
            split = ",",
            description = "Starter modules: customer,payment")
    List<String> starterModules = new ArrayList<>();

    @Option(names = "--multi-tenancy", defaultValue = "none")
    String multiTenancy;

    @Option(names = "--kubernetes")
    boolean kubernetes;

    @Option(names = "--ci", defaultValue = "true", fallbackValue = "true")
    boolean ci;

    @Option(names = "--yes", description = "Accept defaults without prompting.")
    boolean yes;

    @Option(names = "--dry-run")
    boolean dryRun;

    @Option(names = "--force")
    boolean force;

    @Option(names = "--skip-existing")
    boolean skipExisting;

    @Option(names = "--json")
    boolean json;

    @Option(names = "--quiet")
    boolean quiet;

    @Option(names = "--verbose")
    boolean verbose;

    @Override
    public Integer call() {
        try {
            ProjectConfiguration configuration;
            if (configPath != null) {
                configuration = new ConfigurationCodec().read(configPath);
                if (name != null && !name.equals(configuration.project().name())) {
                    throw new IllegalArgumentException(
                            "project name does not match --config project.name");
                }
                name = configuration.project().name();
            } else {
                if (!yes && System.console() != null) {
                    runInteractiveWizard();
                } else if (name == null) {
                    throw new ParameterException(
                            new picocli.CommandLine(this),
                            "Project name is required in non-interactive mode");
                }
                if (starterAuth && security.equalsIgnoreCase("none")) {
                    security = "jwt";
                }
                validateSelections();
                configuration = buildConfiguration();
            }
            Path target = parent.toAbsolutePath().normalize().resolve(name).normalize();
            var plan =
                    new GenerationPlanner()
                            .plan(
                                    target,
                                    new SpringBootAdapter().createProject(configuration),
                                    force,
                                    skipExisting);
            PlanPrinter.print(plan, json, quiet, new java.io.PrintWriter(System.out, true));
            if (plan.hasConflicts()) {
                return ExitCodes.CONFLICTS;
            }
            if (!dryRun) {
                new PlanApplier().apply(plan);
                if (!quiet && !json) {
                    System.out.println("Created " + target);
                }
            }
            return ExitCodes.SUCCESS;
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid configuration: " + exception.getMessage());
            return ExitCodes.INVALID_CONFIGURATION;
        } catch (Exception exception) {
            if (verbose) exception.printStackTrace(System.err);
            else System.err.println("Generation failed: " + exception.getMessage());
            return ExitCodes.GENERATION_FAILED;
        }
    }

    private void validateSelections() {
        if (!framework.equalsIgnoreCase("spring")) {
            throw new IllegalArgumentException("runtime.framework: only 'spring' is supported");
        }
        if (!buildTool.equalsIgnoreCase("maven")) {
            throw new IllegalArgumentException("runtime.build_tool: only 'maven' is supported");
        }
        if (javaVersion != 21) {
            throw new IllegalArgumentException(
                    "runtime.java_version: production adapter requires Java 21");
        }
        requireOneOf(database, "database", "postgresql");
        requireOneOf(persistence, "persistence", "jpa");
        requireOneOf(migration, "migration", "flyway");
        requireOneOf(security, "security", "none", "basic", "session", "jwt", "oauth2", "oidc");
        requireOneOf(messaging, "messaging", "none", "kafka");
        requireOneOf(cache, "cache", "none", "redis");
        requireOneOf(observability, "observability", "basic", "standard", "full");
        requireOneOf(multiTenancy, "multi-tenancy", "none", "shared-schema");
        Architecture.parse(architecture);
    }

    private ProjectConfiguration buildConfiguration() {
        var defaults = new ConfigurationCodec().defaults(name, architecture);
        String selectedArtifact = artifactId == null ? defaults.project().artifactId() : artifactId;
        String selectedGroup = groupId == null ? defaults.project().groupId() : groupId;
        String selectedPackage =
                basePackage == null ? defaults.project().basePackage() : basePackage;
        var projectConfiguration =
                new ProjectConfiguration.Project(
                        name,
                        selectedGroup,
                        selectedArtifact,
                        selectedPackage,
                        defaults.project().version(),
                        defaults.project().description());
        var runtime =
                new ProjectConfiguration.Runtime(
                        "java",
                        javaVersion,
                        framework,
                        defaults.runtime().frameworkVersion(),
                        buildTool);
        var architectureConfiguration =
                new ProjectConfiguration.ArchitectureConfiguration(
                        Architecture.parse(architecture), true);
        var features =
                new ProjectConfiguration.Features(
                        database,
                        persistence,
                        migration,
                        security,
                        messaging,
                        cache,
                        observability,
                        openapi,
                        docker,
                        tests);
        var modules = new ArrayList<String>();
        if (starterAuth) modules.add("authentication");
        starterModules.stream()
                .map(String::toLowerCase)
                .forEach(
                        module -> {
                            requireOneOf(module, "starter-module", "customer", "payment");
                            if (!modules.contains(module)) modules.add(module);
                        });
        if (modules.isEmpty()) {
            modules.add("sample");
        }
        var base =
                new ProjectConfiguration(
                        defaults.schemaVersion(),
                        projectConfiguration,
                        runtime,
                        architectureConfiguration,
                        features,
                        modules);
        var selectedSecurity =
                new ProjectConfiguration.SecurityConfiguration(
                        security, authorization, true, "auto", true, true);
        var deployment =
                new ProjectConfiguration.DeploymentConfiguration(docker, docker, kubernetes, false);
        var tenancy = new ProjectConfiguration.MultiTenancyConfiguration(multiTenancy, "header");
        var selectedResilience =
                new ProjectConfiguration.ResilienceConfiguration(
                        circuitBreaker, retry, timeout, rateLimiter, bulkhead);
        return new ProjectConfiguration(
                base.schemaVersion(),
                base.project(),
                base.runtime(),
                base.architecture(),
                base.features(),
                base.modules(),
                base.api(),
                selectedSecurity,
                base.messaging(),
                base.cache(),
                base.observability(),
                selectedResilience,
                base.testing(),
                base.generation(),
                deployment,
                tenancy);
    }

    private void runInteractiveWizard() {
        var scanner = new Scanner(System.in);
        name = prompt(scanner, "Project name", name == null ? "backend-service" : name);
        groupId = prompt(scanner, "Group ID", groupId == null ? "com.example" : groupId);
        artifactId =
                prompt(
                        scanner,
                        "Artifact ID",
                        artifactId == null ? name.toLowerCase() : artifactId);
        basePackage =
                prompt(
                        scanner,
                        "Base package",
                        basePackage == null
                                ? groupId + "." + artifactId.replaceAll("[^a-zA-Z0-9]", "")
                                : basePackage);
        architecture =
                prompt(
                        scanner,
                        "Architecture (layered/hexagonal/modular-monolith/clean/onion/cqrs/microservice)",
                        architecture);
        database = prompt(scanner, "Database", database);
        persistence = prompt(scanner, "Persistence", persistence);
        migration = prompt(scanner, "Migration tool", migration);
        security = prompt(scanner, "Authentication mode", security);
        authorization = prompt(scanner, "Authorization mode", authorization);
        messaging = prompt(scanner, "Messaging provider", messaging);
        cache = prompt(scanner, "Cache provider", cache);
        observability = prompt(scanner, "Observability level", observability);
        circuitBreaker = promptBoolean(scanner, "Enable circuit breakers", circuitBreaker);
        retry = promptBoolean(scanner, "Enable bounded retries", retry);
        timeout = promptBoolean(scanner, "Enable time limits", timeout);
        rateLimiter = promptBoolean(scanner, "Enable rate limiting", rateLimiter);
        bulkhead = promptBoolean(scanner, "Enable bulkheads", bulkhead);
        multiTenancy = prompt(scanner, "Multi-tenancy mode", multiTenancy);
        String selectedModules =
                prompt(scanner, "Starter modules (comma-separated customer,payment)", "");
        if (!selectedModules.isBlank()) {
            starterModules =
                    java.util.Arrays.stream(selectedModules.split(","))
                            .map(String::trim)
                            .filter(value -> !value.isBlank())
                            .toList();
        }
        docker = promptBoolean(scanner, "Generate Docker support", docker);
        ci = promptBoolean(scanner, "Generate CI workflow", ci);
        tests = promptBoolean(scanner, "Generate tests", tests);
        openapi = promptBoolean(scanner, "Generate OpenAPI support", openapi);
        System.out.printf(
                "%nCreate %s using %s architecture, security=%s, messaging=%s, cache=%s? [Y/n]: ",
                name, architecture, security, messaging, cache);
        String confirmation = scanner.nextLine().trim();
        if (!confirmation.isEmpty()
                && !confirmation.equalsIgnoreCase("y")
                && !confirmation.equalsIgnoreCase("yes")) {
            throw new IllegalArgumentException("creation cancelled; no files were written");
        }
    }

    private String prompt(Scanner scanner, String label, String defaultValue) {
        System.out.printf("%s [%s]: ", label, defaultValue);
        String value = scanner.nextLine().trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private boolean promptBoolean(Scanner scanner, String label, boolean defaultValue) {
        System.out.printf("%s [%s]: ", label, defaultValue ? "Y/n" : "y/N");
        String value = scanner.nextLine().trim();
        if (value.isEmpty()) return defaultValue;
        return value.equalsIgnoreCase("y") || value.equalsIgnoreCase("yes");
    }

    private void requireOneOf(String value, String path, String... supported) {
        if (java.util.Arrays.stream(supported)
                .noneMatch(option -> option.equalsIgnoreCase(value))) {
            throw new IllegalArgumentException(
                    path
                            + ": unsupported value '"
                            + value
                            + "'; expected one of "
                            + String.join(", ", supported));
        }
    }
}
