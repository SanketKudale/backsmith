package io.backsmith.core;

import java.util.Set;

public record GeneratorProvider(String id, Set<String> supportedOptions) {
    public GeneratorProvider {
        if (id == null || !id.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("generator id must be kebab-case");
        }
        supportedOptions = supportedOptions == null ? Set.of() : Set.copyOf(supportedOptions);
    }
}
