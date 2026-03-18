---
name: testing-and-observability
description: >
  Guia de qualidade para testes e observabilidade neste projeto: JUnit 5, MockK,
  SLF4J, MDC e regras Detekt de comentários. Use ao escrever testes novos, configurar
  logging, propagar contexto de rastreamento ou documentar código público.
  Complementa kotlin-quality-pipeline (Detekt/KtLint/JaCoCo/Gradle).
argument-hint: "[use case, route or class to write tests for (optional)]"
allowed-tools: Read, Grep, Glob, Bash
---

# Testes e Observabilidade — JUnit · MockK · SLF4J · MDC · Detekt Comments

> **Escopo deste skill:** como escrever testes corretos, como mockar dependências,
> como registrar logs úteis e como propagar contexto entre camadas.
> Para pipeline de build (Detekt estrutural, KtLint, JaCoCo, Gradle), veja
> o skill `kotlin-quality-pipeline`.

---

## ⛔ REGRA ABSOLUTA — Configurações de Qualidade São Intocáveis

**A IA NUNCA deve modificar nenhum arquivo de configuração de qualidade do projeto.**

Esta regra é não negociável e se aplica a qualquer situação, inclusive quando
o build está falhando por violação de alguma ferramenta.

### Arquivos protegidos — nunca editar

| Arquivo | Ferramenta | O que configura |
|---|---|---|
| `config/detekt/detekt.yml` | Detekt | Thresholds, regras ativas, padrões de nomenclatura |
| `.editorconfig` | KtLint / editores | Estilo de código, tamanho de linha, ordenação de imports |
| `buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts` | Convention plugin | JaCoCo gate (90%), JUnit platform, versões de ferramentas |
| `**/build.gradle.kts` | Gradle | Exclusões de JaCoCo por módulo, dependências de teste |
| `gradle.properties` | Gradle | Versão do Java, flags da JVM |

### A resposta correta quando o build falha por qualidade

```
Build falhou por Detekt / KtLint / JaCoCo?
         │
         ├── Detekt → refatore o CÓDIGO para eliminar a violação
         │
         ├── KtLint → rode ./gradlew ktlintFormat e ajuste o código
         │
         └── JaCoCo → escreva o TESTE que cobre o caminho faltante
```

**Nunca:**
- Aumentar um threshold (`LongMethod`, `CyclomaticComplexMethod`, `LargeClass`, etc.)
- Baixar o gate de cobertura (mínimo 90% é fixo)
- Adicionar exclusões no JaCoCo sem aprovação explícita do time
- Desativar uma regra do Detekt
- Adicionar `@Suppress` sem justificativa documentada no código

**Se o código legítimo requer exceção** (ex: DSL declarativa que não pode ser
dividida sem perder legibilidade), documente no PR e aguarde aprovação humana —
não ajuste a configuração de forma autônoma.

### Por que esta regra existe

As configurações de qualidade representam o contrato coletivo do time sobre
padrões de código. Alterá-las de forma autônoma:
- Mascara problemas de design ao invés de resolvê-los
- Cria inconsistência invisível entre o que o time acordou e o que está configurado
- Viola a confiança no pipeline automatizado de CI/CD

---

## 1. Arquitetura de Testes no Projeto

Três camadas de teste, cada uma com responsabilidade distinta:

```
┌──────────────────────────────────────────────────────────┐
│  Testes de Rota (http_api)                               │
│  testApplication { install(Koin) + configureXxx() }     │
│  MockK para repositórios · verifica HTTP status + JSON  │
├──────────────────────────────────────────────────────────┤
│  Testes de Use Case (usecases)                           │
│  JUnit 5 puro · MockK para repositórios                  │
│  Verifica lógica de orquestração e erro de domínio      │
├──────────────────────────────────────────────────────────┤
│  Testes de Integração (sql_persistence)                  │
│  Embedded PostgreSQL (zonky) · runBlocking               │
│  Verifica SQL real, serialização JSON, erros de DB      │
└──────────────────────────────────────────────────────────┘
```

**Regra fundamental**: cada camada testa somente o que lhe pertence.
- Testes de use case **nunca** sobem servidor HTTP.
- Testes de rota **nunca** tocam banco de dados real.
- Testes de integração **nunca** testam lógica de negócio.

---

## 2. JUnit 5 — Padrões do Projeto

### Versão em uso

O projeto usa **JUnit 5.11.4** (`junit-jupiter`).
Documentação: https://junit.org/junit5/docs/current/user-guide/

### Imports corretos

```kotlin
// ✅ JUnit 5 — use kotlin.test para assertions (mais idiomático em Kotlin)
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertFails

// lifecycle hooks — JUnit 5 nativo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
```

### Nomeação de testes

Backticks são a convenção — devem descrever comportamento, não implementação:

```kotlin
// ✅ descreve o que acontece e em que condição
@Test fun `POST boards creates board and returns 201`()
@Test fun `POST boards with blank name returns 400`()
@Test fun `GET boards by id returns 404 when not found`()
@Test fun `unexpected repository error returns 500`()

// ❌ descreve implementação, não comportamento
@Test fun testCreateBoard()
@Test fun testCreateBoardSuccess()
@Test fun shouldReturnError()
```

### Estrutura obrigatória: os três caminhos

Todo caso de uso e toda rota deve ter testes para os três caminhos:

```kotlin
// ✅ caminho 1 — happy path
@Test fun `POST boards creates board and returns 201`()

// ✅ caminho 2 — validação de entrada (erro do cliente)
@Test fun `POST boards with blank name returns 400`()

// ✅ caminho 3 — erro de dependência (repositório, banco, serviço externo)
@Test fun `unexpected repository error returns 500`()
```

Para use cases com `NotFound`, adicione:
```kotlin
// ✅ caminho 4 — recurso não encontrado
@Test fun `GET board returns 404 when not found`()
```

### Ciclo de vida — integração com banco

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  // uma instância para toda a classe
class JdbcBoardRepositoryIntegrationTest {

    @BeforeAll
    fun initDatabase() {
        IntegrationTestSetup.ensureInitialized()  // sobe Embedded PostgreSQL uma vez
    }

    @BeforeEach
    fun cleanDatabase() {
        IntegrationTestSetup.cleanTables()        // limpa dados entre testes
    }
}
```

**Por que `PER_CLASS`?** Evita subir o banco múltiplas vezes.
`ensureInitialized()` é idempotente — seguro chamar em cada classe.

### Corrotinas em testes de integração

```kotlin
// ✅ usar runBlocking para chamar suspend functions em testes de integração
@Test
fun `save persists board and findById returns it`() = runBlocking {
    val board = newBoard()
    repository.save(board)
    val result = repository.findById(board.id)
    assertTrue(result.isRight())
}

// ✅ em testes de rota — testApplication já lida com corrotinas
@Test
fun `POST boards creates board and returns 201`() = testApplication {
    // testApplication é uma suspend lambda — não precisa de runBlocking
}
```

### Assertions com Either

```kotlin
// ✅ verificar resultado Right
val result = repository.findById(board.id)
assertTrue(result.isRight())
val found = result.getOrNull()
assertNotNull(found)
assertEquals(board.id, found.id)

// ✅ verificar resultado Left com tipo específico
val result = repository.findById(BoardId("nonexistent"))
assertTrue(result.isLeft())
assertIs<DomainError.BoardNotFound>(result.leftOrNull())

// ✅ verificar tipo de erro específico
assertIs<DomainError.PersistenceError>(result.leftOrNull())
```

---

## 3. MockK — Padrões do Projeto

Referência oficial: https://mockk.io

### Criação de mocks

```kotlin
// ✅ mock estrito — falha se método não configurado for chamado
val boardRepository = mockk<BoardRepository>()

// ✅ mock relaxado — retorna valores default para métodos não configurados
// Use para repositórios que o teste não usa, mas o Koin precisa injetar
single<CardRepository> { mockk(relaxed = true) }

// ❌ nunca use relaxed para mocks que você vai verificar comportamento
val importantRepo = mockk<ScenarioRepository>(relaxed = true) // esconde erros
```

### Stubbing de suspend functions

```kotlin
// ✅ coEvery para suspend functions (a maioria dos repositórios)
coEvery { boardRepository.save(any()) } answers { firstArg<Board>().right() }
coEvery { boardRepository.findById(any()) } returns board.right()

// ✅ retornar Left (erro)
coEvery { boardRepository.findById(any()) } returns
    DomainError.BoardNotFound("nonexistent-id").left()

// ✅ retornar Left de persistência
coEvery { boardRepository.findById(any()) } returns
    DomainError.PersistenceError("DB failure").left()

// ✅ usar firstArg quando o resultado depende do argumento
coEvery { scenarioRepository.save(any()) } answers { firstArg<Scenario>().right() }
coEvery { scenarioRepository.saveState(any(), any()) } answers {
    secondArg<SimulationState>().right()
}
```

### Matching de argumentos

```kotlin
// ✅ any() — qualquer valor do tipo inferido
coEvery { repo.findById(any()) } returns board.right()

// ✅ valor específico
coEvery { repo.findById(boardId) } returns board.right()

// ⚠️ PITFALL: any() com @JvmInline value classes pode falhar em runtime
// Sintoma: ClassCastException ao usar any<ScenarioId>()
// Solução: use any() sem tipo quando o parâmetro é um value class
coEvery { repo.findById(any()) } returns ...   // ✅ sem tipo
coEvery { repo.findById(any<ScenarioId>()) } returns ...  // ⚠️ pode falhar
```

### Verificação de chamadas

```kotlin
// ✅ verificar que o repositório foi chamado
coVerify(exactly = 1) { boardRepository.save(any()) }
coVerify { boardRepository.findById(boardId) }

// ✅ verificar que NÃO foi chamado
coVerify(exactly = 0) { boardRepository.save(any()) }

// ✅ verify para funções não-suspend
verify { nonSuspendRepo.doSomething() }
```

### Organização nos testes de rota

```kotlin
class BoardRoutesTest {
    // ✅ mocks declarados como propriedades — visíveis em todos os testes
    private val boardRepository = mockk<BoardRepository>()
    private val cardRepository = mockk<CardRepository>()

    // ✅ Koin module configurado uma vez — injeta mocks reais ou relaxed
    private val testModule = module {
        single<BoardRepository> { boardRepository }          // mock controlado
        single<CardRepository> { mockk(relaxed = true) }    // não relevante
        single { CreateBoardUseCase(get()) }
        single { GetBoardUseCase(get()) }
        // ... todos os use cases necessários para o routing
    }

    @Test
    fun `POST boards creates board and returns 201`() = testApplication {
        install(Koin) { modules(testModule) }
        application {
            configureObservability()
            configureOpenApi()
            configureSerialization()
            configureStatusPages()
            configureRouting()
        }

        // ✅ configure o mock DENTRO do teste — comportamento específico por teste
        coEvery { boardRepository.save(any()) } answers { firstArg<Board>().right() }
        coEvery { boardRepository.findById(any()) } returns board.right()

        val response = client.post("/api/v1/boards") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My Board"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }
}
```

### Antipadrões MockK

| Antipadrão | Correto |
|---|---|
| `every { suspendFun() }` em suspend function | Use `coEvery { suspendFun() }` |
| `relaxed = true` em mocks verificados | Mock estrito para o que você verifica |
| Configurar mock fora do teste (em `@BeforeEach`) | Configurar dentro do teste que o usa |
| `any<ValueClass>()` com inline value class | Use `any()` sem tipo |
| `mockk<UseCase>()` direto | Instancie o use case real com `mockk<Repository>()` |

---

## 4. Detekt — Regras de Comentários

Referência: https://detekt.dev/docs/rules/comments

As regras de comentários protegem a **documentação pública** do código.
Este projeto é uma API interna/educacional — aplique as regras abaixo com critério.

### Regras recomendadas (não ativas no `detekt.yml` do projeto)

> **Nota:** As regras `UndocumentedPublicClass` e `UndocumentedPublicFunction` do
> ruleset `comments` **não estão configuradas** no `config/detekt/detekt.yml` deste
> projeto — o bloco `comments` não está ativo. As diretrizes abaixo são **boas
> práticas recomendadas**, não requisitos que causam falha de build.

#### `UndocumentedPublicClass` (recomendada)
Encoraja KDoc em toda `class`, `interface`, `object` e `enum` public/internal.

```kotlin
// ✅ para classes públicas com comportamento não óbvio
/**
 * Motor de simulação Kanban. Processa um dia de simulação aplicando
 * as decisões fornecidas e calculando as métricas de fluxo.
 */
class SimulationEngine { ... }

// ✅ para data classes simples — exceção razoável (payload óbvio pelo nome)
data class BoardResponse(val id: String, val name: String)  // pode dispensar KDoc
```

#### `UndocumentedPublicFunction` (recomendada)
Encoraja KDoc em funções `public`/`internal` não óbvias.

```kotlin
// ✅ quando o comportamento não é óbvio pelo nome
/**
 * Executa um dia de simulação aplicando as decisões e retornando
 * o snapshot com métricas e movimentos do dia.
 *
 * @param state estado atual da simulação antes do dia
 * @param decisions decisões a aplicar (pode ser vazia)
 * @return snapshot imutável com resultado do dia
 */
fun runDay(state: SimulationState, decisions: List<Decision>): DailySnapshot

// ✅ quando o nome é auto-explicativo — KDoc pode ser omitido
fun findById(id: BoardId): Either<DomainError, Board>
```

#### `ForbiddenComment`
Configurado para bloquear `FIXME:` e `HACK:` em `config/detekt/detekt.yml`.

```kotlin
// ❌ quebra o build
// FIXME: isso está errado mas funciona por ora
// HACK: forçando o tipo aqui para não refatorar agora

// ✅ documente a intenção com referência
// TODO: #42 — substituir por lógica assíncrona quando migrar para Kotlin Flows
```

#### `CommentOverPrivateFunction` / `CommentOverPrivateProperty`
KDoc em funções e propriedades privadas é considerado ruído — use comentários
de linha simples apenas quando necessário:

```kotlin
// ✅ comentário de linha para lógica não óbvia em código privado
private fun calculateAgingDays(item: WorkItem, currentDay: Int): Int {
    // Itens BLOCKED não envelhecem — o clock para enquanto estão bloqueados
    if (item.state == WorkItemState.BLOCKED) return item.agingDays
    return currentDay - item.startDay
}

// ❌ KDoc em função privada — Detekt pode sinalizar como ruído
/**
 * Calcula o envelhecimento.
 */
private fun calculateAgingDays(...)
```

### Onde NÃO usar comentários

```kotlin
// ❌ comentário que repete o código
val boardId = BoardId.generate()  // gera um boardId

// ❌ comentário de seção desnecessário
// cria o board
val board = Board(id = boardId, name = command.name)
// salva no repositório
repository.save(board)

// ✅ código legível não precisa de comentário
val board = Board(id = BoardId.generate(), name = command.name)
repository.save(board)
```

### Onde usar comentários de forma efetiva

```kotlin
// ✅ explica o PORQUÊ, não o O QUÊ
// Locale.ROOT garante uppercase consistente em qualquer JVM —
// toUpperCase() sem locale pode falhar em turcês (i → İ, não I)
val type = DecisionType.valueOf(d.type.uppercase(Locale.ROOT))

// ✅ documenta uma decisão de design não óbvia
// MDC.putCloseable garante que o contexto seja removido mesmo em exceções,
// evitando vazamentos de requestId entre requisições no pool de threads
MDC.putCloseable("scenarioId", scenarioId).use { ... }

// ✅ alerta sobre um pitfall conhecido
// ATENÇÃO: any() com @JvmInline value classes causa ClassCastException no MockK
// Use any() sem tipo quando o parâmetro for um value class
coEvery { repo.findById(any()) } returns ...
```

---

## 5. SLF4J — Logging

Referência: https://www.slf4j.org/manual.html

### Como obter um logger

```kotlin
import org.slf4j.LoggerFactory

// ✅ em classes — companion object, lazy
class SimulationEngine {
    private val logger = LoggerFactory.getLogger(SimulationEngine::class.java)
}

// ✅ top-level — para funções de extensão e arquivos sem classe
private val logger = LoggerFactory.getLogger("com.kanbanvision.httpapi.routes.BoardRoutes")

// ✅ usando a classe diretamente (mais seguro contra refatoração de nome)
private val logger = LoggerFactory.getLogger(this::class.java)
```

### Níveis de log e quando usar cada um

| Nível | Quando usar | Exemplo neste projeto |
|---|---|---|
| `ERROR` | Falha inesperada que impede a operação | Exceção não tratada, falha de banco não recuperável |
| `WARN` | Situação anormal mas recuperável | Decisão inválida ignorada, retry de conexão |
| `INFO` | Evento de negócio relevante | Cenário criado, dia de simulação executado |
| `DEBUG` | Detalhe útil em investigação | Parâmetros de query, estado intermediário |
| `TRACE` | Detalhe muito fino (desativado em produção) | Cada instrução executada na simulação |

### Sintaxe correta — sempre placeholders `{}`

```kotlin
// ✅ placeholders — objeto só é serializado se o nível estiver ativo
logger.info("Cenário {} criado para tenant {}", scenarioId, tenantId)
logger.debug("Executando dia {} com {} decisões", day, decisions.size)
logger.error("Falha ao persistir snapshot do dia {}", day, exception)

// ❌ concatenação — toString() sempre avaliado, mesmo se DEBUG desativado
logger.debug("Executando dia " + day + " com " + decisions.size + " decisões")

// ❌ template string — mesmo problema de avaliação antecipada
logger.debug("Executando dia $day com ${decisions.size} decisões")
```

### O que NÃO logar

```kotlin
// ❌ dados sensíveis
logger.info("Usuário {} autenticado com senha {}", user, password)

// ❌ PII (informação pessoal identificável)
logger.debug("Request recebido de {}", userEmail)

// ❌ corpo completo de request/response em produção
logger.debug("Body: {}", requestBody)  // pode conter dados sensíveis
```

### Integração com Ktor CallLogging

O projeto usa `CallLogging` do Ktor configurado em `Observability.kt`:

```kotlin
install(CallLogging) {
    level = Level.INFO
    mdc("requestId") { call -> call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown" }
    format { call ->
        "${call.request.httpMethod.value} ${call.request.path()} → ${call.response.status()}"
    }
}
```

Cada requisição gera automaticamente um log `INFO` no formato:
```
POST /api/v1/boards → 201 Created  [rid=3f2a1b4c]
```

**Regra**: não adicione `logger.info` manual para início/fim de request —
o `CallLogging` já faz isso.

---

## 6. MDC — Mapped Diagnostic Context

MDC permite **correlacionar logs** de uma mesma operação adicionando campos ao
contexto do thread atual, que aparecem automaticamente em todos os logs emitidos
durante aquele processamento.

### Campos MDC usados neste projeto

| Chave MDC | Onde é adicionada | Valor |
|---|---|---|
| `requestId` | `Observability.kt` (CallLogging) | UUID gerado por requisição ou `X-Request-ID` do cliente |
| `scenarioId` | `ScenarioRoutes.kt` e `ScenarioAnalyticsRoutes.kt` | UUID do cenário sendo processado |
| `day` | `ScenarioRoutes.kt` (runDay e snapshot) | Número do dia da simulação |

### Como propagar contexto MDC em rotas

```kotlin
// ✅ MDC.putCloseable — garante remoção ao sair do bloco (try-with-resources)
private suspend fun ApplicationCall.handleGetScenario(getScenario: GetScenarioUseCase) {
    val scenarioId = parameters["scenarioId"]
        ?: return respondWithDomainError(DomainError.ValidationError("Missing scenario id"))

    MDC.putCloseable("scenarioId", scenarioId).use {
        getScenario.execute(GetScenarioQuery(scenarioId = scenarioId)).fold(
            ifLeft = { error -> respondWithDomainError(error) },
            ifRight = { result -> respond(toResponse(result)) },
        )
    }
}
```

**Por que `putCloseable` e não `put`/`remove`?**

```kotlin
// ❌ MDC.put sem remoção — vaza contexto entre requisições no pool de threads
MDC.put("scenarioId", scenarioId)
getScenario.execute(...)  // se lançar exceção, MDC.remove nunca é chamado
MDC.remove("scenarioId")

// ✅ putCloseable com use — automático, mesmo em exceções
MDC.putCloseable("scenarioId", scenarioId).use {
    getScenario.execute(...)  // contexto removido mesmo se exceção
}
```

### Aninhar contextos MDC

```kotlin
// ✅ blocos aninhados para múltiplas chaves
MDC.putCloseable("scenarioId", scenarioId).use {
    MDC.putCloseable("day", day.toString()).use {
        getDailySnapshot.execute(...).fold(...)
        // logs aqui têm: [requestId=..., scenarioId=..., day=...]
    }
    // aqui: [requestId=..., scenarioId=...]
}
// aqui: [requestId=...]
```

### Integração do MDC com CallLogging (Ktor)

O plugin `CallLogging` extrai valores do MDC e os formata no padrão de log.
A chave `requestId` é adicionada via callback `mdc(...)`:

```kotlin
mdc("requestId") { call ->
    call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown"
}
```

Isso significa: para cada requisição, antes do log ser emitido, o Ktor chama
o lambda e injeta `requestId` no MDC daquele thread.

### Formato de log resultante

Com o layout `%d{HH:mm:ss.SSS} [%thread] %level [rid=%X{requestId}] - %msg%n`:

```
14:22:01.123 [ktor-nio-thread-1] INFO [rid=3f2a1b4c] - POST /api/v1/scenarios/run → 200 OK
14:22:01.130 [ktor-nio-thread-1] INFO [rid=3f2a1b4c] - Dia 5 executado para cenário abc-123
```

### MDC em testes

Os testes de rota verificam o `X-Request-ID` no header de resposta (que espelha
o MDC `requestId`):

```kotlin
// ✅ verificar que o requestId é propagado no header
@Test
fun `X-Request-ID from request is propagated to response`() = testApplication {
    // ...
    val correlationId = "test-correlation-id-123"
    val response = client.post("/api/v1/boards") {
        headers.append("X-Request-ID", correlationId)
        setBody("""{"name":"My Board"}""")
    }
    assertEquals(correlationId, response.headers["X-Request-ID"])
}

// ✅ verificar que requestId é gerado quando ausente
@Test
fun `response contains X-Request-ID header`() = testApplication {
    // ...
    val requestId = response.headers["X-Request-ID"]
    assertNotNull(requestId)
    assertTrue(requestId.isNotBlank())
}

// ✅ verificar que requestId aparece no corpo de erro
@Test
fun `GET boards by id returns 404 when not found`() = testApplication {
    // ...
    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    assertNotNull(body["error"])
    assertNotNull(body["requestId"])  // obrigatório em todo erro 4xx/5xx
}
```

### Onde não usar MDC

```kotlin
// ❌ em use cases ou domain — violaria a Dependency Rule
// Use cases não conhecem HTTP, logo não conhecem requestId
class CreateBoardUseCase(...) {
    fun execute(command: CreateBoardCommand) {
        MDC.put("requestId", ...)  // ❌ dependency rule violation
    }
}

// ✅ MDC é responsabilidade exclusiva da camada de entrega (http_api)
// Use cases recebem dados puros — não contexto de log
```

---

## 7. Checklist ao Adicionar um Novo Endpoint

- [ ] Testes de rota: happy path + 400 (validação) + 404 (not found) + 500 (persistence)
- [ ] Mock do repositório usando `coEvery` (suspend) ou `every` (não-suspend)
- [ ] Verificação de `requestId` no corpo das respostas de erro
- [ ] `MDC.putCloseable` para cada campo de contexto relevante (scenarioId, day)
- [ ] `CallLogging` propaga automaticamente — não adicionar `logger.info` manual para request
- [ ] Funções públicas não óbvias com KDoc (ou nome auto-explicativo)
- [ ] Sem `FIXME:`/`HACK:` — use `TODO: #<ticket>`
- [ ] `./gradlew :http_api:test` verde antes do PR

---

## 8. Referências

| Ferramenta | Documentação |
|---|---|
| JUnit 5 | https://junit.org/junit5/docs/current/user-guide/ |
| MockK | https://mockk.io |
| SLF4J | https://www.slf4j.org/manual.html |
| Detekt Comments | https://detekt.dev/docs/rules/comments |
| KtLint | https://pinterest.github.io/ktlint/latest/ |
| JaCoCo | https://www.jacoco.org/jacoco/trunk/doc/ |
| MDC (SLF4J) | https://www.slf4j.org/manual.html#mdc |
| Ktor CallLogging | https://ktor.io/docs/call-logging.html |