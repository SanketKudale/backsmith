package io.backsmith.cli;

import io.backsmith.core.ConfigurationCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;

@Command(name = "validate", description = "Validate a Backsmith-managed project.")
public final class ValidateCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".") Path project;
    @Option(names = "--compile") boolean compile;
    @Option(names = "--tests") boolean tests;
    @Option(names = "--json") boolean json;
    @Option(names = "--verbose") boolean verbose;

    @Override
    public Integer call() {
        try {
            Path config = project.resolve("backsmith.yaml");
            if (!Files.isRegularFile(config)) {
                System.err.println("backsmith.yaml not found in " + project.toAbsolutePath());
                return ExitCodes.INVALID_CONFIGURATION;
            }
            var parsed = new ConfigurationCodec().read(config);
            if (json) {
                System.out.printf("{\"valid\":true,\"project\":\"%s\",\"architecture\":\"%s\"}%n",
                        parsed.project().name(), parsed.architecture().type());
            } else {
                System.out.println("Configuration valid: " + parsed.project().name());
                System.out.println("Architecture: " + parsed.architecture().type());
            }
            if (compile || tests) {
                String wrapper = System.getProperty("os.name").toLowerCase().contains("win") ? "mvnw.cmd" : "./mvnw";
                var arguments = tests ? new String[] {wrapper, "-B", "test"} : new String[] {wrapper, "-B", "-DskipTests", "compile"};
                var process = new ProcessBuilder(arguments).directory(project.toFile()).inheritIO().start();
                if (process.waitFor() != 0) return ExitCodes.VALIDATION_FAILED;
            }
            return ExitCodes.SUCCESS;
        } catch (Exception exception) {
            if (verbose) exception.printStackTrace(System.err);
            else System.err.println("Validation failed: " + exception.getMessage());
            return ExitCodes.VALIDATION_FAILED;
        }
    }
}
