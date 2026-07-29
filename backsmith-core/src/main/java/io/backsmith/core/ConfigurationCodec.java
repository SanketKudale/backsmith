package io.backsmith.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import java.io.IOException;
import java.nio.file.Path;

public final class ConfigurationCodec {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            JsonMapper.builder(new YAMLFactory())
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

    public String write(ProjectConfiguration configuration) throws IOException {
        return mapper.writeValueAsString(configuration);
    }

    public ProjectConfiguration read(Path path) throws IOException {
        return mapper.readValue(path.toFile(), ProjectConfiguration.class);
    }

    public UpgradeResult upgrade(Path path) throws IOException {
        JsonNode parsed = mapper.readTree(path.toFile());
        if (!(parsed instanceof ObjectNode root)) {
            throw new IllegalArgumentException("configuration root must be a YAML object");
        }
        JsonNode versionNode = root.get("schema_version");
        int version = versionNode == null ? 0 : versionNode.asInt(-1);
        if (version < 0 || version > 1) {
            throw new IllegalArgumentException(
                    "schema_version: cannot migrate unsupported version " + version);
        }
        boolean changed = version < 1;
        if (changed) {
            root.put("schema_version", 1);
        }
        mapper.treeToValue(root, ProjectConfiguration.class);
        return new UpgradeResult(changed, version, 1, mapper.writeValueAsString(root));
    }

    public String addModule(Path path, String module) throws IOException {
        JsonNode parsed = mapper.readTree(path.toFile());
        if (!(parsed instanceof ObjectNode root)) {
            throw new IllegalArgumentException("configuration root must be a YAML object");
        }
        var names = new java.util.TreeSet<String>();
        root.path("modules").forEach(item -> names.add(item.asText()));
        names.add(module);
        ArrayNode modules = root.putArray("modules");
        names.forEach(modules::add);
        mapper.treeToValue(root, ProjectConfiguration.class);
        return mapper.writeValueAsString(root);
    }

    public ProjectConfiguration defaults(String name, String architecture) {
        return ProjectConfiguration.defaults(name, Architecture.parse(architecture));
    }

    public record UpgradeResult(boolean changed, int fromVersion, int toVersion, String content) {}
}
