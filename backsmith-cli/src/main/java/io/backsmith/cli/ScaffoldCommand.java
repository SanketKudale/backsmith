package io.backsmith.cli;

import io.backsmith.core.ConfigurationCodec;
import io.backsmith.core.GenerationPlanner;
import io.backsmith.core.PlanApplier;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;

@Command(description = "Generate an architecture-aware project component.")
public final class ScaffoldCommand implements Callable<Integer> {
    private final String generator;
    @Parameters(index = "0", arity = "0..1") String name;
    @Option(names = "--project", defaultValue = ".") Path project;
    @Option(names = "--module", defaultValue = "shared") String module;
    @Option(names = "--field") List<String> fields;
    @Option(names = "--type") String type;
    @Option(names = "--dry-run") boolean dryRun;
    @Option(names = "--force") boolean force;
    @Option(names = "--skip-existing") boolean skipExisting;
    @Option(names = "--tests", negatable = true, defaultValue = "true") boolean tests;
    @Option(names = "--json") boolean json;
    @Option(names = "--quiet") boolean quiet;
    @Option(names = "--verbose") boolean verbose;

    ScaffoldCommand(String generator) {
        this.generator = generator;
    }

    @Override
    public Integer call() {
        try {
            var configuration = new ConfigurationCodec().read(project.resolve("backsmith.yaml"));
            String selectedModule = generator.equals("module") && name != null ? safePackage(name) : safePackage(module);
            String componentName = name == null
                    ? capitalize(generator.replace("-", ""))
                    : generator.equals("module") ? capitalize(selectedModule) + "Module" : safeJavaName(name);
            String packageName = configuration.project().basePackage() + ".modules." + selectedModule + ".generated";
            String packagePath = packageName.replace('.', '/');
            var files = new LinkedHashMap<Path, String>();
            files.put(Path.of("src/main/java", packagePath, componentName + ".java"), javaSource(packageName, componentName));
            if (tests) {
                files.put(Path.of("src/test/java", packagePath, componentName + "Test.java"), testSource(packageName, componentName));
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

    private String javaSource(String packageName, String componentName) {
        String metadata = fields == null || fields.isEmpty() ? generator : generator + " fields: " + String.join(", ", fields);
        return """
                package %s;

                /**
                 * Backsmith-generated %s.
                 */
                public final class %s {
                    public static final String GENERATOR = "%s";
                    private %s() {}
                }
                """.formatted(packageName, metadata.replace("*/", ""), componentName, generator, componentName);
    }

    private String testSource(String packageName, String componentName) {
        return """
                package %s;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                import org.junit.jupiter.api.Test;
                class %sTest {
                    @Test void recordsGeneratorIdentity() { assertEquals("%s", %s.GENERATOR); }
                }
                """.formatted(packageName, componentName, generator, componentName);
    }

    private static String safeJavaName(String value) {
        if (!value.matches("[A-Z][A-Za-z0-9]*")) throw new IllegalArgumentException("name must be a Java type name");
        return value;
    }
    private static String safePackage(String value) {
        if (!value.matches("[a-z][a-z0-9]*")) throw new IllegalArgumentException("module must be a lowercase Java identifier");
        return value;
    }
    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
