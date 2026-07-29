package io.backsmith.model;

import java.nio.file.Path;

public record PlannedFile(
        Path relativePath,
        OperationType operation,
        String content,
        String contentHash,
        String existingHash,
        String reason) {
}
