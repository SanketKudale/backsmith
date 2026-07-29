package io.backsmith.core;

import java.nio.file.Path;

public final class SafePath {
    private SafePath() {}

    public static Path resolve(Path root, Path relative) {
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Generated path must be relative: " + relative);
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Generated path escapes project: " + relative);
        }
        return resolved;
    }
}
