package io.backsmith.core;

import java.util.Set;

public final class AdapterCapabilities {
    private AdapterCapabilities() {}

    public interface Persistence {
        Set<String> persistenceProviders();
    }

    public interface Messaging {
        Set<String> messagingProviders();
    }

    public interface Security {
        Set<String> securityModes();
    }

    public interface Cache {
        Set<String> cacheProviders();
    }

    public interface Observability {
        Set<String> observabilityLevels();
    }

    public interface Deployment {
        Set<String> deploymentTargets();
    }

    public interface Api {
        Set<String> apiStyles();
    }

    public interface Testing {
        Set<String> testingCapabilities();
    }
}
