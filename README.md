<p align="center">
  <img src="docs/assets/backsmith-hero.png" alt="Backsmith — Forge safer backends" width="100%">
</p>

<p align="center">
  <a href="https://github.com/SanketKudale/backsmith/actions/workflows/build.yml"><img alt="Build" src="https://github.com/SanketKudale/backsmith/actions/workflows/build.yml/badge.svg"></a>
  <a href="https://github.com/SanketKudale/backsmith/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/SanketKudale/backsmith"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/SanketKudale/backsmith"></a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk">
  <a href="https://github.com/SanketKudale/backsmith/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/SanketKudale/backsmith/total"></a>
</p>

<p align="center">
  <strong>A deterministic, conflict-aware CLI for forging safer Spring Boot backends.</strong><br>
  <a href="https://sanketkudale.github.io/backsmith/">Website</a> ·
  <a href="https://github.com/SanketKudale/backsmith/releases/latest">Download</a> ·
  <a href="docs/command-reference.md">Commands</a> ·
  <a href="docs/architecture.md">Architecture</a>
</p>

Backsmith is a Java 21 CLI for deterministic, conflict-aware backend project generation. Version 0.1.2 provides the product foundation and a functional Spring Boot adapter for layered and hexagonal starter applications.

> Backsmith is pre-1.0. Generated code and security settings must be reviewed before production use.

## See it in action

<p align="center">
  <img src="docs/assets/backsmith-demo.gif" alt="Backsmith CLI dry-run, creation, entity generation, and validation demo" width="960">
</p>

## What works in 0.1.x

- Maven multi-module CLI built with Picocli
- immutable configuration persisted as `backsmith.yaml`
- deterministic two-phase plan/apply generation
- dry-run, conflict detection, explicit force, skip-existing, JSON, and quiet output
- normalized paths, traversal protection, stable LF output, and atomic replacement
- Spring Boot 3 / Java 21 starter generation
- layered and hexagonal sample layouts
- PostgreSQL, Flyway, JPA, Actuator, OpenAPI, Docker Compose, and GitHub Actions
- project validation, environment diagnostics, config initialization, and component scaffolding
- automated public releases with signed-by-GitHub SHA-256 asset digests

The full product direction is tracked in [implementation status](docs/implementation-status.md). Unfinished integrations and architectures are not presented as supported.

## Install

Java 21 or newer is the only runtime prerequisite.

macOS and Linux:

```shell
curl -fsSL https://github.com/SanketKudale/backsmith/releases/latest/download/install.sh | sh
```

Windows PowerShell:

```powershell
irm https://github.com/SanketKudale/backsmith/releases/latest/download/install.ps1 | iex
```

Homebrew:

```shell
brew install SanketKudale/tap/backsmith
```

Scoop:

```powershell
scoop bucket add sanketkudale https://github.com/SanketKudale/scoop-bucket
scoop install sanketkudale/backsmith
```

JBang:

```shell
jbang app install backsmith@SanketKudale
```

Manual downloads are available from the [latest release](https://github.com/SanketKudale/backsmith/releases/latest). Verify `backsmith.jar`, `backsmith.zip`, or `backsmith.tar.gz` against the published `SHA256SUMS`.

Release archives contain:

```text
backsmith-<version>/
|-- bin/
|   |-- backsmith
|   `-- backsmith.cmd
|-- lib/backsmith.jar
|-- README.md
|-- CHANGELOG.md
`-- LICENSE
```

## Quick start

```shell
backsmith create payment-service \
  --architecture hexagonal \
  --database postgresql \
  --docker \
  --yes
```

Preview without writing:

```shell
backsmith create payment-service --architecture hexagonal --dry-run --yes
```

Add a managed component:

```shell
backsmith module payment --project payment-service
backsmith entity Payment --project payment-service --module payment --field "id:uuid:required"
```

Generator commands report conflicts when a different destination file exists. Use `--force` only when replacement is intentional or `--skip-existing` to preserve it.

## Commands

The CLI exposes `create`, `init`, `doctor`, `validate`, `upgrade-config`, `diff`, `module`, `entity`, `value-object`, `usecase`, `controller`, `repository`, `service`, `api`, `api-dir`, `migration`, `event`, `consumer`, `producer`, `scheduler`, `integration`, `docker`, `ci`, and `docs`.

In 0.1.x, component commands create deterministic typed scaffolds. Domain-specific entity fields, messaging, security, and distributed-system implementations remain roadmap items.

## Repository layout

- `backsmith-cli`: Picocli commands, output, and exit codes
- `backsmith-core`: planning, path safety, hashing, atomic application, and configuration
- `backsmith-model`: immutable generation and configuration records
- `backsmith-template-engine`: replaceable Mustache rendering abstraction
- `backsmith-adapter-spring`: Spring Boot project generator
- `backsmith-testing`: cross-module and distribution verification
- `docs`: architecture, extension guidance, security model, and delivery status
- `site`: public GitHub Pages source

## Build from source

```shell
./mvnw clean verify
java -jar backsmith-cli/target/backsmith.jar --help
java -jar backsmith-cli/target/backsmith.jar doctor
```

On Windows, use `mvnw.cmd`.

## Exit codes

`0` success, `1` general error, `2` invalid arguments, `3` invalid configuration, `4` conflict, `5` generation failure, `6` validation failure, `7` environment problem.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), [architecture](docs/architecture.md), and [framework adapters](docs/framework-adapters.md).

## License

Backsmith is free and open-source software licensed under the [Apache License 2.0](LICENSE).
