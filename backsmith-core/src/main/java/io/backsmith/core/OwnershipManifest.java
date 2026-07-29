package io.backsmith.core;

import java.util.List;

public record OwnershipManifest(
        int schemaVersion, String backsmithVersion, String generatedAt, List<OwnedFile> files) {

    public OwnershipManifest {
        files = files == null ? List.of() : List.copyOf(files);
    }

    public record OwnedFile(
            String path,
            String generator,
            String templateVersion,
            String contentHash,
            String owningModule,
            String ownership,
            String generatedAt,
            String backsmithVersion) {}
}
