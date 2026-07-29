package io.backsmith.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ClasspathTemplateCatalogTest {
    private final ClasspathTemplateCatalog catalog =
            new ClasspathTemplateCatalog(getClass().getClassLoader(), "test-templates");

    @Test
    void loadsVersionedTemplates() {
        assertEquals("test", catalog.id());
        assertEquals("1", catalog.version());
        assertEquals("hello\n", catalog.load("common/hello.mustache"));
    }

    @Test
    void blocksTraversalOutsideThePack() {
        assertThrows(IllegalArgumentException.class, () -> catalog.load("../secret"));
    }
}
