package io.backsmith.cli;

import io.backsmith.core.BacksmithVersion;
import picocli.CommandLine.IVersionProvider;

public final class BacksmithVersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[] {"Backsmith " + BacksmithVersion.current()};
    }
}
