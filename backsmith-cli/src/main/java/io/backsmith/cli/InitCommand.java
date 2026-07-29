package io.backsmith.cli;

import io.backsmith.core.ConfigurationCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;

@Command(name = "init", description = "Initialize configuration for an existing application.")
public final class InitCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".") Path project;
    @Option(names = "--name") String name;
    @Option(names = "--architecture", defaultValue = "layered") String architecture;
    @Option(names = "--force") boolean force;

    @Override
    public Integer call() throws Exception {
        Path config = project.resolve("backsmith.yaml");
        if (Files.exists(config) && !force) {
            System.err.println("Conflict: backsmith.yaml already exists (use --force to replace)");
            return ExitCodes.CONFLICTS;
        }
        String projectName = name == null ? project.toAbsolutePath().getFileName().toString() : name;
        Files.createDirectories(project);
        Files.writeString(config, new ConfigurationCodec().write(new ConfigurationCodec().defaults(projectName, architecture)));
        System.out.println("Created " + config);
        return ExitCodes.SUCCESS;
    }
}
