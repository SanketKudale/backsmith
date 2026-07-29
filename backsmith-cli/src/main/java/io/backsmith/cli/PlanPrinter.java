package io.backsmith.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.backsmith.model.GenerationPlan;
import java.io.PrintWriter;
import java.util.Map;

final class PlanPrinter {
    private PlanPrinter() {}

    static void print(GenerationPlan plan, boolean json, boolean quiet, PrintWriter out)
            throws Exception {
        if (quiet) {
            return;
        }
        if (json) {
            var entries =
                    plan.files().stream()
                            .map(
                                    file ->
                                            Map.of(
                                                    "operation", file.operation().name(),
                                                    "path",
                                                            file.relativePath()
                                                                    .toString()
                                                                    .replace('\\', '/'),
                                                    "reason", file.reason()))
                            .toList();
            out.println(
                    new ObjectMapper()
                            .writeValueAsString(
                                    Map.of(
                                            "target", plan.target().toString(),
                                            "conflicts", plan.hasConflicts(),
                                            "files", entries)));
            return;
        }
        plan.files()
                .forEach(file -> out.printf("%-9s %s%n", file.operation(), file.relativePath()));
    }
}
