# Backsmith

Backsmith is a Java 21 CLI for deterministic, conflict-aware backend project generation. Version 0.1.0 provides the foundation of the product and a functional Spring Boot adapter for layered and hexagonal starter applications.

> Backsmith is pre-1.0. Generated code and security settings must be reviewed before production use.

## What works in 0.1.0

- Maven multi-module CLI built with Picocli
- immutable configuration model persisted as `backsmith.yaml`
- two-phase plan/apply generation
- dry-run, conflict detection, explicit force, skip-existing, JSON, and quiet output
- normalized paths, traversal protection, deterministic ordering, LF output, and atomic replacement
- Spring Boot 3 / Java 21 starter generation
- layered and hexagonal sample layouts
- PostgreSQL, Flyway, JPA, Actuator, OpenAPI, Docker Compose, and GitHub Actions setup
- project validation, environment diagnostics, config initialization, and component scaffolding
- unit tests for planning, path safety, adapter determinism, and command parsing

The full product direction is tracked in [implementation status](docs/implementation-status.md). Unfinished integrations and architectures are not presented as supported.

## Build and run

Prerequisite: Java 21. The wrapper downloads Maven 3.9.11 on first use.

```shell
./mvnw clean verify
java -jar backsmith-cli/target/backsmith.jar --help
java -jar backsmith-cli/target/backsmith.jar doctor
```

On Windows, use `mvnw.cmd`.

## Quick start

```shell
java -jar backsmith-cli/target/backsmith.jar create payment-service \
  --architecture hexagonal \
  --database postgresql \
  --docker \
  --yes
```

Preview without writing:

```shell
java -jar backsmith-cli/target/backsmith.jar create payment-service --dry-run --yes
```

Add a managed component from inside a generated project:

```shell
backsmith module payment
backsmith entity Payment --module payment --field "id:uuid:required"
```

Generator commands default to conflicts when a different destination file exists. Use `--force` only when replacement is intentional or `--skip-existing` to preserve it.

## Commands

The CLI exposes `create`, `init`, `doctor`, `validate`, `upgrade-config`, `diff`, `module`, `entity`, `value-object`, `usecase`, `controller`, `repository`, `service`, `api`, `api-dir`, `migration`, `event`, `consumer`, `producer`, `scheduler`, `integration`, `docker`, `ci`, and `docs`.

In 0.1.0, component commands create deterministic typed scaffolds. Domain-specific entity fields, messaging, security, and distributed-system implementations remain roadmap items.

## Repository layout

- `backsmith-cli`: Picocli commands, output, and exit codes
- `backsmith-core`: planning, path safety, hashing, atomic file application, configuration
- `backsmith-model`: immutable generation and configuration records
- `backsmith-template-engine`: replaceable Mustache rendering abstraction
- `backsmith-adapter-spring`: Spring Boot project generator
- `backsmith-testing`: home for cross-module fixtures and generated-project verification
- `docs`: architecture, extension guidance, security model, and delivery status

## Exit codes

`0` success, `1` general error, `2` invalid arguments, `3` invalid configuration, `4` conflict, `5` generation failure, `6` validation failure, `7` environment problem.

## Development

```shell
./mvnw clean verify
```

See [architecture](docs/architecture.md), [framework adapters](docs/framework-adapters.md), and [contributing](CONTRIBUTING.md).

## License

Apache License 2.0.
