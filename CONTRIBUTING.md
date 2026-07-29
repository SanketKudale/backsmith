# Contributing

Use Java 21 and run `./mvnw clean verify` before opening a pull request. Keep framework-specific logic inside adapters, add tests for safety-sensitive changes, and update `docs/implementation-status.md` only after verification.

Commits should be focused and use clear imperative subjects. New generators must be deterministic, reject path traversal, plan before writing, and default to preserving user code.
