package io.backsmith.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.backsmith.model.ProjectConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public final class ProjectValidator {
    private static final Pattern MIGRATION = Pattern.compile("^V([0-9]+(?:\\.[0-9]+)*)__.+\\.sql$");
    private final ConfigurationCodec configurations = new ConfigurationCodec();
    private final ObjectMapper json = new ObjectMapper();

    public ValidationReport validate(Path project) {
        Path root = project.toAbsolutePath().normalize();
        var issues = new ArrayList<ValidationIssue>();
        ProjectConfiguration configuration = validateConfiguration(root, issues);
        validateManifest(root, issues);
        validateMigrations(root, issues);
        if (configuration != null) {
            configuration
                    .modules()
                    .forEach(
                            module -> {
                                if (!module.matches("[a-z][a-z0-9]*")) {
                                    issues.add(
                                            error(
                                                    "INVALID_MODULE",
                                                    Path.of("backsmith.yaml"),
                                                    "modules contains an unsafe Java package segment: "
                                                            + module));
                                }
                            });
        }
        return new ValidationReport(root, configuration, List.copyOf(issues));
    }

    private ProjectConfiguration validateConfiguration(Path root, List<ValidationIssue> issues) {
        Path path = root.resolve("backsmith.yaml");
        if (!Files.isRegularFile(path)) {
            issues.add(
                    error(
                            "CONFIGURATION_MISSING",
                            Path.of("backsmith.yaml"),
                            "backsmith.yaml is required"));
            return null;
        }
        try {
            return configurations.read(path);
        } catch (Exception exception) {
            issues.add(
                    error(
                            "CONFIGURATION_INVALID",
                            Path.of("backsmith.yaml"),
                            actionable(exception)));
            return null;
        }
    }

    private void validateManifest(Path root, List<ValidationIssue> issues) {
        Path manifestPath = root.resolve(".backsmith/manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            issues.add(
                    error(
                            "MANIFEST_MISSING",
                            Path.of(".backsmith/manifest.json"),
                            "ownership manifest is missing; run a generator to recreate it"));
            return;
        }
        try {
            OwnershipManifest manifest =
                    json.readValue(manifestPath.toFile(), OwnershipManifest.class);
            if (manifest.schemaVersion() != 1) {
                issues.add(
                        error(
                                "MANIFEST_SCHEMA_UNSUPPORTED",
                                Path.of(".backsmith/manifest.json"),
                                "unsupported manifest schema " + manifest.schemaVersion()));
                return;
            }
            for (var owned : manifest.files()) {
                Path relative = Path.of(owned.path());
                Path generated = SafePath.resolve(root, relative);
                if (!Files.isRegularFile(generated)) {
                    issues.add(
                            error(
                                    "OWNED_FILE_MISSING",
                                    relative,
                                    "Backsmith-owned file is missing"));
                    continue;
                }
                String content = normalize(Files.readString(generated, StandardCharsets.UTF_8));
                if (!Hashing.sha256(content).equals(owned.contentHash())) {
                    issues.add(
                            error(
                                    "OWNED_FILE_MODIFIED",
                                    relative,
                                    "generated content differs from the ownership manifest"));
                }
            }
            validateManagedMetadata(root, issues);
        } catch (Exception exception) {
            issues.add(
                    error(
                            "MANIFEST_INVALID",
                            Path.of(".backsmith/manifest.json"),
                            actionable(exception)));
        }
    }

    private void validateManagedMetadata(Path root, List<ValidationIssue> issues) {
        for (String relative :
                List.of(
                        ".backsmith/generation-history.json",
                        ".backsmith/modules.json",
                        ".backsmith/api-contracts.json")) {
            Path path = root.resolve(relative);
            if (!Files.isRegularFile(path)) {
                issues.add(
                        error(
                                "MANAGED_METADATA_MISSING",
                                Path.of(relative),
                                "managed metadata is missing; run a generator to recreate it"));
                continue;
            }
            try {
                var document = json.readTree(path.toFile());
                if (document.path("schemaVersion").asInt(-1) != 1) {
                    issues.add(
                            error(
                                    "MANAGED_METADATA_INVALID",
                                    Path.of(relative),
                                    "schemaVersion must be 1"));
                }
                if (relative.endsWith("api-contracts.json")) {
                    document.path("contracts")
                            .forEach(
                                    contract -> {
                                        String generatedPath =
                                                contract.path("generatedPath").asText();
                                        if (generatedPath.isBlank()
                                                || !Files.isRegularFile(
                                                        SafePath.resolve(
                                                                root, Path.of(generatedPath)))) {
                                            issues.add(
                                                    error(
                                                            "API_CONTRACT_REFERENCE_MISSING",
                                                            Path.of(relative),
                                                            "generated API reference is missing: "
                                                                    + generatedPath));
                                        }
                                    });
                }
            } catch (Exception exception) {
                issues.add(
                        error(
                                "MANAGED_METADATA_INVALID",
                                Path.of(relative),
                                actionable(exception)));
            }
        }
    }

    private void validateMigrations(Path root, List<ValidationIssue> issues) {
        Path migrations = root.resolve("src/main/resources/db/migration");
        if (!Files.isDirectory(migrations)) return;
        var versions = new HashMap<String, Path>();
        try (var paths = Files.list(migrations)) {
            paths.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(
                            path -> {
                                var match = MIGRATION.matcher(path.getFileName().toString());
                                if (!match.matches()) {
                                    issues.add(
                                            error(
                                                    "MIGRATION_NAME_INVALID",
                                                    root.relativize(path),
                                                    "Flyway migration must use V<version>__<description>.sql"));
                                    return;
                                }
                                Path previous = versions.putIfAbsent(match.group(1), path);
                                if (previous != null) {
                                    issues.add(
                                            error(
                                                    "MIGRATION_VERSION_DUPLICATE",
                                                    root.relativize(path),
                                                    "migration version "
                                                            + match.group(1)
                                                            + " is already used by "
                                                            + root.relativize(previous)));
                                }
                            });
        } catch (IOException exception) {
            issues.add(
                    error(
                            "MIGRATION_SCAN_FAILED",
                            root.relativize(migrations),
                            actionable(exception)));
        }
    }

    private ValidationIssue error(String code, Path path, String message) {
        return new ValidationIssue("ERROR", code, path.toString().replace('\\', '/'), message);
    }

    private String actionable(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private String normalize(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    public record ValidationIssue(String severity, String code, String path, String message) {}

    public record ValidationReport(
            Path project, ProjectConfiguration configuration, List<ValidationIssue> issues) {
        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity().equals("ERROR"));
        }
    }
}
