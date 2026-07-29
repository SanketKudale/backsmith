package io.backsmith.cli;

import io.backsmith.core.ConfigurationCodec;
import io.backsmith.core.GenerationPlanner;
import io.backsmith.core.PlanApplier;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "upgrade-config",
        mixinStandardHelpOptions = true,
        description = "Upgrade backsmith.yaml to the latest supported schema.")
public final class UpgradeConfigCommand implements Callable<Integer> {
    @Option(names = "--project", defaultValue = ".")
    Path project;

    @Option(names = "--dry-run")
    boolean dryRun;

    @Option(names = "--json")
    boolean json;

    @Override
    public Integer call() throws Exception {
        var codec = new ConfigurationCodec();
        var upgrade = codec.upgrade(project.resolve("backsmith.yaml"));
        var plan =
                new GenerationPlanner()
                        .plan(
                                project,
                                java.util.Map.of(Path.of("backsmith.yaml"), upgrade.content()),
                                true,
                                false);
        if (json) {
            System.out.printf(
                    "{\"changed\":%s,\"from\":%d,\"to\":%d}%n",
                    upgrade.changed(), upgrade.fromVersion(), upgrade.toVersion());
        } else if (upgrade.changed()) {
            System.out.printf(
                    "Upgrade backsmith.yaml schema %d -> %d%s%n",
                    upgrade.fromVersion(), upgrade.toVersion(), dryRun ? " (dry-run)" : "");
        } else {
            System.out.println("Configuration already uses current schema version 1");
        }
        if (!dryRun && upgrade.changed()) {
            new PlanApplier().apply(plan);
        }
        return ExitCodes.SUCCESS;
    }
}
