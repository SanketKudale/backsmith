package io.backsmith.core;

import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface BackendFrameworkAdapter {
    String id();

    FrameworkCapabilities capabilities();

    Map<Path, String> createProject(ProjectConfiguration configuration);

    default Map<Path, String> initializeProject(ProjectConfiguration configuration) {
        return createProject(configuration);
    }

    List<GeneratorProvider> generators();

    default ValidationResult validate(ProjectContext context) {
        return ValidationResult.valid();
    }
}
