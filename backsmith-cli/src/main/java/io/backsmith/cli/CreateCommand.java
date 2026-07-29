package io.backsmith.cli;

import io.backsmith.adapter.spring.SpringBootAdapter;
import io.backsmith.core.ConfigurationCodec;
import io.backsmith.core.GenerationPlanner;
import io.backsmith.core.PlanApplier;
import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;

@Command(name = "create", description = "Create a new Spring Boot backend application.")
public final class CreateCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Project name.")
    String name;
    @Option(names = "--project", description = "Parent output directory.", defaultValue = ".")
    Path parent;
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
    @Option(names = "--messaging", defaultValue = "none")
    String messaging;
    @Option(names = "--cache", defaultValue = "none")
    String cache;
    @Option(names = "--observability", defaultValue = "standard")
    String observability;
    @Option(names = "--openapi", defaultValue = "true")
    boolean openapi;
    @Option(names = "--docker", defaultValue = "true")
    boolean docker;
    @Option(names = "--tests", negatable = true, defaultValue = "true")
    boolean tests;
    @Option(names = "--starter-auth")
    boolean starterAuth;
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
            if (name == null) {
                if (yes || System.console() == null) {
                    throw new ParameterException(new picocli.CommandLine(this), "Project name is required in non-interactive mode");
                }
                System.out.print("Project name: ");
                name = new Scanner(System.in).nextLine().trim();
            }
            validateSelections();
            var defaults = new ConfigurationCodec().defaults(name, architecture);
            var features = new ProjectConfiguration.Features(database, persistence, migration,
                    security, messaging, cache, observability, openapi, docker, tests);
            var configuration = new ProjectConfiguration(defaults.schemaVersion(), defaults.project(),
                    defaults.runtime(), defaults.architecture(), features, defaults.modules());
            Path target = parent.toAbsolutePath().normalize().resolve(name).normalize();
            var plan = new GenerationPlanner().plan(
                    target, new SpringBootAdapter().createProject(configuration), force, skipExisting);
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
            throw new IllegalArgumentException("runtime.framework: only 'spring' is supported in 0.1.0");
        }
        if (!buildTool.equalsIgnoreCase("maven")) {
            throw new IllegalArgumentException("runtime.build_tool: only 'maven' is supported in 0.1.0");
        }
        Architecture.parse(architecture);
    }
}
