package io.backsmith.model;

public enum Architecture {
    LAYERED,
    HEXAGONAL,
    MODULAR_MONOLITH,
    CLEAN,
    ONION,
    CQRS,
    MICROSERVICE;

    public static Architecture parse(String value) {
        return valueOf(value.trim().replace('-', '_').toUpperCase());
    }
}
