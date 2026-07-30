# Command reference

Run `backsmith <command> --help` for authoritative options.

- `create`: render and optionally apply a Spring project plan
- `init`: add `backsmith.yaml` to an existing directory
- `doctor`: inspect Java, Git, Docker, writability, and project configuration
- `validate`: parse configuration, verify ownership and project metadata, and optionally format-check, compile, or test
- `upgrade-config`: preview or apply schema migrations while preserving unknown configuration fields
- `diff`: regenerate the plan and optionally show unified text diffs without writing
- component commands: generate architecture-aware modules, domain types, persistence, APIs, migrations, events, integrations, schedulers, infrastructure, tests, and docs

Applicable commands support `--project`, `--dry-run`, `--force`, `--skip-existing`, `--tests`, `--json`, `--quiet`, and `--verbose`.

Create supports all seven architectures; `postgresql`, `mysql`, `mariadb`, `sqlserver`, `oracle`, `h2`, and `mongodb` database targets; and feature selectors for security, messaging, cache, observability, tenancy, deployment, starter modules, API Gateway, and individual resilience policies.

API Gateway options include `--api-gateway`, `--gateway-upstream`, `--gateway-route`, `--gateway-public`, `--[no-]gateway-rate-limiting`, `--gateway-requests-per-minute`, `--gateway-max-request-bytes`, `--gateway-max-header-bytes`, and `--gateway-trusted-proxies`. A non-public gateway automatically selects JWT security when no security mode is supplied. Use `backsmith create --help` for the complete current option set.
