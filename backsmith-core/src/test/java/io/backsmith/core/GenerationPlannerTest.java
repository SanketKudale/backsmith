package io.backsmith.core;

import static org.junit.jupiter.api.Assertions.*;

import io.backsmith.model.OperationType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerationPlannerTest {
    @TempDir Path directory;

    @Test
    void createsNewFilesAndDetectsConflicts() throws Exception {
        var planner = new GenerationPlanner();
        var first = planner.plan(directory, Map.of(Path.of("src/A.java"), "one"), false, false);
        assertEquals(OperationType.CREATE, first.files().getFirst().operation());
        new PlanApplier().apply(first);
        var second = planner.plan(directory, Map.of(Path.of("src/A.java"), "two"), false, false);
        assertEquals(OperationType.CONFLICT, second.files().getFirst().operation());
    }

    @Test
    void rejectsTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePath.resolve(directory, Path.of("..", "escape")));
    }

    @Test
    void dryRunPlanDoesNotWrite() throws Exception {
        new GenerationPlanner().plan(directory, Map.of(Path.of("dry.txt"), "content"), false, false);
        assertFalse(Files.exists(directory.resolve("dry.txt")));
    }
}
