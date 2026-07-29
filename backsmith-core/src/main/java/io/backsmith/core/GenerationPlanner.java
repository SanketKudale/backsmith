package io.backsmith.core;

import io.backsmith.model.GenerationPlan;
import io.backsmith.model.OperationType;
import io.backsmith.model.PlannedFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class GenerationPlanner {
    public GenerationPlan plan(Path target, Map<Path, String> renderedFiles, boolean force, boolean skipExisting)
            throws IOException {
        var files = new java.util.ArrayList<PlannedFile>();
        for (var entry : renderedFiles.entrySet()) {
            var relative = entry.getKey().normalize();
            var destination = SafePath.resolve(target, relative);
            var content = normalize(entry.getValue());
            var desiredHash = Hashing.sha256(content);
            if (!Files.exists(destination)) {
                files.add(new PlannedFile(relative, OperationType.CREATE, content, desiredHash, null, "new file"));
                continue;
            }
            var existing = normalize(Files.readString(destination, StandardCharsets.UTF_8));
            var existingHash = Hashing.sha256(existing);
            OperationType operation;
            String reason;
            if (desiredHash.equals(existingHash)) {
                operation = OperationType.SKIP;
                reason = "unchanged";
            } else if (force) {
                operation = OperationType.UPDATE;
                reason = "overwrite explicitly permitted";
            } else if (skipExisting) {
                operation = OperationType.SKIP;
                reason = "existing file preserved";
            } else {
                operation = OperationType.CONFLICT;
                reason = "existing content differs";
            }
            files.add(new PlannedFile(relative, operation, content, desiredHash, existingHash, reason));
        }
        return new GenerationPlan(target, List.copyOf(files));
    }

    private static String normalize(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }
}
