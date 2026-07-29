# Template authoring

Backsmith uses Mustache behind `TemplateRenderer`. Templates must be deterministic, use LF line endings, contain no executable scripts, and never construct absolute or parent-relative paths. Keep framework templates inside their adapter until external template-pack trust and metadata rules are implemented.
