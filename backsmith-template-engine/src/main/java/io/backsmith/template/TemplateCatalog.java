package io.backsmith.template;

import java.util.Set;

public interface TemplateCatalog {
    String id();

    String version();

    Set<String> architectures();

    String load(String relativePath);
}
