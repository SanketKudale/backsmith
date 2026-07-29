package io.backsmith.model;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public record GenerationPlan(Path target, List<PlannedFile> files) {
    public GenerationPlan {
        target = target.toAbsolutePath().normalize();
        files = files.stream().sorted(Comparator.comparing(f -> f.relativePath().toString())).toList();
    }

    public boolean hasConflicts() {
        return files.stream().anyMatch(file -> file.operation() == OperationType.CONFLICT);
    }
}
