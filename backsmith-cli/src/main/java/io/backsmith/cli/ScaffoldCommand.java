package io.backsmith.cli;

import io.backsmith.adapter.spring.SpringComponentGenerator;
import io.backsmith.adapter.spring.SpringOpenApiGenerator;
import io.backsmith.core.ConfigurationCodec;
import io.backsmith.core.GenerationPlanner;
import io.backsmith.core.PlanApplier;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        mixinStandardHelpOptions = true,
        description = "Generate an architecture-aware project component.")
public final class ScaffoldCommand implements Callable<Integer> {
    private final String generator;

    @Parameters(index = "0", arity = "0..1")
    String name;

    @Option(names = "--project", defaultValue = ".")
    Path project;

    @Option(names = "--module", defaultValue = "shared")
    String module;

    @Option(names = "--field")
    List<String> fields;

    @Option(names = "--type")
    String type;

    @Option(names = "--dry-run")
    boolean dryRun;

    @Option(names = "--force")
    boolean force;

    @Option(names = "--skip-existing")
    boolean skipExisting;

    @Option(names = "--tests", negatable = true, defaultValue = "true", fallbackValue = "true")
    boolean tests;

    @Option(names = "--json")
    boolean json;

    @Option(names = "--quiet")
    boolean quiet;

    @Option(names = "--verbose")
    boolean verbose;

    ScaffoldCommand(String generator) {
        this.generator = generator;
    }

    @Override
    public Integer call() {
        try {
            var codec = new ConfigurationCodec();
            var configuration = codec.read(project.resolve("backsmith.yaml"));
            String selectedModule =
                    generator.equals("module") && name != null
                            ? safePackage(name)
                            : safePackage(module);
            if (generator.equals("module") && !configuration.modules().contains(selectedModule)) {
                var modules = new java.util.ArrayList<>(configuration.modules());
                modules.add(selectedModule);
                modules.sort(String::compareTo);
                configuration = withModules(configuration, modules);
            }
            String componentName = generator.equals("module") ? null : name;
            java.util.Map<Path, String> files;
            if (generator.equals("api") || generator.equals("api-dir")) {
                if (name == null)
                    throw new IllegalArgumentException(generator + " requires a contract path");
                var contractGenerator = new SpringOpenApiGenerator();
                files =
                        generator.equals("api")
                                ? contractGenerator.generate(
                                        configuration, project, Path.of(name), selectedModule)
                                : contractGenerator.generateDirectory(
                                        configuration, project, Path.of(name), selectedModule);
            } else {
                files =
                        new SpringComponentGenerator()
                                .generate(
                                        configuration,
                                        generator,
                                        componentName,
                                        selectedModule,
                                        fields,
                                        type);
            }
            if (generator.equals("module")) {
                var expanded = new java.util.LinkedHashMap<>(files);
                expanded.put(
                        Path.of("backsmith.yaml"),
                        codec.addModule(project.resolve("backsmith.yaml"), selectedModule));
                files = expanded;
            }
            if (!tests) {
                files =
                        files.entrySet().stream()
                                .filter(entry -> !entry.getKey().startsWith("src/test"))
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                java.util.Map.Entry::getKey,
                                                java.util.Map.Entry::getValue,
                                                (left, right) -> left,
                                                java.util.LinkedHashMap::new));
            }
            var plan = new GenerationPlanner().plan(project, files, force, skipExisting);
            PlanPrinter.print(plan, json, quiet, new java.io.PrintWriter(System.out, true));
            if (plan.hasConflicts()) return ExitCodes.CONFLICTS;
            if (!dryRun) new PlanApplier().apply(plan);
            return ExitCodes.SUCCESS;
        } catch (Exception exception) {
            if (verbose) exception.printStackTrace(System.err);
            else System.err.println("Generation failed: " + exception.getMessage());
            return ExitCodes.GENERATION_FAILED;
        }
    }

    private static String safePackage(String value) {
        if (!value.matches("[a-z][a-z0-9]*"))
            throw new IllegalArgumentException("module must be a lowercase Java identifier");
        return value;
    }

    private static io.backsmith.model.ProjectConfiguration withModules(
            io.backsmith.model.ProjectConfiguration configuration, List<String> modules) {
        return new io.backsmith.model.ProjectConfiguration(
                configuration.schemaVersion(),
                configuration.project(),
                configuration.runtime(),
                configuration.architecture(),
                configuration.features(),
                modules,
                configuration.api(),
                configuration.security(),
                configuration.messaging(),
                configuration.cache(),
                configuration.observability(),
                configuration.resilience(),
                configuration.testing(),
                configuration.generation(),
                configuration.deployment(),
                configuration.multiTenancy(),
                configuration.apiGateway());
    }
}
