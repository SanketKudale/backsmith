package io.backsmith.core;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final Path MANIFEST_PATH = Path.of(".backsmith", "manifest.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    public GenerationPlan plan(
            Path target, Map<Path, String> renderedFiles, boolean force, boolean skipExisting)
            throws IOException {
        var files = new java.util.ArrayList<PlannedFile>();
        var ownedHashes = ownedHashes(target);
        for (var entry : renderedFiles.entrySet()) {
            var relative = entry.getKey().normalize();
            var destination = SafePath.resolve(target, relative);
            var content = normalize(entry.getValue());
            var desiredHash = Hashing.sha256(content);
            if (!Files.exists(destination)) {
                files.add(
                        new PlannedFile(
                                relative,
                                OperationType.CREATE,
                                content,
                                desiredHash,
                                null,
                                "new file"));
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
            } else if (existingHash.equals(ownedHashes.get(portable(relative)))) {
                operation = OperationType.UPDATE;
                reason = "Backsmith-owned file is unchanged since its last generation";
            } else {
                operation = OperationType.CONFLICT;
                reason =
                        ownedHashes.containsKey(portable(relative))
                                ? "Backsmith-owned file was modified by the user"
                                : "existing content differs";
            }
            files.add(
                    new PlannedFile(
                            relative, operation, content, desiredHash, existingHash, reason));
        }
        return new GenerationPlan(target, List.copyOf(files));
    }

    public GenerationPlan planManagedProject(
            Path target, Map<Path, String> renderedFiles, boolean force, boolean skipExisting)
            throws IOException {
        GenerationPlan base = plan(target, renderedFiles, force, skipExisting);
        if (skipExisting) {
            return base;
        }
        var files = new java.util.ArrayList<>(base.files());
        var desired =
                renderedFiles.keySet().stream()
                        .map(Path::normalize)
                        .map(this::portable)
                        .collect(java.util.stream.Collectors.toSet());
        for (var owned : ownedHashes(target).entrySet()) {
            if (desired.contains(owned.getKey())) {
                continue;
            }
            Path relative = Path.of(owned.getKey());
            Path destination = SafePath.resolve(target, relative);
            if (!Files.isRegularFile(destination)) {
                files.add(
                        new PlannedFile(
                                relative,
                                OperationType.DELETE,
                                "",
                                Hashing.sha256(""),
                                null,
                                "remove missing obsolete ownership entry"));
                continue;
            }
            String existing = normalize(Files.readString(destination, StandardCharsets.UTF_8));
            String existingHash = Hashing.sha256(existing);
            files.add(
                    new PlannedFile(
                            relative,
                            existingHash.equals(owned.getValue())
                                    ? OperationType.DELETE
                                    : OperationType.CONFLICT,
                            "",
                            Hashing.sha256(""),
                            existingHash,
                            existingHash.equals(owned.getValue())
                                    ? "obsolete Backsmith-owned file"
                                    : "obsolete Backsmith-owned file was modified by the user"));
        }
        return new GenerationPlan(target, List.copyOf(files));
    }

    private Map<String, String> ownedHashes(Path target) throws IOException {
        Path path = SafePath.resolve(target, MANIFEST_PATH);
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        OwnershipManifest manifest = JSON.readValue(path.toFile(), OwnershipManifest.class);
        var result = new java.util.HashMap<String, String>();
        manifest.files().forEach(file -> result.put(file.path(), file.contentHash()));
        return Map.copyOf(result);
    }

    private String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String normalize(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }
}
