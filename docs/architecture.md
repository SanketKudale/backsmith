# Architecture

Backsmith separates immutable intent, planning, rendering, framework knowledge, and terminal interaction.

```text
CLI -> core planning/apply -> framework adapter -> template engine
             |
             +-> immutable model
```

The core knows nothing about Spring. An adapter returns relative paths and rendered content; the planner resolves those paths under a normalized project root, hashes content, compares existing files, and emits sorted operations. The applier refuses unresolved conflicts and writes through same-directory temporary files followed by atomic replacement where supported.

This boundary lets future adapters reuse safety behavior without copying filesystem logic.
