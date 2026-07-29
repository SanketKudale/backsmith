# Configuration reference

Schema version 1 covers `project`, `runtime`, `architecture`, `features`, `modules`, `api`, `security`, `messaging`, `cache`, `observability`, `resilience`, `testing`, `generation`, `deployment`, and `multiTenancy`. See `backsmith.example.yaml`.

Project names use letters, digits, and hyphens and must start with a letter. Base packages must be valid dotted Java packages. Java 21, Spring Boot, Maven, PostgreSQL, JPA, and Flyway are the supported runtime foundation.

Supported architecture values are `layered`, `hexagonal`, `modular-monolith`, `clean`, `onion`, `cqrs`, and `microservice`. Security values are `none`, `basic`, `session`, `jwt`, `oauth2`, and `oidc`; messaging supports `none` or `kafka`; cache supports `none` or `redis`; multi-tenancy supports `none` or `shared-schema`.

`backsmith validate` rejects unsafe or incompatible combinations. `backsmith upgrade-config` migrates older schemas and preserves unknown fields so adapter-specific configuration is not discarded.
