package io.backsmith.template;

import java.util.Map;

public interface TemplateRenderer {
    String render(String template, Map<String, ?> values);
}
