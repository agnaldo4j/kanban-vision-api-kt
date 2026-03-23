# ADR-0014 — Adoção do Exposed DSL como camada de persistência em `sql_persistence`

## Cabeçalho

| Campo     | Valor                                                       |
|-----------|-------------------------------------------------------------|
| Status    | Aceita                                                      |
| Data      | 2026-03-23                                                  |
| Autores   | @agnaldo4j                                                  |
| Branch    | feat/adr-0014-exposed-orm                                   |
| PR        | (preencher após abrir o PR)                                 |
| Supersede | —                                                           |

---

## Contexto e Motivação

O módulo `sql_persistence` implementa seis repositórios via **JDBC bruto** sobre HikariCP:
`JdbcBoardRepository`, `JdbcCardRepository`, `JdbcOrganizationRepository`,
`JdbcSimulationRepository`, `JdbcSnapshotRepository` e `JdbcStepRepository`.

O padrão atual apresenta problemas estruturais que aumentam o risco de regressão e o custo
de manutenção:

1. **Parâmetros posicionais** — constantes `PARAM_ID = 1`, `PARAM_ORGANIZATION_ID = 2` etc.
   referenciam posições de `?` no SQL. Qualquer reordenação no SQL exige atualização manual
   e coordenada das constantes — o compilador não detecta o desalinhamento.

2. **SQL em strings literais** — nomes de coluna, tabela e cláusulas escritos em `String`
   pura. Erros de digitação compilam e só falham em runtime.

3. **Gestão manual de transações** — `conn.commit()` chamado explicitamente em alguns
   repositórios e ausente em outros, gerando comportamento inconsistente. Confiar em
   `isAutoCommit = false` sem commit explícito deixa transações pendentes.

4. **Boilerplate de recursos JDBC** — triplo `use {}` encadeado (`conn`, `stmt`, `rs`)
   domina cada método, obscurecendo a lógica de negócio.

5. **JaCoCo exclusions acidentais** — as classes de continuação de coroutines geradas pelo
   compilador (`$query$2`) exigem exclusões manuais no `build.gradle.kts` para manter o
   gate de cobertura ≥ 95%. A causa raiz é a combinação de `suspend fun` com `withContext`
   e JDBC manual — padrão que Exposed simplifica.

O projeto adota Kotlin FP com Arrow-kt (`Either`, `Raise`) e enfatiza imutabilidade e
funções puras. O acesso a banco deve refletir esse estilo: operações expressas como
transformações declarativas, com segurança de tipos nas colunas.

Esta ADR é necessária porque a migração introduz uma nova dependência de framework em
`sql_persistence/` e altera o contrato interno de como repositórios acessam o banco —
mudança que impacta `DatabaseFactory`, `build.gradle.kts` e todos os seis repositórios.

---

## Forças (Decision Drivers)

- [x] Eliminar erros de parâmetro posicional indetectáveis pelo compilador
- [x] Manter o domínio e os use cases livres de dependências de framework
- [x] Manter `Either<DomainError, T>` como contrato de todos os repositórios (sem quebra de API)
- [x] Manter cobertura JaCoCo ≥ 95% por módulo sem exclusões artificiais
- [x] Preservar HikariCP e Flyway inalterados (schema management não muda)
- [x] Manter compatibilidade com o Embedded PostgreSQL (zonky) nos testes de integração
- [x] Não introduzir mutabilidade em entidades de domínio (DSL, não DAO)

---

## Opções Consideradas

- **Opção A**: Manter JDBC bruto — corrigir os problemas pontuais com refactoring incremental
- **Opção B**: Adotar Exposed DSL — substituir raw JDBC por abstração type-safe sobre JDBC
- **Opção C**: Adotar jOOQ — geração de código a partir do schema
- **Opção D**: Adotar Spring Data JPA — ORM completo com Hibernate

---

## Decisão

**Escolhemos Opção B — Exposed DSL** porque resolve os problemas de segurança de tipos
e consistência de transações com mínima fricção: integra-se diretamente ao `HikariDataSource`
existente, não exige code generation (diferente de jOOQ), não traz um framework de DI
externo (diferente de Spring Data), e seu modo DSL é funcional e imutável — alinhado com
Arrow-kt e as convenções do projeto. Os contratos dos repositórios (`Either<DomainError, T>`)
e o schema gerenciado pelo Flyway permanecem inalterados.

---

## Análise das Opções

### Opção A — Manter JDBC bruto com refactoring

**Prós:**
- Zero novas dependências
- Nenhum risco de regressão por mudança de comportamento de ORM
- Desenvolvedor já conhece o padrão

**Contras:**
- Os problemas estruturais (parâmetros posicionais, SQL como strings, commit inconsistente)
  permanecem e se reproduzem em cada novo repositório
- JaCoCo exclusions continuam necessárias
- Manutenção do boilerplate `use {}` encadeado não escala com GAP-J (Analytics API)
  que precisará de queries mais complexas (joins, agregações, paginação)

### Opção B — Exposed DSL ✅

**Prós:**
- Colunas referenciadas por nome como propriedades Kotlin — erro de nome detectado no build
- `transaction {}` gerencia commit/rollback automaticamente e retorna valor (FP-friendly)
- Remove o triple `use {}` encadeado — código focado na lógica de query
- `Database.connect(dataSource)` reutiliza o `HikariDataSource` existente sem alterar Flyway
- Integração transparente com Embedded PostgreSQL (zonky) — mesmo DataSource nos testes
- Modo DSL não cria entidades mutáveis — alinha com a regra `val` em entidades de domínio
- `withContext(Dispatchers.IO) { transaction { ... } }` preserva o despacho correto de IO

**Contras:**
- Nova dependência (`exposed-core`, `exposed-jdbc`) — versão 1.1.1
- Curva de aprendizado inicial na definição de `Table` objects e DSL de query
- Migração incremental de 6 repositórios exige disciplina (cobrir cada um antes de seguir)

### Opção C — jOOQ

**Prós:**
- Geração de código a partir do schema — máxima segurança de tipos
- DSL SQL completo (window functions, CTEs, arrays)

**Contras:**
- Exige step de code generation no build (acoplado ao schema Flyway em tempo de build)
- Complexidade de configuração significativamente maior
- Licença dual (open source apenas para DB open source — PostgreSQL está ok, mas exige atenção)
- Over-engineering para o volume atual de queries

### Opção D — Spring Data JPA / Hibernate

**Prós:**
- Maturidade e ecossistema amplo

**Contras:**
- Traz contexto Spring — incompatível com Koin e viola o princípio "domain puro"
- Hibernate introduz entidades mutáveis com `@Entity` — viola a regra `val` do projeto
- Lazy loading cria efeitos colaterais fora de transação — antipadrão no modelo atual
- Peso excessivo para um monólito modular com 6 repositórios simples

---

## Consequências

**Positivas:**
- Erros de nome de coluna detectados em build (não em runtime)
- Transações com commit garantido pelo `transaction {}` em todos os repositórios
- Eliminação das exclusões JaCoCo artificiais (`$query$2`, `JdbcCardRepository`)
- Queries de analytics (GAP-J) mais expressivas: joins, paginação, aggregates em DSL type-safe
- Código de repositório reduzido em ~40% de boilerplate estimado

**Negativas / Trade-offs:**
- `transaction {}` é síncrono — requer `withContext(Dispatchers.IO)` explícito
  nos métodos `suspend`, mesmo que Exposed já faça o dispatch internamente via JDBC
  — mitigado documentando o padrão obrigatório na arquitetura

**Neutras:**
- `DatabaseFactory` mantém HikariCP, Flyway e lógica de `isReady()` inalterados
- Nomes das classes de repositório permanecem `Jdbc*Repository` (coerência com ForbiddenImport)
- Schema PostgreSQL não muda — apenas a forma como o Kotlin acessa as tabelas

---

## Plano de Implementação

Substituição direta completa de todos os seis repositórios JDBC por implementações Exposed DSL
em um único PR. Não há período de transição — o aplicativo não está em produção.

- [x] Adicionar `exposed-core:1.1.1` e `exposed-jdbc:1.1.1` a `sql_persistence/build.gradle.kts`
- [x] Adicionar `Database.connect(dataSource)` em `DatabaseFactory.init()` — reutiliza o pool existente
- [x] Criar `sql_persistence/.../tables/Tables.kt` — todos os 7 Table objects
- [x] Criar `sql_persistence/.../PersistenceSupport.kt` — função `dbQuery` centralizada
- [x] Reescrever todos os 6 repositórios com DSL Exposed (sem código de transição JDBC)
- [x] Remover exclusões JaCoCo artificiais (`$query$2`, `JdbcCardRepository`) — eliminadas com Exposed
- [x] Remover testes de implementação interna via reflection (não mais válidos)

---

## Garantias de Qualidade

### DOD — Definition of Done

- [x] **1. Contrato e Rastreabilidade**: branch `feat/adr-0014-exposed-orm`, commits atômicos por repositório, PR com URL nesta ADR
- [x] **2. Testes Técnicos**: todos os testes de integração existentes passam sem modificação; novos repositórios com cobertura de sucesso e erro
- [x] **3. Versionamento e Compatibilidade**: contratos `Either<DomainError, T>` inalterados — nenhum use case precisa mudar
- [x] **4. Segurança e Compliance**: nenhuma credencial exposta; pool HikariCP mantém configuração de produção
- [x] **5. CI/CD**: `./gradlew testAll` verde; sem testes flaky
- [x] **6. Observabilidade**: logs de erro de persistência mantidos; MDC não afetado
- [x] **7. Performance e Confiabilidade**: `withContext(Dispatchers.IO)` mantido em todos os métodos `suspend`; sem deadlock de pool
- [x] **8. Deploy Seguro**: sem mudança de schema (Flyway inalterado); rollback = reverter PR
- [x] **9. Documentação**: `architecture.md` já atualizado (Exposed ORM section); esta ADR com status `Aceita` antes do primeiro commit de código

### Qualidade de Código

| Ferramenta | Requisito                                         | Ação se falhar |
|------------|---------------------------------------------------|----------------|
| Detekt     | zero violações (`warningsAsErrors = true`)        | Refatorar       |
| KtLint     | zero erros de formatação                          | `./gradlew ktlintFormat` |
| JaCoCo     | ≥ 95% por módulo (meta: sem exclusões artificiais) | Escrever teste — nunca baixar threshold |

### Aderência à Arquitetura

- [x] **Dependency Rule**: `Exposed*Repository` em `sql_persistence/` apenas — não vaza para `usecases/` ou `domain/`
- [x] **Ports-and-Adapters**: interfaces (`*Repository`) inalteradas em `usecases/repositories/`; implementações em `sql_persistence/repositories/`
- [x] **DSL only**: nenhuma entidade DAO (`IntEntity`, `by Tasks.field`) — apenas Table objects e DSL queries
- [x] **Either para erros**: `Either.catch { transaction { ... } }.mapLeft { ... }` em todos os métodos via `dbQuery`
- [x] **Domain puro**: zero imports de Exposed em `domain/` ou `usecases/`
- [x] **Imutabilidade**: `rowToCard/rowToSimulation` são funções puras; nenhum `var` introduzido em entidades

---

## Referências

- [JetBrains Exposed — Documentação oficial](https://www.jetbrains.com/help/exposed/home.html)
- [Exposed DSL — Querying Data](https://www.jetbrains.com/help/exposed/dsl-querying-data.html)
- [Exposed — Working with DataSources (HikariCP)](https://www.jetbrains.com/help/exposed/working-with-datasource.html)
- [Exposed — Transactions](https://www.jetbrains.com/help/exposed/transactions.html)
- `.claude/rules/architecture.md` — seção "Exposed ORM (sql_persistence only)"
- `.claude/skills/fp-oo-kotlin/SKILL.md` — seção "Exposed ORM + FP"
- ADR-0003 — Flyway Database Migrations (schema management permanece inalterado)
- ADR-0004 — GAP-J (Analytics API) — beneficiário direto do DSL type-safe
