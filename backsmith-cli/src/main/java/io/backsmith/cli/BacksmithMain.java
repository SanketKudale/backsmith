package io.backsmith.cli;

import picocli.CommandLine;

public final class BacksmithMain {
    private BacksmithMain() {}

    public static void main(String[] args) {
        var root = new BacksmithCommand();
        var commandLine = new CommandLine(root);
        for (String generator :
                new String[] {
                    "module", "entity", "value-object", "usecase", "controller", "repository",
                            "service",
                    "api", "api-dir", "migration", "event", "consumer", "producer", "scheduler",
                    "integration", "docker", "ci", "docs"
                }) {
            commandLine.addSubcommand(generator, new ScaffoldCommand(generator));
        }
        commandLine.setExecutionExceptionHandler(
                (exception, command, parseResult) -> {
                    command.getErr().println("Error: " + exception.getMessage());
                    if (parseResult.hasMatchedOption("--verbose")) {
                        exception.printStackTrace(command.getErr());
                    }
                    return ExitCodes.GENERAL_ERROR;
                });
        int exitCode = commandLine.execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
