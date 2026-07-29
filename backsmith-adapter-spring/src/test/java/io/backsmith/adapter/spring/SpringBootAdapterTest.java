package io.backsmith.adapter.spring;

import static org.junit.jupiter.api.Assertions.*;

import io.backsmith.model.Architecture;
import io.backsmith.model.ProjectConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SpringBootAdapterTest {
    @Test
    void rendersHexagonalProjectDeterministically() {
        var configuration = ProjectConfiguration.defaults("orders", Architecture.HEXAGONAL);
        var first = new SpringBootAdapter().createProject(configuration);
        var second = new SpringBootAdapter().createProject(configuration);
        assertEquals(first, second);
        assertTrue(first.containsKey(Path.of("src/main/java/com/example/orders/Application.java")));
        assertTrue(first.keySet().stream().anyMatch(path -> path.toString().contains("application")));
    }
}
