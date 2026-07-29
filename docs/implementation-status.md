# Implementation status

This checklist is intentionally conservative. Checked items are implemented in 1.0.0 and covered by source or generated-project verification.

## Phase 1 — foundation

- [x] Maven multi-module repository
- [x] immutable configuration and generation models
- [x] CLI shell and stable exit codes
- [x] YAML loading and validation messages
- [x] deterministic two-phase generation plan
- [x] dry-run, conflicts, force, and skip-existing
- [x] normalized safe paths and atomic writes
- [x] basic ownership manifest
- [x] transactional multi-file rollback after an unexpected mid-apply failure
- [x] per-file hashes, generator version, timestamps, history, modules, and API-contract metadata
- [ ] structured partial-file merging; Backsmith intentionally owns complete generated files in 1.0

## Phase 2 — Spring creation

- [x] Spring adapter boundary
- [x] layered starter project
- [x] hexagonal starter project
- [x] PostgreSQL, JPA, Flyway, OpenAPI, Docker, health configuration
- [x] generated Maven wrapper and CI workflow
- [x] security modes, Problem Details, correlation IDs, audit hooks, and generated Testcontainers tests

## Phase 3 — component generators

- [x] all command names exposed
- [x] safe deterministic typed component scaffold
- [x] domain-aware entity, repository, use-case, controller, migration, and OpenAPI generators

## Production features

- [x] persistent JWT authentication, roles, refresh rotation, idempotency, audit, and shared-schema tenancy
- [x] Kafka, transactional outbox, idempotent consumers, resilience, external integrations, and Redis
- [x] modular monolith, clean, onion, CQRS, and microservice layouts
- [x] generated-project architecture matrix and full-feature build
- [x] public GitHub Releases with cross-platform archives, launchers, checksums, and installers
- [x] Homebrew, Scoop, and JBang publication automation
- [x] Maven Central publication automation when signing and Central credentials are configured
- [ ] SDKMAN vendor onboarding, which requires approval by the external SDKMAN service

Unchecked items are explicit boundaries, not silently simulated features.
