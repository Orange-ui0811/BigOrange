# Contributing to OrangeVC

## Development rules

- Base work on `V2` unless a release branch is explicitly selected.
- Keep API URLs, response fields, OSS object keys and MCP tool names backward compatible.
- Add an additive Flyway migration for every schema change. Never edit a migration already applied in production.
- Put new business logic in its target module and use constructor injection.
- Use the persistent task center for work that can outlive one HTTP request.
- Never commit credentials, production exports, uploaded files or connector runtime data.

## Required checks

Run the backend test suite and both frontend quality pipelines described in `docs/架构治理实施说明.md`. A pull request must explain compatibility risk, database impact, quota or payment idempotency, manual verification and rollback.
