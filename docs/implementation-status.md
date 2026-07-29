# Implementation status

This checklist is intentionally conservative. Checked items have automated or manual verification in 0.1.0.

## Phase 1 — foundation

- [x] Maven multi-module repository
- [x] immutable configuration and generation models
- [x] CLI shell and stable exit codes
- [x] YAML loading and validation messages
- [x] deterministic two-phase generation plan
- [x] dry-run, conflicts, force, and skip-existing
- [x] normalized safe paths and atomic writes
- [x] basic ownership manifest
- [ ] transactional multi-file rollback after an unexpected mid-apply failure
- [ ] complete per-file ownership history and partial-file structured merges

## Phase 2 — Spring creation

- [x] Spring adapter boundary
- [x] layered starter project
- [x] hexagonal starter project
- [x] PostgreSQL, JPA, Flyway, OpenAPI, Docker, health configuration
- [x] generated Maven wrapper and CI workflow
- [ ] complete production security and generated Testcontainers repository suite

## Phase 3 — component generators

- [x] all command names exposed
- [x] safe deterministic typed component scaffold
- [ ] domain-aware entity, repository, use-case, controller, migration, and API generators

## Later phases

- [ ] authentication, authorization, idempotency, audit, and multi-tenancy
- [ ] Kafka, transactional outbox, resilience, external integrations, and Redis
- [ ] modular monolith, clean, onion, CQRS, and microservice implementations
- [ ] full generated-project architecture matrix and golden tests
- [ ] publishing and automated release workflow

These unchecked items are roadmap work and are not claimed as supported.
