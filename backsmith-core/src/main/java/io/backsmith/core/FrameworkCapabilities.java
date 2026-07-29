package io.backsmith.core;

import java.util.Set;

public record FrameworkCapabilities(
        Set<String> runtime,
        Set<String> architectures,
        Set<String> persistence,
        Set<String> security,
        Set<String> messaging,
        Set<String> cache,
        Set<String> deployment) {

    public FrameworkCapabilities {
        runtime = copy(runtime);
        architectures = copy(architectures);
        persistence = copy(persistence);
        security = copy(security);
        messaging = copy(messaging);
        cache = copy(cache);
        deployment = copy(deployment);
    }

    private static Set<String> copy(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
