package io.backsmith.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record FieldDefinition(
        String name,
        FieldType type,
        boolean required,
        boolean unique,
        boolean generated,
        Map<String, String> options,
        List<String> enumValues) {

    public FieldDefinition {
        if (name == null || !name.matches("[a-z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException(
                    "field name must be a lower camel-case Java identifier");
        }
        options = options == null ? Map.of() : Map.copyOf(options);
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        if (type == FieldType.DECIMAL
                && (positiveInteger(options.get("precision")) == null
                        || positiveInteger(options.get("scale")) == null)) {
            throw new IllegalArgumentException(
                    "decimal field '" + name + "' requires precision and scale");
        }
        if (type == FieldType.ENUM && enumValues.isEmpty()) {
            throw new IllegalArgumentException(
                    "enum field '" + name + "' requires at least one value");
        }
    }

    public static FieldDefinition parse(String specification) {
        if (specification == null || specification.isBlank()) {
            throw new IllegalArgumentException("field specification must not be blank");
        }
        String[] parts = specification.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "field must use name:type[:constraint] syntax: " + specification);
        }
        String rawType = parts[1].trim();
        List<String> enumValues = List.of();
        FieldType type;
        if (rawType.toLowerCase(Locale.ROOT).startsWith("enum[") && rawType.endsWith("]")) {
            type = FieldType.ENUM;
            String values = rawType.substring(rawType.indexOf('[') + 1, rawType.length() - 1);
            enumValues =
                    List.of(values.split(",")).stream()
                            .map(String::trim)
                            .filter(value -> !value.isBlank())
                            .peek(
                                    value -> {
                                        if (!value.matches("[A-Z][A-Z0-9_]*")) {
                                            throw new IllegalArgumentException(
                                                    "enum value must use UPPER_SNAKE_CASE: "
                                                            + value);
                                        }
                                    })
                            .toList();
        } else {
            type = FieldType.parse(rawType);
        }
        boolean required = false;
        boolean unique = false;
        boolean generated = false;
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 2; index < parts.length; index++) {
            String constraint = parts[index].trim();
            switch (constraint) {
                case "required" -> required = true;
                case "unique" -> unique = true;
                case "generated" -> generated = true;
                default -> {
                    int equals = constraint.indexOf('=');
                    if (equals < 1 || equals == constraint.length() - 1) {
                        throw new IllegalArgumentException(
                                "unsupported field constraint: " + constraint);
                    }
                    options.put(constraint.substring(0, equals), constraint.substring(equals + 1));
                }
            }
        }
        return new FieldDefinition(
                parts[0].trim(), type, required, unique, generated, options, enumValues);
    }

    private static Integer positiveInteger(String value) {
        if (value == null || !value.matches("\\d+")) return null;
        int parsed = Integer.parseInt(value);
        return parsed >= 0 ? parsed : null;
    }

    public enum FieldType {
        STRING,
        TEXT,
        INTEGER,
        LONG,
        DECIMAL,
        BOOLEAN,
        UUID,
        DATE,
        INSTANT,
        EMAIL,
        JSON,
        BINARY,
        REFERENCE,
        LIST,
        ENUM;

        static FieldType parse(String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "string" -> STRING;
                case "text" -> TEXT;
                case "integer", "int" -> INTEGER;
                case "long" -> LONG;
                case "decimal", "money" -> DECIMAL;
                case "boolean", "bool" -> BOOLEAN;
                case "uuid" -> UUID;
                case "date" -> DATE;
                case "date-time", "datetime", "instant" -> INSTANT;
                case "email" -> EMAIL;
                case "json" -> JSON;
                case "binary", "bytes" -> BINARY;
                case "reference", "ref" -> REFERENCE;
                case "list" -> LIST;
                default -> throw new IllegalArgumentException("unsupported field type: " + value);
            };
        }
    }
}
