package io.backsmith.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.backsmith.core.ProjectValidator;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Validate a Backsmith-managed project.")
public final class ValidateCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".")
    Path project;

    @Option(names = "--compile")
    boolean compile;

    @Option(names = "--tests")
    boolean tests;

    @Option(names = "--format")
    boolean format;

    @Option(names = "--json")
    boolean json;

    @Option(names = "--verbose")
    boolean verbose;

    @Override
    public Integer call() {
        try {
            var report = new ProjectValidator().validate(project);
            if (json) {
                System.out.println(new ObjectMapper().writeValueAsString(report));
            } else {
                if (report.configuration() != null) {
                    System.out.println("Project: " + report.configuration().project().name());
                    System.out.println(
                            "Architecture: " + report.configuration().architecture().type());
                }
                report.issues()
                        .forEach(
                                issue ->
                                        System.out.printf(
                                                "%-6s %-28s %-45s %s%n",
                                                issue.severity(),
                                                issue.code(),
                                                issue.path(),
                                                issue.message()));
                if (report.valid()) System.out.println("Validation passed.");
            }
            if (!report.valid()) return ExitCodes.VALIDATION_FAILED;
            if (compile || tests || format) {
                Path wrapper =
                        project.resolve(
                                        System.getProperty("os.name").toLowerCase().contains("win")
                                                ? "mvnw.cmd"
                                                : "mvnw")
                                .toAbsolutePath()
                                .normalize();
                var arguments = new java.util.ArrayList<String>();
                arguments.add(wrapper.toString());
                arguments.add("-B");
                if (format) arguments.add("spotless:check");
                if (tests) arguments.add("test");
                else if (compile) {
                    arguments.add("-DskipTests");
                    arguments.add("compile");
                }
                var process =
                        new ProcessBuilder(arguments)
                                .directory(project.toFile())
                                .inheritIO()
                                .start();
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
