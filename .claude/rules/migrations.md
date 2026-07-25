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

## Refinar o tipo de um campo já persistido — decode tolerante a legado

Os blobs JSON (`state_json`, `snapshot_json`) são **registro imutável de leitura**: contêm dados gravados por
releases anteriores, quando a borda podia aceitar valores que hoje seriam inválidos. Ao **refinar o tipo de um
campo serializado** — trocar um `String` cru por um value class / smart constructor com invariante (ex.:
`NonBlankTitle`, GAP-DH #355) — o `require`/`init` do novo tipo passa a rodar **também sobre o histórico**.

- **Ponto cego:** a borda de *entrada* nova (DTO/domínio) guarda o valor; o **decode** (`toDomain`/`surrogate`)
  não. Um único valor legado inválido (ex.: um `ADD_ITEM` de título em branco gravado antes do guard) faz o
  decode **lançar** → `Either.catch` → `PersistenceError` → a **linha/agregado inteiro** fica não-carregável
  (`findById`/`findAll` viram 500), não só o campo.
- **Regra:** o decode deve ser **tolerante a legado**. Coaja o valor inválido a um **sentinel** (ex.:
  `decodeTitle()` → `"(untitled)"`) ou converta em erro tipado — nunca deixe o `require` do value class lançar
  cru. Cubra com um teste dedicado ("legacy blank … decodes to a sentinel instead of crashing the load").
- **Alternativa (quando cabe uma migração):** um data-fix Flyway forward-only que sanitize o histórico
  (`UPDATE … SET … WHERE …` sobre o JSONB) — mas só depois de **auditar** que tais registros existem; se o
  campo *sempre* teve guard (ex.: `Card.init` nunca deixou blank), não há legado a tolerar e nada a migrar.