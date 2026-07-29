package io.backsmith.core;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import java.io.IOException;
import java.nio.file.Path;

public final class ConfigurationCodec {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder(new YAMLFactory())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public String write(ProjectConfiguration configuration) throws IOException {
        return mapper.writeValueAsString(configuration);
    }

    public ProjectConfiguration read(Path path) throws IOException {
        return mapper.readValue(path.toFile(), ProjectConfiguration.class);
    }

    public ProjectConfiguration defaults(String name, String architecture) {
        return ProjectConfiguration.defaults(name, Architecture.parse(architecture));
    }
}
