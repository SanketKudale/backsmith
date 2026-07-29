package io.backsmith.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FieldDefinitionTest {
    @Test
    void parsesDecimalConstraints() {
        FieldDefinition field =
                FieldDefinition.parse("amount:decimal:required:precision=19:scale=4");

        assertEquals("amount", field.name());
        assertEquals(FieldDefinition.FieldType.DECIMAL, field.type());
        assertTrue(field.required());
        assertEquals("19", field.options().get("precision"));
        assertEquals("4", field.options().get("scale"));
    }

    @Test
    void parsesEnumValues() {
        FieldDefinition field =
                FieldDefinition.parse("status:enum[CREATED,PROCESSING,COMPLETED]:required");

        assertEquals(FieldDefinition.FieldType.ENUM, field.type());
        assertEquals(3, field.enumValues().size());
    }

    @Test
    void rejectsDecimalWithoutPrecisionAndScale() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FieldDefinition.parse("amount:decimal:required"));
    }
}
