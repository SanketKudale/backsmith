package io.backsmith.cli;

import io.backsmith.core.ConfigurationCodec;
import io.backsmith.core.GenerationPlanner;
import io.backsmith.core.PlanApplier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "init", description = "Initialize configuration for an existing application.")
public final class InitCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".")
    Path project;

    @Option(names = "--name")
    String name;

    @Option(names = "--architecture", defaultValue = "layered")
    String architecture;

    @Option(names = "--force")
    boolean force;

    @Override
    public Integer call() throws Exception {
        Path config = project.resolve("backsmith.yaml");
        if (Files.exists(config) && !force) {
            System.err.println("Conflict: backsmith.yaml already exists (use --force to replace)");
            return ExitCodes.CONFLICTS;
        }
        String projectName =
                name == null ? project.toAbsolutePath().getFileName().toString() : name;
        var codec = new ConfigurationCodec();
        var content = codec.write(codec.defaults(projectName, architecture));
        var plan =
                new GenerationPlanner()
                        .plan(
                                project,
                                java.util.Map.of(Path.of("backsmith.yaml"), content),
                                force,
                                false);
        new PlanApplier().apply(plan);
        System.out.println("Created " + config);
        return ExitCodes.SUCCESS;
    }
}
