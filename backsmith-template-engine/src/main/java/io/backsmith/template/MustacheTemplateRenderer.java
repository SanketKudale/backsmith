package io.backsmith.template;

import com.github.mustachejava.DefaultMustacheFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

public final class MustacheTemplateRenderer implements TemplateRenderer {
    private final DefaultMustacheFactory factory = new DefaultMustacheFactory();

    @Override
    public String render(String template, Map<String, ?> values) {
        var writer = new StringWriter();
        factory.compile(new StringReader(template), "inline").execute(writer, values);
        return writer.toString().replace("\r\n", "\n").replace('\r', '\n');
    }
}
