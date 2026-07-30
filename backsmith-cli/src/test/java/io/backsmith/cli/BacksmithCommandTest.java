package io.backsmith.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    @Test
    void apiGatewayUsesAuthenticatedSecureDefaults(@TempDir Path output) throws Exception {
        var command = new CommandLine(new BacksmithCommand());

        int exit =
                command.execute(
                        "create",
                        "edge-service",
                        "--project",
                        output.toString(),
                        "--api-gateway",
                        "--yes",
                        "--quiet");

        assertEquals(0, exit);
        String configuration =
                Files.readString(output.resolve("edge-service").resolve("backsmith.yaml"));
        assertTrue(configuration.contains("security: \"jwt\""));
        assertTrue(configuration.contains("api_gateway:"));
        assertTrue(configuration.contains("require_authentication: true"));
        assertTrue(
                Files.exists(
                        output.resolve("edge-service")
                                .resolve(
                                        "src/main/java/com/example/edgeservice/shared/gateway/ApiGatewayConfiguration.java")));
    }
}
