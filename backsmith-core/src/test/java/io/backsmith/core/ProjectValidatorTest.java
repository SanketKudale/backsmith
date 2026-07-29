package io.backsmith.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.backsmith.model.Architecture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectValidatorTest {
    @TempDir Path project;

    @Test
    void detectsDriftInAnOwnedFile() throws Exception {
        var codec = new ConfigurationCodec();
        Files.writeString(
                project.resolve("backsmith.yaml"),
                codec.write(codec.defaults("validator", Architecture.LAYERED.name())));
        var plan =
                new GenerationPlanner()
                        .plan(project, Map.of(Path.of("owned.txt"), "original\n"), false, false);
        new PlanApplier().apply(plan);

        Files.writeString(project.resolve("owned.txt"), "changed\n");

        var report = new ProjectValidator().validate(project);
        assertFalse(report.valid());
        assertTrue(
                report.issues().stream()
                        .anyMatch(issue -> issue.code().equals("OWNED_FILE_MODIFIED")));
    }
}
