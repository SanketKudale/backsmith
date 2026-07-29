package io.backsmith.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;

@Command(name = "diff", description = "Preview regeneration differences without writing.")
public final class DiffCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".") Path project;
    @Override public Integer call() {
        System.out.println("Use generator commands with --dry-run for an operation-level diff.");
        System.out.println("Project: " + project.toAbsolutePath().normalize());
        return ExitCodes.SUCCESS;
    }
}
