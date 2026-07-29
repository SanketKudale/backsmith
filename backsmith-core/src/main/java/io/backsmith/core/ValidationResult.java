package io.backsmith.core;

import java.util.List;

public record ValidationResult(List<String> errors, List<String> warnings) {
    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static ValidationResult valid() {
        return new ValidationResult(List.of(), List.of());
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
