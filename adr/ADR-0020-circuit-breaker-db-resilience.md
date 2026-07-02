# ADR-0020 — Circuit Breaker para DB: Resiliência de Pool HikariCP

## Cabeçalho

| Campo     | Valor                        |
|-----------|------------------------------|
| Status    | Aceita                       |
| Data      | 2026-06-20                   |
| Execução  | 2026-07-02 — implementada em 2 PRs |
| Autores   | @agnaldo4j                   |
| Branch    | `feat/gap-aj-db-circuit-breaker` (PR1) · `feat/gap-aj-health-metrics` (PR2) |
| PR        | #208 (breaker + 503) · #209 (health + métricas) |
| Supersede | —                            |

### Desvios registrados na execução

| Previsto na ADR | Implementado | Motivo |
|---|---|---|
| `DomainError` em `usecases/.../errors/` | `domain/errors/DomainError.kt` | Localização real do sealed class |
| Mapeamento 503 em `StatusPages.kt` | `adapters/EitherRespond.kt` | O `when` exaustivo de `DomainError` vive ali; StatusPages só trata exceptions |
| `HealthCheckUseCase` | Composição em `plugins/Routing.kt` (`!DbCircuitBreaker.isOpen() && DatabaseFactory.isReady()`) | Classe não existe; readiness é uma closure passada a `healthRoutes` |
| Wiring em `AppModule.kt` | `DatabaseFactory` + `DbCircuitBreaker` (objects) | Repositórios não recebem DataSource via Koin; `dbQuery` é função top-level |
| Registro nas 2 camadas | Registro só em `dbQuery`; `CircuitBreakerDataSource` é gate puro por estado | Registrar 2× (retries do Exposed × camadas) abria o circuito com ~2 operações; gate não disputa permits de HALF_OPEN |
| `resilience4j-kotlin` | Omitido | Nenhum ponto de suspensão dentro do breaker (`executeSupplier` bloqueante em `Dispatchers.IO`) |
| Config sem transição automática OPEN→HALF_OPEN | `automaticTransitionFromOpenToHalfOpenEnabled(true)` | Com o pod fora do load balancer (readiness 503), nenhum tráfego atravessa o breaker — sem a transição automática o circuito ficaria OPEN para sempre após a recuperação do banco (review Codex no PR #209) |
| 1 PR único | 2 PRs sequenciais | Limite J-Curve de 400 linhas por PR |

---

## Contexto e Motivação

A dimensão **Prontidão para Produção** está em 8.5/10. Uma das lacunas remanescentes é a ausência
de **circuit breaker** para a conexão com o banco de dados. O HikariCP está configurado com
parâmetros de produção (timeout, max lifetime, leak detection), mas não há mecanismo que impeça
o pool de saturar quando o PostgreSQL está indisponível ou lento.

**Cenário de falha sem circuit breaker:**
1. PostgreSQL fica lento (CPU alta, lock contention, rede saturada)
2. Todas as conexões do pool ficam aguardando (`connectionTimeout = 30s` por padrão no HikariCP)
3. Requisições HTTP começam a se acumular aguardando conexão disponível
4. Pool de threads do Netty satura — a API inteira fica irresponsiva para requisições que não
   precisam de banco (ex.: `GET /health/live`, `GET /metrics`)
5. O Kubernetes interpreta o liveness probe como falha e reinicia o pod — cascata de reinícios

**Com circuit breaker:**
1. PostgreSQL fica lento → primeiras falhas incrementam contador de erros
2. Ao atingir threshold (50% falha em janela de 10 requests), circuito abre
3. Requisições subsequentes ao banco retornam imediatamente com `DomainError.ServiceUnavailable`
4. `GET /health/ready` responde `503 Service Unavailable` (readiness probe falha → pod sai do load balancer)
5. `GET /health/live` continua respondendo `200 OK` (pod não é reiniciado)
6. Após `waitDurationInOpenState`, circuito entra em estado half-open para testar recuperação

Este gap é do tipo `[E]` Estrutural porque:
- Adiciona nova dependência de biblioteca (`resilience4j`)
- Altera o contrato de retorno de todos os repositórios (novo `DomainError.ServiceUnavailable`)
- Modifica a infraestrutura de wiring em `AppModule` (decoração do `DataSource`)

---

## Gap Coberto

| GAP | Título | Tipo |
|-----|--------|------|
| GAP-AJ | Circuit breaker para DB (resilience4j) | E |

---

## Decisão

### Biblioteca: resilience4j

**Escolhida:** `io.github.resilience4j:resilience4j-kotlin` + `resilience4j-circuitbreaker` + `resilience4j-micrometer`

**Razões:**
- Biblioteca padrão para resiliência em JVM com suporte nativo a Kotlin (coroutines-aware)
- Integração com Micrometer já presente no projeto (exporta estado CB para Prometheus)
- Sem dependência de framework — funciona com qualquer biblioteca de DI (compatível com Koin)
- Leve: não traz container IoC próprio ou AOP bytecode instrumentation

**Alternativas descartadas:**
- **Ktor retry plugin:** lida com retry de requests HTTP, não com falhas de pool de DB
- **HikariCP health check:** só reporta o estado, não corta o circuito
- **Kotlin Resilience (fork):** menos maduro, sem comunidade ampla

### Arquitetura: Decorator no DataSource

Não decorar repositórios individuais — o circuit breaker deve envolver o `DataSource` no nível
de infraestrutura. Desta forma:
- Um único circuit breaker protege todos os repositórios
- A abertura do circuito é visível em `GET /health/ready` sem acoplamento entre camadas
- Os repositórios não precisam saber sobre o circuit breaker

```kotlin
// sql_persistence/src/main/kotlin/com/kanbanvision/persistence/CircuitBreakerDataSource.kt
class CircuitBreakerDataSource(
    private val delegate: DataSource,
    private val circuitBreaker: CircuitBreaker,
) : DataSource by delegate {

    override fun getConnection(): Connection =
        circuitBreaker.executeSupplier { delegate.connection }

    override fun getConnection(username: String, password: String): Connection =
        circuitBreaker.executeSupplier { delegate.getConnection(username, password) }
}
```

> **Escopo e limitação:** `CircuitBreakerDataSource` intercepta apenas o checkout de conexão do
> pool — protege contra exaustão do pool HikariCP (quando todas as conexões estão bloqueadas
> aguardando o banco). Porém, se o PostgreSQL aceita a conexão mas executa queries lentamente
> (ex.: lock contention, full table scan), o `getConnection()` retorna rápido e o circuito
> **não abre** para esse cenário.
>
> Para proteção completa contra queries lentas, os repositórios devem também envolver o bloco
> `transaction { }` com `circuitBreaker.executeSupplier { }`:
>
> ```kotlin
> // Exemplo em JdbcSimulationRepository.kt
> fun save(simulation: Simulation): Either<DomainError, Unit> =
>     either {
>         catch({
>             circuitBreaker.executeSupplier {
>                 transaction { SimulationsTable.insert { /* ... */ } }
>             }
>         }) { e -> raise(DomainError.PersistenceError(e.message ?: "DB error")) }
>     }
> ```
>
> A implementação do GAP-AJ deve incluir ambas as camadas: `CircuitBreakerDataSource` como
> proteção de pool + injeção do `CircuitBreaker` nos repositórios para proteção de operação.

### Configuração do Circuit Breaker

```kotlin
// sql_persistence/src/main/kotlin/com/kanbanvision/persistence/DatabaseConfig.kt
fun buildCircuitBreaker(): CircuitBreaker =
    CircuitBreaker.of(
        "database",
        CircuitBreakerConfig.custom()
            .slidingWindowType(COUNT_BASED)
            .slidingWindowSize(10)
            .failureRateThreshold(50f)           // 50% falhas → abre circuito
            .slowCallRateThreshold(80f)           // 80% lentas → também abre
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .recordExceptions(SQLException::class.java, SQLTimeoutException::class.java)
            .build(),
    )
```

### Novo DomainError

```kotlin
// usecases/src/main/kotlin/com/kanbanvision/usecases/errors/DomainError.kt
sealed class DomainError {
    // ... erros existentes ...
    data class ServiceUnavailable(val service: String, val reason: String) : DomainError()
}
```

Mapeamento em `respondWithDomainError()`:
```kotlin
is DomainError.ServiceUnavailable -> call.respond(
    HttpStatusCode.ServiceUnavailable,
    mapOf("error" to "Service temporarily unavailable", "service" to it.service)
)
```

### Integração com Health Check

O `GET /health/ready` deve verificar o estado do circuit breaker:

```kotlin
// usecases/src/main/kotlin/com/kanbanvision/usecases/health/HealthCheckUseCase.kt
// Injetar CircuitBreaker via porta — verificar estado no ready check
val cbState = circuitBreaker.state
if (cbState == CircuitBreaker.State.OPEN) {
    raise(DomainError.ServiceUnavailable("database", "circuit breaker open"))
}
```

### Métricas Prometheus

Com `resilience4j-micrometer`, registrar o CB no `MeterRegistry` existente:

```kotlin
TaggedCircuitBreakerMetrics
    .ofCircuitBreakerRegistry(circuitBreakerRegistry)
    .bindTo(meterRegistry)
```

Métricas geradas automaticamente:
- `resilience4j_circuitbreaker_state` (gauge: 0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- `resilience4j_circuitbreaker_calls_total` (counter por outcome)
- `resilience4j_circuitbreaker_slow_calls_total`

---

## Plano de Implementação

**1 sessão LLM — 1 PR:**

1. Adicionar dependências em `sql_persistence/build.gradle.kts`:
   ```kotlin
   val resilience4jVersion = "2.3.0"
   implementation("io.github.resilience4j:resilience4j-kotlin:$resilience4jVersion")
   implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
   implementation("io.github.resilience4j:resilience4j-micrometer:$resilience4jVersion")
   ```
2. Criar `sql_persistence/.../CircuitBreakerDataSource.kt`
3. Criar `sql_persistence/.../DatabaseConfig.kt` com `buildCircuitBreaker()`
4. Adicionar `DomainError.ServiceUnavailable` em `usecases/`
5. Atualizar `respondWithDomainError()` em `http_api/` com mapeamento `503`
6. Atualizar `AppModule.kt`: `CircuitBreakerDataSource(hikariDataSource, buildCircuitBreaker())`
7. Atualizar `HealthCheckUseCase` para verificar estado do CB
8. Registrar métricas via `TaggedCircuitBreakerMetrics`
9. Testes: simular falha de DB via mock de `DataSource`, verificar que CB abre e retorna `ServiceUnavailable`
10. Verificar JaCoCo ≥ 97%, Detekt 0, `./gradlew testAll` green

**Arquivos modificados:**
- `sql_persistence/build.gradle.kts` (dependências)
- `sql_persistence/.../CircuitBreakerDataSource.kt` (criar)
- `sql_persistence/.../DatabaseConfig.kt` (criar ou atualizar)
- `usecases/.../errors/DomainError.kt` (adicionar `ServiceUnavailable`)
- `http_api/.../plugins/StatusPages.kt` (mapear `503`)
- `http_api/.../di/AppModule.kt` (wiring CB)
- `usecases/.../health/HealthCheckUseCase.kt` (verificar estado CB)

---

## Consequências

**Positivas:**
- Prontidão para Produção sobe de 8.5 → 9.0+
- Proteção contra cascata de falhas quando PostgreSQL está lento/indisponível
- Pod sai do load balancer via readiness probe quando circuito está aberto (sem reinícios desnecessários)
- Métricas de estado do circuit breaker visíveis no Grafana dashboard existente

**Negativas:**
- Nova dependência de biblioteca (`resilience4j`)
- Adiciona `DomainError.ServiceUnavailable` — todos os `when` exhaustivos em rotas precisam de novo `is`
- Testes de integração com banco embarcado precisam simular abertura do circuito via mock de `DataSource`

**Neutras:**
- O `CircuitBreakerDataSource` é transparente para os repositórios (Kotlin `by` delegation)
- Configuração via env vars permite ajuste de thresholds por ambiente sem recompilação

---

## Referências

- resilience4j: https://resilience4j.readme.io/docs/circuitbreaker
- HikariCP: https://github.com/brettwooldridge/HikariCP
- Fowler, Martin. *CircuitBreaker*. https://martinfowler.com/bliki/CircuitBreaker.html
- Skill: [clean-architecture](.claude/skills/clean-architecture/SKILL.md)
- Skill: [fp-oo-kotlin](.claude/skills/fp-oo-kotlin/SKILL.md)
- Skill: [testing-and-observability](.claude/skills/testing-and-observability/SKILL.md)
