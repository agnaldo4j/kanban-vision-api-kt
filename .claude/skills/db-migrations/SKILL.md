---
name: db-migrations
description: >
  Gerencie o ciclo de vida do schema PostgreSQL com Flyway neste projeto.
  Use ao criar novas migrations, alterar tabelas existentes, depurar conflitos
  de checksum ou planejar mudanças de schema. Cobre Flyway naming conventions,
  estratégia forward-only, HikariCP e o módulo sql_persistence.
argument-hint: "[migration task or schema change description]"
allowed-tools: Read, Grep, Glob, Bash
---

# Database Migrations e Estrutura PostgreSQL

> **Escopo deste skill:** como gerenciar o ciclo de vida do schema PostgreSQL com
> Flyway, como configurar o HikariCP para produção, e como aplicar esses conceitos
> no módulo `sql_persistence` deste projeto.
>
> Referências:
> - Flyway: https://documentation.red-gate.com/flyway/getting-started-with-flyway
> - PostgreSQL: https://www.postgresql.org/docs/current/index.html
> - HikariCP: https://github.com/brettwooldridge/HikariCP

---

## ⛔ REGRA ABSOLUTA — Configurações de Qualidade São Intocáveis

Esta skill **não autoriza** editar nenhum arquivo de configuração de qualidade.
Se uma migração introduzir código que viole Detekt/KtLint/JaCoCo, corrija o
código — nunca abaixe os thresholds.

---

## 1. Por que Migrar do `CREATE TABLE IF NOT EXISTS` para Flyway

O `DatabaseFactory.createSchema()` atual usa DDL idempotente inline. Isso funciona
para a criação inicial, mas tem limitações sérias:

| Situação | Abordagem atual | Com Flyway |
|---|---|---|
| Criar tabela nova | `CREATE TABLE IF NOT EXISTS` | `V2__add_new_table.sql` |
| Adicionar coluna | `ALTER TABLE` manual fora dos guards | `V3__add_column_to_table.sql` |
| Remover coluna | Sem controle — muda silenciosamente | Versão com `DROP COLUMN` rastreada |
| Auditar o que rodou em prod | Impossível | Tabela `flyway_schema_history` |
| CI/CD com banco limpo | Vai funcionar | Vai funcionar + histórico |
| CI/CD com banco existente | Risco de inconsistência | Apenas pendentes são executados |
| Rollback | Manual, sem rastreio | `UNDO` migration ou script reverso |

---

## 2. Flyway — Conceitos Essenciais

### Naming Convention dos Arquivos SQL

```
<Prefix><Version>__<Description>.sql
```

| Parte | Versioned | Repeatable | Exemplo |
|---|---|---|---|
| Prefix | `V` | `R` | `V`, `R` |
| Version | `1`, `1_1`, `2_0_1` | _(nenhuma)_ | `V1`, `V1_1` |
| Separador | `__` | `__` | **duplo** underscore — erro comum é usar simples |
| Description | snake_case | snake_case | `create_boards_table` |
| Sufixo | `.sql` | `.sql` | configurável |

**Exemplos para este projeto:**
```
src/main/resources/db/migration/
├── V1__initial_schema.sql              ← DDL inicial (boards, columns, cards, tenants)
├── V2__simulation_tables.sql           ← scenarios, scenario_states, daily_snapshots
├── V3__add_board_description.sql       ← ALTER TABLE futura
└── R__seed_service_classes.sql         ← dados de referência (re-roda se mudar)
```

### Versioned vs Repeatable

```sql
-- V3__add_board_description.sql — roda uma vez, nunca mais
ALTER TABLE boards ADD COLUMN IF NOT EXISTS description TEXT NOT NULL DEFAULT '';

-- R__seed_service_classes.sql — roda toda vez que o conteúdo mudar
TRUNCATE service_classes;
INSERT INTO service_classes (name, priority) VALUES ('Expedite', 1), ('Standard', 2);
```

### Local padrão no classpath

```
sql_persistence/src/main/resources/db/migration/
```

Flyway escaneia `classpath:db/migration` por padrão — este diretório é incluído
automaticamente no JAR de recursos do módulo.

---

## 3. Integrando Flyway no Projeto

### 3.1 Dependências (`sql_persistence/build.gradle.kts`)

```kotlin
dependencies {
    // Flyway core (API e runner)
    implementation("org.flywaydb:flyway-core:10.23.0")
    // PostgreSQL support — obrigatório a partir do Flyway 10
    runtimeOnly("org.flywaydb:flyway-database-postgresql:10.23.0")

    // manter HikariCP e postgres driver já existentes
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.5")
}
```

### 3.2 Integrando no `DatabaseFactory.kt`

```kotlin
import org.flywaydb.core.Flyway

object DatabaseFactory {
    lateinit var dataSource: HikariDataSource
        private set

    fun init(config: DatabaseConfig) {
        dataSource = HikariDataSource(buildHikariConfig(config))
        runMigrations()
    }

    private fun runMigrations() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)   // false: banco deve estar vazio ou já ter histórico Flyway
            .validateOnMigrate(true)    // rejeita se checksum de migration já rodada mudou
            .load()
            .migrate()
    }

    // ... buildHikariConfig() — ver seção 5
}
```

### 3.3 Migrando do Schema Inline para Flyway

**Passo 1** — criar `V1__initial_schema.sql` com o DDL atual:

```sql
-- sql_persistence/src/main/resources/db/migration/V1__initial_schema.sql

CREATE TABLE IF NOT EXISTS boards (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS columns (
    id         VARCHAR(36)  PRIMARY KEY,
    board_id   VARCHAR(36)  NOT NULL REFERENCES boards(id),
    name       VARCHAR(255) NOT NULL,
    position   INT          NOT NULL
);

CREATE TABLE IF NOT EXISTS cards (
    id          VARCHAR(36)  PRIMARY KEY,
    column_id   VARCHAR(36)  NOT NULL REFERENCES columns(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    position    INT          NOT NULL,
    created_at  BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS tenants (
    id   VARCHAR(36)  PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS scenarios (
    id         VARCHAR(36) PRIMARY KEY,
    tenant_id  VARCHAR(36) NOT NULL REFERENCES tenants(id),
    wip_limit  INT         NOT NULL,
    team_size  INT         NOT NULL,
    seed_value BIGINT      NOT NULL
);

CREATE TABLE IF NOT EXISTS scenario_states (
    scenario_id VARCHAR(36) PRIMARY KEY REFERENCES scenarios(id),
    state_json  TEXT        NOT NULL
);

CREATE TABLE IF NOT EXISTS daily_snapshots (
    scenario_id   VARCHAR(36) NOT NULL REFERENCES scenarios(id),
    day           INT         NOT NULL,
    snapshot_json TEXT        NOT NULL,
    PRIMARY KEY (scenario_id, day)
);
```

**Passo 2** — remover `createSchema()` e seus métodos auxiliares do `DatabaseFactory`.

**Passo 3** — para banco existente em produção/staging, usar `baseline`:

```kotlin
// Apenas na primeira vez em banco que já tem o schema sem histórico Flyway:
Flyway.configure()
    .dataSource(dataSource)
    .baselineOnMigrate(true)    // cria flyway_schema_history marcando V1 como já executada
    .baselineVersion("1")
    .load()
    .migrate()
// Desative baselineOnMigrate = true após a primeira execução
```

---

## 4. Padrões PostgreSQL para Migrations

### 4.1 Tipos de Dados — Escolhas para Este Projeto

| Uso | Tipo atual | Tipo recomendado | Motivo |
|---|---|---|---|
| IDs (UUIDs) | `VARCHAR(36)` | `UUID` | Armazenamento nativo, comparação eficiente |
| Timestamps | `BIGINT` (epoch ms) | `BIGINT` | Mantém compatibilidade com código atual |
| JSON estruturado | `TEXT` | `JSONB` | Indexação, consultas internas, validação |
| Nomes curtos | `VARCHAR(255)` | `VARCHAR(255)` | Adequado |
| Conteúdo longo | `TEXT` | `TEXT` | Sem limite — correto |

**Nota:** migrar `VARCHAR(36)` para `UUID` e `TEXT` para `JSONB` são melhorias
válidas para uma `V3` futura — não altere tabelas existentes sem uma migration versionada.

### 4.2 Constraints e Índices

```sql
-- Chave primária composta (já existe em daily_snapshots)
PRIMARY KEY (scenario_id, day)

-- Índice para consultas frequentes
CREATE INDEX idx_daily_snapshots_scenario_id ON daily_snapshots(scenario_id);
CREATE INDEX idx_cards_column_id ON cards(column_id);

-- NOT NULL com DEFAULT — preferível a nullable
description TEXT NOT NULL DEFAULT ''

-- Constraint de verificação (adicionar em migration futura)
ALTER TABLE scenarios ADD CONSTRAINT check_wip_limit_positive CHECK (wip_limit > 0);
ALTER TABLE scenarios ADD CONSTRAINT check_team_size_positive CHECK (team_size > 0);
```

### 4.3 ALTER TABLE — Padrões Seguros

```sql
-- Adicionar coluna com valor default (não bloqueia leituras no PG 11+)
ALTER TABLE boards ADD COLUMN IF NOT EXISTS archived_at BIGINT;

-- Adicionar NOT NULL com default (em duas etapas para tabelas grandes)
ALTER TABLE boards ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active';
-- Depois, em V_next: ALTER TABLE boards ALTER COLUMN status DROP DEFAULT;

-- Renomear coluna (não use em produção sem coordenação com o código)
ALTER TABLE boards RENAME COLUMN name TO title;

-- Criar índice sem bloquear escrita
CREATE INDEX CONCURRENTLY idx_boards_created_at ON boards(created_at);
```

### 4.4 Transações em Migrations

Flyway envolve cada migration `.sql` em uma transação automaticamente.
**Exceção:** `CREATE INDEX CONCURRENTLY` não pode rodar dentro de uma transação.

```sql
-- V4__add_concurrent_index.sql
-- flyway: outOfOrder=false, executeInTransaction=false  ← anotação no header do arquivo

CREATE INDEX CONCURRENTLY idx_cards_position ON cards(position);
```

Para desabilitar transação automática em um arquivo específico, adicione no topo:
```sql
-- flyway: executeInTransaction=false
```

---

## 5. HikariCP — Configuração de Produção

### 5.1 Configuração Recomendada para Este Projeto

```kotlin
private fun buildHikariConfig(config: DatabaseConfig): HikariConfig =
    HikariConfig().apply {
        jdbcUrl          = config.url
        driverClassName  = config.driver
        username         = config.user
        password         = config.password

        // Pool sizing — fórmula PostgreSQL: (core_count * 2) + effective_spindle_count
        // Para a maioria das VMs/containers: 10 é adequado
        maximumPoolSize  = config.poolSize   // default 10

        // Timeouts críticos
        connectionTimeout = 30_000           // 30 s — max espera por conexão do pool
        maxLifetime       = 1_800_000        // 30 min — deve ser < timeout do firewall/VPC
        keepaliveTime     = 120_000          // 2 min — mantém idle connections vivas

        // Comportamento da transação
        isAutoCommit          = false
        transactionIsolation  = "TRANSACTION_REPEATABLE_READ"

        // Detecção de leak (ative em staging/produção)
        leakDetectionThreshold = 60_000      // 60 s — log warning se conexão ficar aberta

        // Pool name — aparece nos logs e no JMX
        poolName = "KanbanVisionPool"

        validate()
    }
```

### 5.2 Propriedades para Não Configurar

| Propriedade | Motivo |
|---|---|
| `connectionTestQuery` | PostgreSQL driver suporta JDBC4 `isValid()` — mais eficiente |
| `minimumIdle` | Deixar igual a `maximumPoolSize` (pool de tamanho fixo) — mais previsível |
| `idleTimeout` | Só relevante quando `minimumIdle < maximumPoolSize` |

### 5.3 URL de Conexão PostgreSQL Recomendada

```
jdbc:postgresql://host:5432/dbname?sslmode=require&ApplicationName=kanban-vision
```

| Parâmetro | Valor | Motivo |
|---|---|---|
| `sslmode=require` | produção | Sempre criptografar em produção |
| `sslmode=disable` | testes locais | Embedded PostgreSQL não usa SSL |
| `ApplicationName` | `kanban-vision` | Visível em `pg_stat_activity` — facilita debugging |
| `connectTimeout=10` | 10 s | Timeout de conexão TCP (diferente do HikariCP `connectionTimeout`) |

---

## 6. Estratégias de Teste com Flyway

### 6.1 `IntegrationTestSetup` — Sem Mudança de Interface

O `IntegrationTestSetup` existente usa `DatabaseFactory.init(...)`. Com Flyway,
as migrations rodam automaticamente ao chamar `init()`. Nenhuma mudança nos testes
de integração é necessária — exceto garantir que os arquivos `.sql` estejam no
classpath de teste.

```kotlin
// IntegrationTestSetup — continua igual
object IntegrationTestSetup {
    fun ensureInitialized() {
        if (!initialized) {
            val pg = EmbeddedPostgres.start()
            val config = DatabaseConfig(
                url = pg.getJdbcUrl("postgres", "postgres"),
                driver = "org.postgresql.Driver",
                user = "postgres",
                password = "postgres",
            )
            DatabaseFactory.init(config)   // agora chama Flyway internamente
            initialized = true
        }
    }
}
```

### 6.2 Validar Schema em Teste

```kotlin
@Test
fun `flyway migrations create all expected tables`() = runBlocking {
    // Verifica que as tabelas existem após init()
    IntegrationTestSetup.ensureInitialized()
    val conn = DatabaseFactory.dataSource.connection
    val tables = listOf("boards", "columns", "cards", "tenants",
                        "scenarios", "scenario_states", "daily_snapshots")
    tables.forEach { table ->
        val result = conn.createStatement()
            .executeQuery("SELECT to_regclass('public.$table')")
        result.next()
        assertNotNull(result.getString(1), "Tabela '$table' não existe")
    }
    conn.close()
}
```

### 6.3 Limpeza Entre Testes — `cleanTables()` vs `flywayClean()`

```kotlin
// ✅ cleanTables() — apaga dados, mantém schema (use em @BeforeEach)
fun cleanTables() {
    DatabaseFactory.dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            // ordem inversa das FK constraints
            stmt.executeUpdate("DELETE FROM daily_snapshots")
            stmt.executeUpdate("DELETE FROM scenario_states")
            stmt.executeUpdate("DELETE FROM scenarios")
            stmt.executeUpdate("DELETE FROM cards")
            stmt.executeUpdate("DELETE FROM columns")
            stmt.executeUpdate("DELETE FROM boards")
            stmt.executeUpdate("DELETE FROM tenants")
        }
        conn.commit()
    }
}

// ❌ flywayClean() em testes — destrói schema e re-migra, muito lento para @BeforeEach
```

---

## 7. Adicionando Novas Migrations — Checklist

### Ao criar uma nova migration

- [ ] Nome segue a convenção: `V{N}__{descricao_em_snake_case}.sql`
- [ ] Versão é maior que todas as existentes (sem gaps necessários, mas sem sobreposição)
- [ ] DDL é idempotente onde possível (`ADD COLUMN IF NOT EXISTS`, `CREATE TABLE IF NOT EXISTS`)
- [ ] Índices grandes usam `CONCURRENTLY` com `executeInTransaction=false`
- [ ] Constraints de NOT NULL em tabelas existentes têm DEFAULT para não bloquear
- [ ] `./gradlew :sql_persistence:test` verde após a migration

### Ao adicionar coluna em tabela existente

```sql
-- V5__add_archived_at_to_boards.sql
ALTER TABLE boards ADD COLUMN IF NOT EXISTS archived_at BIGINT;
```

### Ao criar índice sem lock

```sql
-- V6__add_index_cards_column_id.sql
-- flyway: executeInTransaction=false
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cards_column_id ON cards(column_id);
```

### Ao renomear coluna (breaking change — coordenar com código)

Estratégia expand-contract (3 etapas):
```sql
-- V7__expand_add_new_column_name.sql
ALTER TABLE boards ADD COLUMN title VARCHAR(255);
UPDATE boards SET title = name WHERE title IS NULL;
ALTER TABLE boards ALTER COLUMN title SET NOT NULL;

-- (deploy do código que usa nova coluna)

-- V8__contract_remove_old_column_name.sql
ALTER TABLE boards DROP COLUMN name;
```

---

## 8. Flyway vs Liquibase — Quando Usar Cada Um

| Critério | Flyway | Liquibase |
|---|---|---|
| **Formato principal** | SQL puro | XML/YAML/JSON + SQL |
| **Curva de aprendizado** | Baixa — SQL que você já conhece | Maior — DSL própria |
| **Rollback automático** | Requer script manual (`U1__undo.sql`) | Suporte nativo via `<rollback>` |
| **Migrations condicionais** | Não nativo | Suporte via `preconditions` |
| **Diff de schema** | Pro/Teams (pago) | Open-source via `generateChangelog` |
| **Suporte PostgreSQL** | Excelente | Excelente |
| **Integração com Spring** | `spring-boot-starter-flyway` | `spring-boot-starter-liquibase` |
| **Recomendação para este projeto** | ✅ **Flyway** | — |

**Recomendação:** Flyway é a escolha certa para este projeto por três razões:
1. SQL puro — sem DSL intermediária, sem geração de XML/YAML
2. Menor surface area — apenas `flyway-core` + `flyway-database-postgresql`
3. Fácil integração com a API Kotlin (`Flyway.configure().dataSource(ds).load().migrate()`)

---

## 9. Troubleshooting Comum

### `FlywayValidateException: Checksum mismatch`

**Causa:** arquivo de migration já executado foi editado.

```bash
# Opção 1: reparar histórico (remove entradas com checksum incorreto)
./gradlew :sql_persistence:flywayRepair   # se usar Gradle plugin

# Opção 2: no código (apenas em desenvolvimento)
flyway.repair()
```

**Regra:** **nunca edite** um arquivo `V` após ele ser executado em qualquer ambiente.
Crie uma nova versão `V{N+1}` com a correção.

### `FlywayException: Found non-empty schema without schema history table`

**Causa:** banco tem tabelas mas nunca teve Flyway — migração do projeto sem Flyway.

```kotlin
// Uma vez apenas — em DatabaseFactory.init() antes de remover esta linha
Flyway.configure()
    .dataSource(dataSource)
    .baselineOnMigrate(true)
    .baselineVersion("1")
    .load()
    .migrate()
```

### `MigrationExecutionException: ERROR: relation already exists`

**Causa:** migration usou `CREATE TABLE` sem `IF NOT EXISTS`, banco já tem a tabela.

```sql
-- ❌ vai falhar se tabela existe
CREATE TABLE boards (...);

-- ✅ idempotente
CREATE TABLE IF NOT EXISTS boards (...);
```

### Pool de Conexões Esgotado (`Connection is not available, request timed out`)

**Causa mais comum:** conexão nunca fechada — `connection.use { }` não usado.

```kotlin
// ❌ vaza conexão
val conn = dataSource.connection
val result = conn.createStatement().executeQuery("SELECT ...")
// conn nunca fechada!

// ✅ use { } garante fechamento mesmo em exceção
dataSource.connection.use { conn ->
    conn.createStatement().use { stmt ->
        val result = stmt.executeQuery("SELECT ...")
        // result processado aqui
    }
    conn.commit()
}
```

Para diagnosticar: ative `leakDetectionThreshold = 60_000` no HikariCP.
O log vai mostrar o stack trace de onde a conexão foi aberta e nunca fechada.

---

## 10. Referência Rápida

```bash
# Rodar migrations (ocorre automaticamente no init())
# Manualmente via Gradle plugin (se configurado):
./gradlew :sql_persistence:flywayMigrate

# Ver status das migrations
./gradlew :sql_persistence:flywayInfo

# Validar checksums
./gradlew :sql_persistence:flywayValidate

# Reparar histórico (desenvolvimento)
./gradlew :sql_persistence:flywayRepair

# Limpar schema (APENAS desenvolvimento — destrutivo!)
./gradlew :sql_persistence:flywayClean
```

```
Localização dos arquivos de migration:
sql_persistence/src/main/resources/db/migration/

Tabela de controle criada pelo Flyway:
public.flyway_schema_history

Ordem de execução:
Flyway lê versões em ordem crescente (1, 2, 3...) — independente da ordem dos arquivos no disco
```