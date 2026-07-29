package io.backsmith.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProjectConfigurationFeaturesTest {
    @Test
    void acceptsRelationalAndDocumentDatabaseContracts() {
        assertDoesNotThrow(() -> features("postgresql", "jpa", "flyway"));
        assertDoesNotThrow(() -> features("mysql", "jpa", "flyway"));
        assertDoesNotThrow(() -> features("mariadb", "jpa", "flyway"));
        assertDoesNotThrow(() -> features("sqlserver", "jpa", "flyway"));
        assertDoesNotThrow(() -> features("oracle", "jpa", "flyway"));
        assertDoesNotThrow(() -> features("h2", "jpa", "flyway"));
        assertDoesNotThrow(() -> features("mongodb", "mongodb", "none"));
    }

    @Test
    void rejectsMismatchedDatabaseContracts() {
        assertThrows(IllegalArgumentException.class, () -> features("mongodb", "jpa", "flyway"));
        assertThrows(
                IllegalArgumentException.class, () -> features("postgresql", "mongodb", "none"));
        assertThrows(
                IllegalArgumentException.class, () -> features("unsupported", "jpa", "flyway"));
    }

    private ProjectConfiguration.Features features(
            String database, String persistence, String migrations) {
        return new ProjectConfiguration.Features(
                database,
                persistence,
                migrations,
                "none",
                "none",
                "none",
                "standard",
                true,
                true,
                true);
    }
}
