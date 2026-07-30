# Configuration reference

Schema version 1 covers `project`, `runtime`, `architecture`, `features`, `modules`, `api`, `security`, `messaging`, `cache`, `observability`, `resilience`, `testing`, `generation`, `deployment`, `multi_tenancy`, and `api_gateway`. See `backsmith.example.yaml`.

Project names use letters, digits, and hyphens and must start with a letter. Base packages must be valid dotted Java packages. Java 21, Spring Boot, and Maven are the supported runtime foundation.

Supported `features.database` values are `postgresql`, `mysql`, `mariadb`, `sqlserver`, `oracle`, `h2`, and `mongodb`. Relational databases use `features.persistence: jpa` and `features.migrations: flyway`. MongoDB uses `features.persistence: mongodb` and `features.migrations: none`; the CLI selects those values automatically when `--database mongodb` is used. H2 is generated only when explicitly selected and is not used in place of a production database during integration tests.

Each database selection controls the Maven driver and Flyway database module, application URL and credentials, SQL type placeholders, Docker Compose service, Kubernetes connection URL, and generated integration test. The container-backed tests use the selected database engine rather than an in-memory compatibility mode.

Supported architecture values are `layered`, `hexagonal`, `modular-monolith`, `clean`, `onion`, `cqrs`, and `microservice`. Security values are `none`, `basic`, `session`, `jwt`, `oauth2`, and `oidc`; messaging supports `none` or `kafka`; cache supports `none` or `redis`; multi-tenancy supports `none` or `shared-schema`.

`api_gateway` controls an optional Spring Cloud Gateway Server MVC route. `route_path` must be absolute and end in `/**`. `upstream_uri` must be an HTTP(S) origin without embedded credentials, query, or fragment. Authenticated gateways require a non-`none` security mode. Rate, request-body, and request-header limits must be positive; request headers are capped at 1 MiB. `trusted_proxies` is a Java regular expression, not CIDR notation.

`backsmith validate` rejects unsafe or incompatible combinations. `backsmith upgrade-config` migrates older schemas and preserves unknown fields so adapter-specific configuration is not discarded.
