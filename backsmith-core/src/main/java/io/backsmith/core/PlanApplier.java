package io.backsmith.core;

import io.backsmith.model.GenerationPlan;
import io.backsmith.model.OperationType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class PlanApplier {
    public void apply(GenerationPlan plan) throws IOException {
        if (plan.hasConflicts()) {
            throw new IllegalStateException("Generation plan contains unresolved conflicts");
        }
        for (var file : plan.files()) {
            if (file.operation() != OperationType.CREATE && file.operation() != OperationType.UPDATE) {
                continue;
            }
            var target = SafePath.resolve(plan.target(), file.relativePath());
            Files.createDirectories(target.getParent());
            var temporary = Files.createTempFile(target.getParent(), ".backsmith-", ".tmp");
            try {
                Files.writeString(temporary, file.content(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
