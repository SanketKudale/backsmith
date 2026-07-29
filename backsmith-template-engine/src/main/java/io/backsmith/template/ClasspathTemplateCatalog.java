package io.backsmith.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClasspathTemplateCatalog implements TemplateCatalog {
    private final ClassLoader loader;
    private final String root;
    private final String id;
    private final String version;
    private final Set<String> architectures;

    public ClasspathTemplateCatalog(ClassLoader loader, String root) {
        this.loader = loader;
        this.root = normalizeRoot(root);
        var metadata = loadProperties(this.root + "/template-pack.properties");
        this.id = required(metadata, "id");
        this.version = required(metadata, "version");
        this.architectures =
                Arrays.stream(required(metadata, "architectures").split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
        if (architectures.isEmpty()) {
            throw new IllegalArgumentException(
                    "template pack must declare at least one architecture");
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Set<String> architectures() {
        return architectures;
    }

    @Override
    public String load(String relativePath) {
        Path normalized = safeRelative(relativePath);
        String resource = root + "/" + normalized.toString().replace('\\', '/');
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null)
                throw new IllegalArgumentException("template not found: " + relativePath);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load template: " + relativePath, exception);
        }
    }

    private Properties loadProperties(String resource) {
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null)
                throw new IllegalArgumentException("template metadata not found: " + resource);
            var properties = new Properties();
            properties.load(stream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "failed to load template metadata: " + resource, exception);
        }
    }

    private Path safeRelative(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("template path is required");
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("template path escapes its pack: " + value);
        }
        return path;
    }

    private String normalizeRoot(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("template root is required");
        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/"))
            normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.contains(".."))
            throw new IllegalArgumentException("unsafe template root: " + value);
        return normalized;
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("template metadata is missing '" + key + "'");
        }
        return value.trim();
    }
}
