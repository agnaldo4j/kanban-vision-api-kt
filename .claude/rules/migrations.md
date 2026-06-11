---
paths:
  - "**/db/migration/*.sql"
  - "**/migration/**"
---

# Database Migrations — Flyway + PostgreSQL

## Conventions

- **Naming**: `V{N}__{description}.sql` (double underscore). Monotonically increasing `N`. Never reuse a number.
- **Immutability**: never edit an existing migration — Flyway will fail on checksum mismatch. Create a new migration instead.
- **Rollback**: Flyway Community does not support rollback scripts. Design migrations to be forward-only (prefer `ADD COLUMN` over `DROP COLUMN`; prefer nullable additions).
- **Idempotency**: use `IF NOT EXISTS` / `IF EXISTS` guards where possible.

## Current Schema

| Migration | Purpose |
|---|---|
| `V1__initial_schema.sql` | All tables consolidated: `boards`, `steps`, `cards`, `organizations`, `simulations`, `simulation_states`, `daily_snapshots` (post-unification PRs #87–#91) |
| `V2__jsonb_simulation_blobs.sql` | Migrate `simulation_states.state_json` and `daily_snapshots.snapshot_json` from TEXT to JSONB (ADR-0013) |

## Rules

- New migration file = new PR — never bundle schema changes with application code in the same PR without explicit justification.
- Integration tests automatically apply all migrations via `DatabaseFactory` + Embedded PostgreSQL.
- JSON columns (`simulation_states.state_json`, `daily_snapshots.snapshot_json`) use `JSONB` (migrated in V2 — ADR-0013).
- Next available migration number: **V3**.