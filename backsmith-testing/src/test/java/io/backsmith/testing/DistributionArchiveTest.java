package io.backsmith.testing;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class DistributionArchiveTest {
    @Test
    void zipContainsExecutableJarLaunchersAndLegalFiles() throws Exception {
        Path cliTarget = Path.of("..", "backsmith-cli", "target").toAbsolutePath().normalize();
        Path archive;
        try (var files = Files.list(cliTarget)) {
            archive =
                    files.filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .matches("backsmith-.+-bin\\.zip"))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "Backsmith distribution ZIP was not built"));
        }

        try (var zip = new ZipFile(archive.toFile())) {
            String root =
                    zip.stream()
                            .map(entry -> entry.getName().split("/", 2)[0])
                            .filter(name -> name.startsWith("backsmith-"))
                            .findFirst()
                            .orElseThrow();
            assertNotNull(zip.getEntry(root + "/lib/backsmith.jar"));
            assertNotNull(zip.getEntry(root + "/bin/backsmith.cmd"));
            assertNotNull(zip.getEntry(root + "/README.md"));
            assertNotNull(zip.getEntry(root + "/LICENSE"));
            assertNotNull(zip.getEntry(root + "/NOTICE"));

            var unixLauncher = zip.getEntry(root + "/bin/backsmith");
            assertNotNull(unixLauncher);
            try (InputStream stream = zip.getInputStream(unixLauncher)) {
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(content.contains("java -jar"));
                assertTrue(content.contains("../lib/backsmith.jar"));
            }
        }
    }
}
