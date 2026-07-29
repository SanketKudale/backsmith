package io.backsmith.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BacksmithVersion {
    private static final String RESOURCE = "/io/backsmith/core/backsmith-version.properties";
    private static final String VERSION = load();

    private BacksmithVersion() {}

    public static String current() {
        return VERSION;
    }

    private static String load() {
        try (InputStream stream = BacksmithVersion.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return "development";
            }
            var properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("version", "development").trim();
            return version.startsWith("${") ? "development" : version;
        } catch (IOException ignored) {
            return "development";
        }
    }
}
