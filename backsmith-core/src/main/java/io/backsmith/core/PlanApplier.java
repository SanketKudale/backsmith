package io.backsmith.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.backsmith.model.GenerationPlan;
import io.backsmith.model.OperationType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlanApplier {
    private static final Path MANIFEST_PATH = Path.of(".backsmith", "manifest.json");
    private static final Path HISTORY_PATH = Path.of(".backsmith", "generation-history.json");
    private static final Path MODULES_PATH = Path.of(".backsmith", "modules.json");
    private static final Path API_CONTRACTS_PATH = Path.of(".backsmith", "api-contracts.json");
    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void apply(GenerationPlan plan) throws IOException {
        if (plan.hasConflicts()) {
            throw new IllegalStateException("Generation plan contains unresolved conflicts");
        }
        var changes = new ArrayList<PreviousState>();
        try {
            for (var file : plan.files()) {
                if (file.operation() == OperationType.DELETE) {
                    var target = SafePath.resolve(plan.target(), file.relativePath());
                    changes.add(previousState(target));
                    Files.deleteIfExists(target);
                } else if (file.operation() != OperationType.CREATE
                        && file.operation() != OperationType.UPDATE) {
                    continue;
                } else {
                    var target = SafePath.resolve(plan.target(), file.relativePath());
                    changes.add(previousState(target));
                    writeAtomically(target, file.content());
                }
            }
            if (plan.files().stream()
                    .anyMatch(
                            file ->
                                    file.operation() == OperationType.CREATE
                                            || file.operation() == OperationType.UPDATE
                                            || file.operation() == OperationType.DELETE)) {
                String generatedAt = Instant.now().toString();
                Path manifest = SafePath.resolve(plan.target(), MANIFEST_PATH);
                changes.add(previousState(manifest));
                String manifestJson = manifestContent(plan, manifest, generatedAt);
                writeAtomically(manifest, manifestJson);
                writeMetadata(plan, manifestJson, generatedAt, changes);
            }
        } catch (IOException | RuntimeException failure) {
            rollback(changes, failure);
            throw failure;
        }
    }

    private String manifestContent(GenerationPlan plan, Path manifestPath, String generatedAt)
            throws IOException {
        Map<String, OwnershipManifest.OwnedFile> owned = new LinkedHashMap<>();
        if (Files.isRegularFile(manifestPath)) {
            OwnershipManifest current =
                    JSON.readValue(manifestPath.toFile(), OwnershipManifest.class);
            current.files().forEach(file -> owned.put(file.path(), file));
        }
        for (var file : plan.files()) {
            String path = portable(file.relativePath());
            if (file.operation() == OperationType.DELETE) {
                owned.remove(path);
                continue;
            }
            if (file.operation() == OperationType.CONFLICT
                    || file.relativePath().equals(MANIFEST_PATH)
                    || (file.operation() == OperationType.SKIP
                            && !file.contentHash().equals(file.existingHash()))) {
                continue;
            }
            owned.put(
                    path,
                    new OwnershipManifest.OwnedFile(
                            path,
                            generatorFor(path),
                            "1",
                            file.contentHash(),
                            moduleFor(path),
                            "fully-managed",
                            generatedAt,
                            BacksmithVersion.current()));
        }
        var sorted =
                owned.values().stream()
                        .sorted(java.util.Comparator.comparing(OwnershipManifest.OwnedFile::path))
                        .toList();
        var manifest = new OwnershipManifest(1, BacksmithVersion.current(), generatedAt, sorted);
        return JSON.writeValueAsString(manifest) + "\n";
    }

    private void writeMetadata(
            GenerationPlan plan,
            String manifestJson,
            String generatedAt,
            List<PreviousState> changes)
            throws IOException {
        OwnershipManifest manifest = JSON.readValue(manifestJson, OwnershipManifest.class);
        writeTracked(plan.target(), MODULES_PATH, modulesContent(manifest), changes);
        writeTracked(plan.target(), API_CONTRACTS_PATH, apiContractsContent(manifest), changes);
        writeTracked(plan.target(), HISTORY_PATH, historyContent(plan, generatedAt), changes);
    }

    private String modulesContent(OwnershipManifest manifest) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode modules = root.putArray("modules");
        manifest.files().stream()
                .filter(file -> file.owningModule() != null)
                .collect(
                        java.util.stream.Collectors.groupingBy(
                                OwnershipManifest.OwnedFile::owningModule,
                                java.util.TreeMap::new,
                                java.util.stream.Collectors.mapping(
                                        OwnershipManifest.OwnedFile::path,
                                        java.util.stream.Collectors.toCollection(
                                                java.util.TreeSet::new))))
                .forEach(
                        (name, paths) -> {
                            ObjectNode module = modules.addObject();
                            module.put("name", name);
                            ArrayNode files = module.putArray("files");
                            paths.forEach(files::add);
                        });
        return JSON.writeValueAsString(root) + "\n";
    }

    private String apiContractsContent(OwnershipManifest manifest) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode contracts = root.putArray("contracts");
        manifest.files().stream()
                .filter(file -> file.generator().equals("openapi"))
                .sorted(java.util.Comparator.comparing(OwnershipManifest.OwnedFile::path))
                .forEach(
                        file -> {
                            ObjectNode contract = contracts.addObject();
                            contract.put("generatedPath", file.path());
                            contract.put("templateVersion", file.templateVersion());
                            if (file.owningModule() != null) {
                                contract.put("module", file.owningModule());
                            }
                        });
        return JSON.writeValueAsString(root) + "\n";
    }

    private String historyContent(GenerationPlan plan, String generatedAt) throws IOException {
        Path historyPath = SafePath.resolve(plan.target(), HISTORY_PATH);
        ObjectNode root;
        if (Files.isRegularFile(historyPath)) {
            var parsed = JSON.readTree(historyPath.toFile());
            root = parsed instanceof ObjectNode object ? object : JSON.createObjectNode();
        } else {
            root = JSON.createObjectNode();
        }
        root.put("schemaVersion", 1);
        ArrayNode events =
                root.get("events") instanceof ArrayNode array ? array : root.putArray("events");
        ObjectNode event = events.addObject();
        event.put("generatedAt", generatedAt);
        event.put("backsmithVersion", BacksmithVersion.current());
        ObjectNode operations = event.putObject("operations");
        for (OperationType operation : OperationType.values()) {
            long count =
                    plan.files().stream().filter(file -> file.operation() == operation).count();
            if (count > 0) {
                operations.put(operation.name(), count);
            }
        }
        ArrayNode paths = event.putArray("paths");
        plan.files().stream()
                .filter(
                        file ->
                                file.operation() == OperationType.CREATE
                                        || file.operation() == OperationType.UPDATE
                                        || file.operation() == OperationType.DELETE)
                .map(file -> portable(file.relativePath()))
                .sorted()
                .forEach(paths::add);
        while (events.size() > 100) {
            events.remove(0);
        }
        return JSON.writeValueAsString(root) + "\n";
    }

    private void writeTracked(
            Path target, Path relative, String content, List<PreviousState> changes)
            throws IOException {
        Path path = SafePath.resolve(target, relative);
        changes.add(previousState(path));
        writeAtomically(path, content);
    }

    private String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String generatorFor(String path) {
        if (path.equals("backsmith.yaml")) return "configuration";
        if (path.endsWith("Api.java") || path.endsWith("ContractTest.java")) return "openapi";
        if (path.contains("/db/migration/")) return "migration";
        if (path.endsWith("Controller.java")) return "controller";
        if (path.endsWith("Repository.java")) return "repository";
        if (path.endsWith("Service.java")) return "service";
        if (path.endsWith("Test.java")) return "test";
        return "project";
    }

    private String moduleFor(String path) {
        String marker = "/modules/";
        int start = path.indexOf(marker);
        if (start < 0) return null;
        int moduleStart = start + marker.length();
        int moduleEnd = path.indexOf('/', moduleStart);
        return moduleEnd < 0 ? null : path.substring(moduleStart, moduleEnd);
    }

    protected void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        var temporary = Files.createTempFile(target.getParent(), ".backsmith-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private PreviousState previousState(Path target) throws IOException {
        return Files.exists(target)
                ? new PreviousState(target, Files.readAllBytes(target))
                : new PreviousState(target, null);
    }

    private void rollback(List<PreviousState> changes, Throwable originalFailure) {
        Collections.reverse(changes);
        for (var change : changes) {
            try {
                if (change.content() == null) {
                    Files.deleteIfExists(change.path());
                } else {
                    restoreAtomically(change.path(), change.content());
                }
            } catch (IOException rollbackFailure) {
                originalFailure.addSuppressed(rollbackFailure);
            }
        }
    }

    private void restoreAtomically(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        var temporary = Files.createTempFile(target.getParent(), ".backsmith-rollback-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record PreviousState(Path path, byte[] content) {}
}
