# Generator development

Generators produce an in-memory `Map<Path, String>`, never direct writes. Feed output to `GenerationPlanner`, surface every operation, reject conflicts by default, and call `PlanApplier` only after validation. Add tests for deterministic output, an existing modified destination, dry-run behavior, and traversal attempts.
