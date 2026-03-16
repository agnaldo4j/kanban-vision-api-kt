# ADR-0003 — Flyway para Gestão de Migrations PostgreSQL

**Status:** Aceita
**Data:** 2026-03-16
**Autor:** agnaldo4j

---

## Contexto

O `DatabaseFactory.kt` criava o schema via DDL inline em 7 métodos privados (`createBoardsTable`, `createColumnsTable`, etc.). Esse padrão:

- Não rastreia versão: não há histórico de qual DDL foi aplicado em qual ambiente.
- Não suporta evolução incremental: qualquer mudança de schema (índices, constraints, colunas) exige modificar o código Kotlin e não deixa rastro auditável.
- Não tem validação de integridade: sem garantia de que o schema em produção bate com o que o código espera.
- Carece de índices nas FKs e `CHECK` constraints nos campos numéricos.

O skill `db-migrations/SKILL.md` documenta a abordagem Flyway adotada neste projeto.

---

## Decisão

Adotar **Flyway 10.x** para gerenciar todas as migrations PostgreSQL.

### Motivações

1. **Rastreabilidade**: `flyway_schema_history` registra versão, checksum, data e resultado de cada migration.
2. **Evolução incremental**: novas migrações adicionam comportamento sem regravar DDL existente.
3. **Validação automática**: Flyway rejeita na inicialização qualquer migration aplicada cujo checksum difira do arquivo em disco — previne deriva silenciosa entre ambientes.
4. **`DatabaseFactory` simplificado**: 137 → ~65 linhas; responsabilidade única (pool + migrations).
5. **Melhorias de schema**: V2 adiciona 4 índices FK e 2 `CHECK` constraints que espelham invariantes do domínio.

### Alternativas consideradas

| Alternativa | Descartada porque |
|---|---|
| Liquibase | Mais pesado (XML/YAML); Flyway é suficiente para SQL puro |
| Manter DDL inline | Sem versionamento, sem histórico, sem validação |
| Exposed Migrations (Jetbrains) | Não compatível com JDBC raw + HikariCP direto |

---

## Consequências

### Positivas

- Schema versionado e auditável por ambiente.
- `DatabaseFactory` enxuto: apenas HikariConfig + `Flyway.migrate()`.
- HikariCP configurado com parâmetros de produção: `connectionTimeout`, `maxLifetime`, `keepaliveTime`, `leakDetectionThreshold`.
- 4 índices FK evitam full-scan nos joins mais frequentes.
- 2 `CHECK` constraints (`wip_limit > 0`, `team_size > 0`) reforçam invariantes do domínio no banco.
- 5 testes de integração `FlywayMigrationIntegrationTest` verificam tabelas, histórico, índices e constraints.

### Negativas / Riscos

| Risco | Mitigação |
|---|---|
| Banco pré-existente sem Flyway (prod/staging) | `baselineOnMigrate=true` + `baselineVersion("1")` na primeira execução |
| `CHECK` constraint em tabela com dados `wip_limit ≤ 0` | Documentado em runbook de deploy — Embedded Postgres inicia vazio em testes |
| `reinitDataSource()` chama `init()` duas vezes | Flyway detecta que não há migrations pendentes e retorna sem erro |

---

## Implementação

### Dependências adicionadas (`sql_persistence/build.gradle.kts`)

```kotlin
implementation("org.flywaydb:flyway-core:10.21.0")
runtimeOnly("org.flywaydb:flyway-database-postgresql:10.21.0")
```

### Arquivos de migration

| Arquivo | Conteúdo |
|---|---|
| `V1__initial_schema.sql` | DDL das 7 tabelas (boards, columns, cards, tenants, scenarios, scenario_states, daily_snapshots) |
| `V2__add_indexes_and_constraints.sql` | 4 índices FK + 2 CHECK constraints |

### DatabaseFactory refatorado

- `createSchema()` substituído por `runMigrations()` com `Flyway.configure()...migrate()`.
- Timeouts extraídos como `private const val` para satisfazer Detekt `MagicNumber`.
- Parâmetros HikariCP de produção adicionados: `connectionTimeout`, `maxLifetime`, `keepaliveTime`, `leakDetectionThreshold`.

### Verificação

```bash
./gradlew :sql_persistence:test   # FlywayMigrationIntegrationTest (5 testes verdes)
./gradlew testAll                  # BUILD SUCCESSFUL — Detekt + KtLint + JaCoCo ≥ 95%
```
