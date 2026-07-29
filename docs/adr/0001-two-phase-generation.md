# ADR 0001: Two-phase generation

Status: accepted.

Backsmith adapters return rendered intent without performing I/O. The core first creates a complete, sorted plan and only then applies it. This makes dry-run truthful, conflict behavior inspectable, and framework adapters independent of filesystem policy.
