package io.backsmith.core;

import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Path;

public record ProjectContext(Path root, ProjectConfiguration configuration) {
    public ProjectContext {
        root = root.toAbsolutePath().normalize();
    }
}
