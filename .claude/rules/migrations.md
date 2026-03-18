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
| `V1__initial_schema.sql` | Core tables: `boards`, `columns`, `cards`, `tenants`, `scenarios`, `scenario_states`, `daily_snapshots` |
| `V2__add_indexes_and_constraints.sql` | Performance indexes + FK constraints |
| `V3__unique_column_name_per_board.sql` | `UNIQUE(board_id, name)` on `columns` table — enforces Board aggregate invariant at DB level |

## Rules

- New migration file = new PR — never bundle schema changes with application code in the same PR without explicit justification.
- Integration tests automatically apply all migrations via `DatabaseFactory` + Embedded PostgreSQL.
- JSON columns (`scenario_states.state`, `daily_snapshots.snapshot`) use `TEXT` — queryability improvement is tracked as GAP-M.
- Next available migration number: **V4**.