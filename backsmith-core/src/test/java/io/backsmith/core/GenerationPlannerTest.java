package io.backsmith.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.backsmith.model.OperationType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerationPlannerTest {
    @TempDir Path directory;

    @Test
    void createsNewFilesAndDetectsConflicts() throws Exception {
        var planner = new GenerationPlanner();
        var first = planner.plan(directory, Map.of(Path.of("src/A.java"), "one"), false, false);
        assertEquals(OperationType.CREATE, first.files().getFirst().operation());
        new PlanApplier().apply(first);
        Files.writeString(directory.resolve("src/A.java"), "user edit\n");
        var second = planner.plan(directory, Map.of(Path.of("src/A.java"), "two"), false, false);
        assertEquals(OperationType.CONFLICT, second.files().getFirst().operation());
    }

    @Test
    void rejectsTraversal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SafePath.resolve(directory, Path.of("..", "escape")));
    }

    @Test
    void dryRunPlanDoesNotWrite() throws Exception {
        new GenerationPlanner()
                .plan(directory, Map.of(Path.of("dry.txt"), "content"), false, false);
        assertFalse(Files.exists(directory.resolve("dry.txt")));
    }

    @Test
    void safelyUpdatesAnUnmodifiedOwnedFile() throws Exception {
        Path target = directory.resolve("owned");
        Path source = target.resolve("src/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "old\n", StandardCharsets.UTF_8);
        writeManifest(target, Hashing.sha256("old\n"));

        var plan =
                new GenerationPlanner()
                        .plan(target, Map.of(Path.of("src/Example.java"), "new"), false, false);

        assertEquals(OperationType.UPDATE, plan.files().getFirst().operation());
        assertTrue(plan.files().getFirst().reason().contains("Backsmith-owned"));
    }

    @Test
    void protectsAnOwnedFileAfterTheUserModifiesIt() throws Exception {
        Path target = directory.resolve("modified-owned");
        Path source = target.resolve("src/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "user edit\n", StandardCharsets.UTF_8);
        writeManifest(target, Hashing.sha256("old\n"));

        var plan =
                new GenerationPlanner()
                        .plan(target, Map.of(Path.of("src/Example.java"), "new"), false, false);

        assertEquals(OperationType.CONFLICT, plan.files().getFirst().operation());
        assertTrue(plan.files().getFirst().reason().contains("modified by the user"));
    }

    @Test
    void deletesOnlyAnUnmodifiedOwnedFileThatBecameObsolete() throws Exception {
        Path target = directory.resolve("obsolete");
        Path source = target.resolve("old.txt");
        Files.createDirectories(target);
        Files.writeString(source, "managed\n");
        Path manifest = target.resolve(".backsmith/manifest.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(
                manifest,
                """
                {
                  "schemaVersion": 1,
                  "backsmithVersion": "test",
                  "generatedAt": "2026-01-01T00:00:00Z",
                  "files": [{
                    "path": "old.txt",
                    "generator": "project",
                    "templateVersion": "1",
                    "contentHash": "%s",
                    "owningModule": null,
                    "ownership": "fully-managed"
                  }]
                }
                """
                        .formatted(Hashing.sha256("managed\n")));

        var plan =
                new GenerationPlanner()
                        .planManagedProject(
                                target, Map.of(Path.of("new.txt"), "new"), false, false);

        assertTrue(
                plan.files().stream()
                        .anyMatch(
                                file ->
                                        file.relativePath().equals(Path.of("old.txt"))
                                                && file.operation() == OperationType.DELETE));
    }

    private void writeManifest(Path target, String hash) throws Exception {
        Path manifest = target.resolve(".backsmith/manifest.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(
                manifest,
                """
                {
                  "schemaVersion": 1,
                  "backsmithVersion": "test",
                  "generatedAt": "2026-01-01T00:00:00Z",
                  "files": [{
                    "path": "src/Example.java",
                    "generator": "project",
                    "templateVersion": "1",
                    "contentHash": "%s",
                    "owningModule": null,
                    "ownership": "fully-managed"
                  }]
                }
                """
                        .formatted(hash));
    }
}
