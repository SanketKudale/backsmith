package io.backsmith.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "doctor", description = "Check the local Backsmith development environment.")
public final class DoctorCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".") Path project;
    @Option(names = "--json") boolean json;

    @Override
    public Integer call() throws Exception {
        var checks = new LinkedHashMap<String, Object>();
        int javaVersion = Runtime.version().feature();
        checks.put("java", MapResult.of(javaVersion >= 21, "Java " + javaVersion));
        checks.put("git", executable("git"));
        checks.put("docker", executable("docker"));
        checks.put("writable", MapResult.of(Files.isWritable(project.toAbsolutePath()), project.toAbsolutePath().toString()));
        checks.put("backsmithProject", MapResult.of(Files.exists(project.resolve("backsmith.yaml")),
                Files.exists(project.resolve("backsmith.yaml")) ? "configuration found" : "not in a Backsmith project"));
        boolean requiredHealthy = javaVersion >= 21 && ((MapResult) checks.get("git")).ok();
        if (json) {
            System.out.println(new ObjectMapper().writeValueAsString(checks));
        } else {
            checks.forEach((name, result) -> {
                var value = (MapResult) result;
                System.out.printf("%-18s %s  %s%n", name, value.ok ? "OK" : "WARN", value.detail);
            });
        }
        return requiredHealthy ? ExitCodes.SUCCESS : ExitCodes.ENVIRONMENT_PROBLEM;
    }

    private static MapResult executable(String name) {
        try {
            var process = new ProcessBuilder(name, "--version").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes()).lines().findFirst().orElse(name);
            return MapResult.of(process.waitFor() == 0, output);
        } catch (Exception exception) {
            return MapResult.of(false, name + " not available");
        }
    }

    private record MapResult(boolean ok, String detail) {
        static MapResult of(boolean ok, String detail) { return new MapResult(ok, detail); }
        public boolean isOk() { return ok; }
        public String getDetail() { return detail; }
    }
}
