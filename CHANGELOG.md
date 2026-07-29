# Changelog

All notable changes follow [Keep a Changelog](https://keepachangelog.com/) and Semantic Versioning.

## [1.0.0] - 2026-07-29

### Added

- Production-safe transactional generation with rollback, ownership hashes, history, module metadata, and API-contract tracking.
- Layered, hexagonal, modular-monolith, clean, onion, CQRS, and microservice project layouts.
- Architecture-aware modules, entities, value objects, use cases, controllers, repositories, services, migrations, OpenAPI contracts, events, messaging, schedulers, integrations, Docker, CI, and documentation generators.
- JWT authentication starter with persistent accounts, BCrypt, role and tenant claims, refresh rotation, logout, and current-account endpoints.
- Customer and payment starter modules with tenant-scoped persistence, optimistic locking, money semantics, lifecycle validation, idempotency, audit, and outbox integration.
- Kafka event envelopes, transactional outbox retries and dead state, idempotent consumers, Redis cache, Resilience4j policies, metrics, tracing, structured logs, and Kubernetes resources.
- Generated Testcontainers and ArchUnit tests, code quality gates, dependency convergence, CodeQL, dependency review, and a seven-architecture CI matrix.
- Optional signed Maven Central publication in the release workflow.
- PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, H2, and MongoDB project generation with database-specific dependencies, configuration, persistence scaffolding, Compose services, and integration tests.

### Changed

- `diff`, `validate`, `doctor`, and `upgrade-config` now perform real project-aware diagnostics and migrations.
- Spring generation targets Java 21 and Spring Boot 3.5.16.
- Relational SQL and idempotency/outbox persistence are portable across the supported SQL engines; MongoDB projects use Spring Data MongoDB without Flyway.

### Security

- Added environment-only secrets, RFC-style security errors, JWT issuer/audience validation, tenant membership enforcement, correlation IDs, request hashing, and safe external-integration errors.

## [0.1.2] - 2026-07-29

### Added

- Public project website and search/social metadata.
- Homebrew, Scoop, and JBang distribution catalogs with automated updates.
- Installation, support, issue, pull request, launch, and SDKMAN onboarding documentation.
- Complete Apache License 2.0 text, NOTICE file, citation metadata, and branded project assets.

## [0.1.1] - 2026-07-29

### Fixed

- Make the Windows installer tolerate Java's standard version output on stderr.
- Make fresh-runner release version validation ignore Maven wrapper bootstrap output.

## [0.1.0] - 2026-07-29

### Added

- Multi-module Java 21 CLI foundation.
- Safe deterministic plan/apply engine with dry-run and conflict handling.
- Versioned YAML configuration.
- Spring Boot layered and hexagonal project generation.
- Doctor, validation, initialization, diff guidance, and component commands.
- Docker, PostgreSQL, Flyway, OpenAPI, health configuration, and CI templates.
- Unit test suite and project documentation.
- Automated public GitHub Releases with executable JAR, cross-platform archives, SHA-256 checksums, and checksum-verifying installers.
