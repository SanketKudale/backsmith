package io.backsmith.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.backsmith.core.ProjectValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "doctor", description = "Check the local Backsmith development environment.")
public final class DoctorCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".")
    Path project;

    @Option(names = "--json")
    boolean json;

    @Override
    public Integer call() throws Exception {
        var checks = new LinkedHashMap<String, Object>();
        int javaVersion = Runtime.version().feature();
        checks.put("java", MapResult.of(javaVersion >= 21, "Java " + javaVersion));
        checks.put(
                "operatingSystem",
                MapResult.of(
                        true, System.getProperty("os.name") + " " + System.getProperty("os.arch")));
        checks.put("git", executable("git"));
        checks.put("maven", maven(project));
        checks.put("docker", executable("docker"));
        checks.put(
                "terminal",
                MapResult.of(
                        true,
                        System.console() == null
                                ? "non-interactive terminal; use --yes"
                                : "interactive terminal"));
        checks.put(
                "templates",
                MapResult.of(
                        io.backsmith.adapter.spring.SpringBootAdapter.class.getResource(
                                        "SpringBootAdapter.class")
                                != null,
                        "built-in Spring templates"));
        checks.put(
                "writable",
                MapResult.of(
                        Files.isWritable(project.toAbsolutePath()),
                        project.toAbsolutePath().toString()));
        if (Files.exists(project.resolve("backsmith.yaml"))) {
            var report = new ProjectValidator().validate(project);
            checks.put(
                    "backsmithProject",
                    MapResult.of(
                            report.valid(),
                            report.valid()
                                    ? "configuration and ownership manifest valid"
                                    : report.issues().size()
                                            + " validation issue(s); run backsmith validate"));
        } else {
            checks.put(
                    "backsmithProject",
                    MapResult.of(true, "not inside a Backsmith project; project checks skipped"));
        }
        boolean requiredHealthy =
                javaVersion >= 21
                        && ((MapResult) checks.get("git")).ok()
                        && ((MapResult) checks.get("maven")).ok()
                        && ((MapResult) checks.get("writable")).ok()
                        && ((MapResult) checks.get("templates")).ok()
                        && ((MapResult) checks.get("backsmithProject")).ok();
        if (json) {
            System.out.println(new ObjectMapper().writeValueAsString(checks));
        } else {
            checks.forEach(
                    (name, result) -> {
                        var value = (MapResult) result;
                        System.out.printf(
                                "%-18s %s  %s%n", name, value.ok ? "OK" : "WARN", value.detail);
                    });
        }
        return requiredHealthy ? ExitCodes.SUCCESS : ExitCodes.ENVIRONMENT_PROBLEM;
    }

    private static MapResult executable(String name) {
        try {
            var process = new ProcessBuilder(name, "--version").redirectErrorStream(true).start();
            String output =
                    new String(process.getInputStream().readAllBytes())
                            .lines()
                            .findFirst()
                            .orElse(name);
            return MapResult.of(process.waitFor() == 0, output);
        } catch (Exception exception) {
            return MapResult.of(false, name + " not available");
        }
    }

    private static MapResult maven(Path project) {
        Path wrapper =
                project.resolve(
                        System.getProperty("os.name").toLowerCase().contains("win")
                                ? "mvnw.cmd"
                                : "mvnw");
        if (Files.isRegularFile(wrapper)) {
            return MapResult.of(true, "project Maven wrapper: " + wrapper.toAbsolutePath());
        }
        return executable("mvn");
    }

    private record MapResult(boolean ok, String detail) {
        static MapResult of(boolean ok, String detail) {
            return new MapResult(ok, detail);
        }

        public boolean isOk() {
            return ok;
        }

        public String getDetail() {
            return detail;
        }
    }
}
