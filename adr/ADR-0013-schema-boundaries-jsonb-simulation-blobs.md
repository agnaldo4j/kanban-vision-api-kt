# ADR-0013 — Schema Boundaries e Migração TEXT → JSONB para Blobs de Simulação

## Cabeçalho

| Campo     | Valor                                                        |
|-----------|--------------------------------------------------------------|
| Status    | Aceita                                                       |
| Data      | 2026-06-11                                                   |
| Autores   | @agnaldo4j                                                   |
| Branch    | feat/gap-m-adr-0013-schema-boundaries                        |
| Gap       | GAP-M (ADR-0004 — Ciclo Excelência P4)                       |
| Supersede | —                                                            |

---

## Contexto e Motivação

O schema atual do projeto possui duas colunas que armazenam blobs JSON como `TEXT`:

| Tabela               | Coluna         | Conteúdo                                         |
|----------------------|----------------|--------------------------------------------------|
| `simulation_states`  | `state_json`   | Grafo completo da simulação (organização + cenário + board + cards) |
| `daily_snapshots`    | `snapshot_json`| Snapshot diário: métricas de fluxo + movimentos  |

O ADR-0004 (GAP-M) identificou dois problemas relacionados:

1. **Sem queryabilidade por campo interno**: colunas `TEXT` não permitem filtrar por campos
   do JSON (ex.: encontrar snapshots onde `metrics.throughput > 5`) sem deserializar no
   servidor de aplicação, o que é ineficiente e não escalável.

2. **Sem boundaries de schema documentados**: as tabelas do bounded context **Kanban
   Management** (`boards`, `steps`, `cards`) e do bounded context **Simulation**
   (`organizations`, `simulations`, `simulation_states`, `daily_snapshots`) compartilham
   o schema PostgreSQL `public` sem separação formal, o que pode dificultar eventual
   extração de um bounded context para microserviço independente.

Adicionalmente, o ADR-0004 apontava que as PRs #87–#91 (Column→Step rename) poderiam ter
introduzido inconsistência nos blobs legados. **Investigação confirma que não há problema**:
os surrogates de serialização (`StepSurrogate`, `CardSurrogate`, etc.) já usam a terminologia
`step` — o JSON armazenado está consistente com o modelo atual.

---

## Decisão 1 — TEXT → JSONB para Colunas de Blob de Simulação

### Decisão: **Aceita — migrar TEXT para JSONB**

As colunas `simulation_states.state_json` e `daily_snapshots.snapshot_json` serão migradas
de `TEXT` para `JSONB` via migration Flyway V2.

### Justificativa

| Critério | TEXT (atual) | JSONB (proposto) |
|---|---|---|
| Queryabilidade por campo | ❌ Nenhuma — requires app-level deserialize | ✅ Operadores `->`, `->>`, `@>`, `#>` |
| Validação de JSON | ❌ Invalida na leitura, não no write | ✅ PostgreSQL rejeita JSON inválido no INSERT |
| Suporte a índice | ❌ Apenas B-tree no TEXT inteiro | ✅ GIN index para queries complexas |
| Performance de leitura | ✅ Retorna texto direto | ✅ Equivalente (binário parseado) |
| Storage | ✅ Compacto | ≈ Equivalente (binary, pode ser levemente maior) |
| Custo de migração | — | Baixo: `ALTER COLUMN ... TYPE JSONB USING state_json::jsonb` |
| Mudança em código de aplicação | — | **Zero** — Exposed usa `text()` e PostgreSQL faz cast automático |

**Benefícios concretos:**

- Queries analíticas futuras direto no banco: throughput médio por simulação, distribuição
  de Lead Time, WIP por step — sem round-trip de deserialização na aplicação.
- Fundação técnica para extração futura do SimulationEngine: schema JSONB com estrutura
  conhecida é mais fácil de migrar para colunas normalizadas ou migrar entre DBs.
- Proteção de integridade: blobs malformados são rejeitados no INSERT, não silenciados.
- Habilita GIN index em `daily_snapshots(snapshot_json)` para queries de séries temporais
  quando o volume crescer.

### Alternativas Consideradas

**A — Normalizar `daily_snapshots` em colunas relacionais** (throughput, wip_count, etc.):
Descartado nesta ADR. O schema de `DailySnapshot` ainda está evoluindo e a normalização
representaria um gap estrutural muito maior (ADR separada, múltiplas migrations, refactor
de todos os serializers e repositórios). JSONB é o passo correto agora.

**B — Manter TEXT + queries via `jsonb_path_query`**:
Descartado. O cast implícito funciona mas não valida JSON no write. JSONB nativo é superior.

---

## Decisão 2 — Schema Boundaries: Naming Convention, Não PostgreSQL Schemas

### Decisão: **Manter schema PostgreSQL `public` único; documentar boundaries no Context Map**

### Justificativa

PostgreSQL schemas separados (`kanban.boards`, `sim.simulations`) exigiriam:
1. Configuração `schemas` no Flyway (`flyway.schemas=kanban,sim,public`)
2. Atualização de todas as definições de `Table` no Exposed (prefix `"kanban"."boards"`)
3. Configuração do `search_path` na conexão HikariCP
4. Migrations para criar os schemas e mover tabelas (`ALTER TABLE ... SET SCHEMA ...`)

Para o **monólito modular atual**, este custo não tem retorno imediato. As boundaries
já são comunicadas pelas convenções de nomenclatura:

| Bounded Context    | Tabelas                                               |
|--------------------|-------------------------------------------------------|
| Kanban Management  | `boards`, `steps`, `cards`                            |
| Simulation         | `organizations`, `simulations`, `simulation_states`, `daily_snapshots` |

O `docs/context-map.md` (GAP-T, PR #105) já documenta esses bounded contexts e suas
relações. A separação física de schema só se justifica quando:

1. Um bounded context for extraído para serviço com banco próprio (*Database per Service*), **ou**
2. Times diferentes de desenvolvimento precisarem de permissões independentes no DB.

**Nenhuma das condições se aplica atualmente.**

Quando a extração ocorrer, a migration de separação de schema será uma ADR dedicada
(parte do processo de extração, não um pré-requisito do monólito).

---

## Implementação

### Migration V2

Arquivo: `sql_persistence/src/main/resources/db/migration/V2__jsonb_simulation_blobs.sql`

```sql
ALTER TABLE simulation_states
    ALTER COLUMN state_json TYPE JSONB USING state_json::jsonb;

ALTER TABLE daily_snapshots
    ALTER COLUMN snapshot_json TYPE JSONB USING snapshot_json::jsonb;
```

A cláusula `USING state_json::jsonb` executa o cast explícito de cada linha existente.
Em banco de desenvolvimento (zero dados ou dados de teste), isto é instantâneo.
Em produção com dados existentes, é uma operação `EXCLUSIVE LOCK` por tabela — para volumes
grandes, prefira `ALTER TABLE ... ALTER COLUMN ... SET DATA TYPE` dentro de uma manutenção
programada ou use uma migration multi-passo (ADD COLUMN JSONB, UPDATE, DROP OLD).

### Código de Aplicação

**Zero mudanças necessárias.** O Exposed com coluna `text()` funciona transparentemente
contra uma coluna JSONB — o PostgreSQL JDBC driver lida com o cast automaticamente.
Serializers (`SimulationSerializer`, `DailySnapshotSerializer`) não mudam.

Se no futuro `jsonb()` column type for adicionado ao Exposed (disponível em versões ≥ 1.0.x
como `KotlinxSerializationColumn`), atualizar `Tables.kt` para usar `jsonb()` seria uma
melhoria opcional para habilitar type-safe JSON operators via DSL.

---

## Consequências

**Positivas:**
- Integridade de dados reforçada: JSON inválido é rejeitado no write pelo PostgreSQL.
- Queryabilidade habilitada: Analytics queries futuras (Lead Time distribution, throughput
  por cenário, snapshots por intervalo de WIP) podem ser executadas no banco sem round-trip.
- Fundação para GIN index em `daily_snapshots` quando volume justificar.
- Custo de implementação mínimo: uma migration, zero mudanças em código Kotlin.

**Negativas:**
- `ALTER TABLE ... ALTER COLUMN ... TYPE JSONB` é uma operação de lock exclusivo. Em banco
  com dados reais, requer janela de manutenção ou migration multi-passo.
- Storage pode aumentar levemente (formato binário vs texto UTF-8 compacto para JSONs simples).

**Neutras:**
- Código de aplicação não precisa ser alterado: Exposed e serializers continuam iguais.
- Schema PostgreSQL permanece em `public`: mudança de naming conventions para boundaries
  fica documentada no Context Map, não implementada no banco.

---

## Referências

- ADR-0004 — Avaliação de Qualidade: GAP-M (JSON blob + schema boundaries)
- `docs/context-map.md` — Context Map dos Bounded Contexts (GAP-T, PR #105)
- PostgreSQL docs — [JSONB Type](https://www.postgresql.org/docs/current/datatype-json.html)
- Exposed docs — [DSL Columns](https://www.jetbrains.com/help/exposed/working-with-tables.html)
- Flyway docs — [Migrations](https://documentation.red-gate.com/fd/migrations-184127470.html)
