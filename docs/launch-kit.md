# Backsmith launch kit

Use these drafts when announcing a verified release. Update the version and one concrete capability before posting. Avoid claiming roadmap features as shipped.

## Short description

Backsmith is a free, open-source Java 21 CLI that generates Spring Boot backend foundations through a deterministic, conflict-aware plan/apply workflow.

## One-line pitch

Forge safer Spring Boot backends with a generation plan you can inspect before any file is written.

## DEV Community / blog post

### Title

Introducing Backsmith: a conflict-aware CLI for generating Spring Boot backends

### Draft

Backend generators are useful until they overwrite code, hide architecture decisions, or produce a project that cannot compile. I built Backsmith around a different contract: plan first, show every operation, reject conflicts by default, and only apply a validated deterministic plan.

The first supported stack is Java 21, Spring Boot, Maven, PostgreSQL, Flyway, JPA, OpenAPI, Docker, and GitHub Actions. Backsmith currently generates layered and hexagonal starter applications and records generation decisions in `backsmith.yaml`.

Try a no-write preview:

```shell
backsmith create payment-service --architecture hexagonal --dry-run --yes
```

Backsmith is Apache-2.0 licensed and pre-1.0, so limitations are documented openly. Contributions and architecture feedback are welcome.

- Website: https://sanketkudale.github.io/backsmith/
- Source: https://github.com/SanketKudale/backsmith
- Latest release: https://github.com/SanketKudale/backsmith/releases/latest

## Hacker News

### Title

Show HN: Backsmith – a deterministic, conflict-aware Spring Boot generator

### Text

I built Backsmith because backend generators often treat the filesystem as disposable. Backsmith creates a complete in-memory plan, hashes existing and rendered content, reports CREATE/UPDATE/SKIP/CONFLICT operations, and refuses different existing files by default.

The initial release supports Java 21 + Spring Boot with layered and hexagonal starters. It is pre-1.0 and Apache-2.0 licensed. I would especially value feedback on its adapter boundary and conflict model.

https://github.com/SanketKudale/backsmith

## Reddit

### Suggested communities

- r/java
- r/SpringBoot
- r/opensource
- r/programming (only if its self-promotion rules are satisfied)

### Title

I built an open-source, conflict-aware Spring Boot backend generator

### Text

Backsmith is a Java 21 CLI that plans generation before writing, supports dry-run and JSON output, and treats changed existing files as conflicts. The first working adapter generates layered and hexagonal Spring Boot starters with PostgreSQL, Flyway, JPA, OpenAPI, Docker, and CI.

It is intentionally pre-1.0, and the README distinguishes shipped functionality from the roadmap. I would appreciate feedback from people maintaining generated or scaffolded Spring applications.

## LinkedIn

I have released Backsmith, a free and open-source Java 21 CLI for generating safer Spring Boot backend foundations.

Its core rule is simple: plan every filesystem operation, show conflicts, and never silently overwrite user code. The first release supports layered and hexagonal starters with PostgreSQL, Flyway, JPA, OpenAPI, Docker, and GitHub Actions.

Try it, inspect the architecture, or contribute:
https://github.com/SanketKudale/backsmith

#java #springboot #opensource #backenddevelopment #developerTools

## Submission checklist

- Confirm the latest release workflow is green.
- Run both public installers from clean temporary locations.
- Confirm the website and package-manager commands resolve.
- Use an accurate current screenshot or demo.
- Read each community's self-promotion rules immediately before posting.
- Answer questions with current implementation facts, not roadmap promises.
