package io.backsmith.core;

import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public interface BackendFrameworkAdapter {
    String id();
    Set<String> capabilities();
    Map<Path, String> createProject(ProjectConfiguration configuration);
}
