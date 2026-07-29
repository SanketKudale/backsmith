package io.backsmith.cli;

import io.backsmith.adapter.spring.SpringBootAdapter;
import io.backsmith.core.ConfigurationCodec;
import io.backsmith.core.GenerationPlanner;
import io.backsmith.model.OperationType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "diff",
        mixinStandardHelpOptions = true,
        description = "Preview regeneration differences without writing.")
public final class DiffCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".")
    Path project;

    @Option(names = "--json")
    boolean json;

    @Option(names = "--unified", description = "Include compact unified text diffs.")
    boolean unified;

    @Override
    public Integer call() {
        try {
            var configuration = new ConfigurationCodec().read(project.resolve("backsmith.yaml"));
            var plan =
                    new GenerationPlanner()
                            .plan(
                                    project,
                                    new SpringBootAdapter().createProject(configuration),
                                    false,
                                    false);
            PlanPrinter.print(plan, json, false, new java.io.PrintWriter(System.out, true));
            if (unified && !json) {
                plan.files().stream()
                        .filter(
                                file ->
                                        file.operation() == OperationType.UPDATE
                                                || file.operation() == OperationType.CONFLICT)
                        .forEach(this::printDiff);
            }
            return plan.hasConflicts() ? ExitCodes.CONFLICTS : ExitCodes.SUCCESS;
        } catch (Exception exception) {
            System.err.println("Diff failed: " + exception.getMessage());
            return ExitCodes.VALIDATION_FAILED;
        }
    }

    private void printDiff(io.backsmith.model.PlannedFile file) {
        Path existing = project.resolve(file.relativePath()).normalize();
        if (!Files.isRegularFile(existing) || file.content() == null) return;
        try {
            String before = Files.readString(existing, StandardCharsets.UTF_8);
            String after = file.content();
            if (before.equals(after)) return;
            System.out.println("--- " + file.relativePath() + " (existing)");
            System.out.println("+++ " + file.relativePath() + " (generated)");
            System.out.println("@@ full-file preview @@");
            before.lines().limit(80).forEach(line -> System.out.println("-" + line));
            after.lines().limit(80).forEach(line -> System.out.println("+" + line));
            if (before.lines().count() > 80 || after.lines().count() > 80) {
                System.out.println("... diff truncated after 80 lines per side");
            }
        } catch (Exception exception) {
            System.out.println("... unable to render text diff: " + exception.getMessage());
        }
    }
}
