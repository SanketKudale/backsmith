package io.backsmith.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class BacksmithCommandTest {
    @Test
    void helpIsAvailable() {
        var output = new java.io.StringWriter();
        var command = new CommandLine(new BacksmithCommand());
        command.setOut(new java.io.PrintWriter(output));
        assertEquals(0, command.execute("--help"));
        assertTrue(output.toString().contains("Deterministic and safe"));
    }
}
