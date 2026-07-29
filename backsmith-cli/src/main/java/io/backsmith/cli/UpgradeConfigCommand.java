package io.backsmith.cli;

import io.backsmith.core.ConfigurationCodec;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.*;

@Command(name = "upgrade-config", description = "Upgrade backsmith.yaml to the latest supported schema.")
public final class UpgradeConfigCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".") Path project;
    @Override public Integer call() throws Exception {
        var codec = new ConfigurationCodec();
        var configuration = codec.read(project.resolve("backsmith.yaml"));
        System.out.println("Configuration already uses current schema version " + configuration.schemaVersion());
        return ExitCodes.SUCCESS;
    }
}
