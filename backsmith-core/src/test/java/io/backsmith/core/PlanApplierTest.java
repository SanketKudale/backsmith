package io.backsmith.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.backsmith.model.GenerationPlan;
import io.backsmith.model.OperationType;
import io.backsmith.model.PlannedFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanApplierTest {
    @TempDir Path project;

    @Test
    void rollsBackEveryAppliedFileWhenALaterWriteFails() throws Exception {
        Path existing = project.resolve("existing.txt");
        Files.writeString(existing, "original\n");
        var plan =
                new GenerationPlan(
                        project,
                        List.of(
                                file("existing.txt", OperationType.UPDATE, "updated\n"),
                                file("new.txt", OperationType.CREATE, "new\n"),
                                file("failure.txt", OperationType.CREATE, "failure\n")));
        var applier =
                new PlanApplier() {
                    private int writes;

                    @Override
                    protected void writeAtomically(Path target, String content) throws IOException {
                        if (++writes == 3) {
                            throw new IOException("simulated write failure");
                        }
                        super.writeAtomically(target, content);
                    }
                };

        assertThrows(IOException.class, () -> applier.apply(plan));

        assertEquals("original\n", Files.readString(existing));
        assertFalse(Files.exists(project.resolve("new.txt")));
        assertFalse(Files.exists(project.resolve("failure.txt")));
    }

    @Test
    void refusesToApplyPlansContainingConflicts() {
        var plan =
                new GenerationPlan(
                        project,
                        List.of(file("conflict.txt", OperationType.CONFLICT, "desired\n")));

        assertThrows(IllegalStateException.class, () -> new PlanApplier().apply(plan));
        assertFalse(Files.exists(project.resolve("conflict.txt")));
    }

    @Test
    void writesAHashBasedOwnershipManifestAfterSuccessfulApply() throws Exception {
        var plan =
                new GenerationPlan(
                        project,
                        List.of(
                                file(
                                        "src/main/java/com/example/modules/payment/Payment.java",
                                        OperationType.CREATE,
                                        "package com.example.modules.payment;\n")));

        new PlanApplier().apply(plan);

        Path manifestPath = project.resolve(".backsmith/manifest.json");
        assertTrue(Files.isRegularFile(manifestPath));
        OwnershipManifest manifest =
                new ObjectMapper().readValue(manifestPath.toFile(), OwnershipManifest.class);
        assertEquals(1, manifest.schemaVersion());
        assertEquals(BacksmithVersion.current(), manifest.backsmithVersion());
        assertEquals(1, manifest.files().size());
        assertEquals("payment", manifest.files().getFirst().owningModule());
        assertEquals(
                plan.files().getFirst().contentHash(), manifest.files().getFirst().contentHash());
        assertTrue(Files.isRegularFile(project.resolve(".backsmith/generation-history.json")));
        assertTrue(Files.isRegularFile(project.resolve(".backsmith/modules.json")));
        assertTrue(Files.isRegularFile(project.resolve(".backsmith/api-contracts.json")));
    }

    @Test
    void deletesOnlyFilesExplicitlyMarkedForDeletionAndUpdatesOwnership() throws Exception {
        var create =
                new GenerationPlan(
                        project, List.of(file("obsolete.txt", OperationType.CREATE, "managed\n")));
        new PlanApplier().apply(create);
        var delete =
                new GenerationPlan(
                        project, List.of(file("obsolete.txt", OperationType.DELETE, "")));

        new PlanApplier().apply(delete);

        assertFalse(Files.exists(project.resolve("obsolete.txt")));
        OwnershipManifest manifest =
                new ObjectMapper()
                        .readValue(
                                project.resolve(".backsmith/manifest.json").toFile(),
                                OwnershipManifest.class);
        assertTrue(manifest.files().isEmpty());
    }

    private static PlannedFile file(String path, OperationType operation, String content) {
        return new PlannedFile(
                Path.of(path), operation, content, Hashing.sha256(content), null, "test");
    }
}
