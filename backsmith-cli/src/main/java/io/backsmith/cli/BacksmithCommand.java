package io.backsmith.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "backsmith",
        mixinStandardHelpOptions = true,
        version = "Backsmith 0.1.2",
        description = "Deterministic and safe backend architecture generator.",
        subcommands = {
                CreateCommand.class,
                InitCommand.class,
                DoctorCommand.class,
                ValidateCommand.class,
                UpgradeConfigCommand.class,
                DiffCommand.class
        })
public final class BacksmithCommand implements Callable<Integer> {
    @Option(names = "--verbose", description = "Show diagnostic details.")
    boolean verbose;

    @Override
    public Integer call() {
        System.out.println("Backsmith 0.1.2");
        System.out.println("Run 'backsmith --help' to see available commands.");
        return ExitCodes.SUCCESS;
    }
}
