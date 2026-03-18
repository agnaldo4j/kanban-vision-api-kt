argument-hint: "[file or class to refactor (optional)]"
allowed-tools: Read, Grep, Glob
---
name: refactoring
description: >
  Identifique code smells e aplique técnicas de refactoring para melhorar a
  estrutura interna do código sem alterar seu comportamento externo. Use este
  skill ao revisar PRs, ao encontrar métodos longos, classes grandes, código
  duplicado ou qualquer sinal de degradação de design. Baseado em
  https://refactoring.guru/pt-br/refactoring
---

# Refactoring — Identificar Smells e Aplicar Técnicas

> "Refactoring é um processo sistemático de melhoria do código sem criar
> novas funcionalidades, que transforma um design bagunçado em código
> limpo e design simples."
> — Fonte: https://refactoring.guru/pt-br/refactoring

---

## O que é Refactoring

Refactoring **não é** reescrever. É uma sequência de pequenas transformações
estruturais, cada uma preservando o comportamento observável do código.

**Regras fundamentais:**
1. Os testes devem passar antes de começar
2. Cada transformação é pequena e verificável
3. Os testes devem continuar passando após cada transformação
4. Nunca misture refactoring com adição de funcionalidade no mesmo commit

```
❌ "Vou limpar esse código enquanto adiciono a feature"
✅ Commit 1: refactor — extrair método X
✅ Commit 2: feat — adicionar validação Y
```

---

## Code Smells — Sinais de Problema

Code smells são indicadores de que algo no design pode estar errado.
Não são bugs — o código funciona — mas criam dificuldade de manutenção.

---

### Bloaters — Código que Cresceu Demais

Estruturas que aumentaram de tamanho ao longo do tempo até prejudicar a
compreensão e a manutenção.

#### Long Method

Métodos que fazem coisas demais. Dificulta leitura, teste e reuso.

```kotlin
// ❌ Long Method — 30+ linhas fazendo tudo junto
fun execute(command: CreateCardCommand): Either<DomainError, CardId> = either {
    if (command.columnId.isBlank()) raise(DomainError.ValidationError("Column id must not be blank"))
    if (command.title.isBlank()) raise(DomainError.ValidationError("Card title must not be blank"))
    log.debug("Creating card: columnId={} title={}", command.columnId, command.title)
    val startTime = System.currentTimeMillis()
    val columnId = ColumnId(command.columnId)
    val existing = cardRepository.findByColumnId(columnId)
    val card = Card.create(columnId = columnId, title = command.title,
        description = command.description, position = existing.size)
    cardRepository.save(card)
    val duration = System.currentTimeMillis() - startTime
    log.info("Card created: id={} duration={}ms", card.id.value, duration)
    card.id
}

// ✅ Extrair responsabilidades em métodos com nomes que revelam intenção
fun execute(command: CreateCardCommand): Either<DomainError, CardId> = either {
    command.validate().bind()
    log.debug("Creating card: columnId={} title={}", command.columnId, command.title)
    val (cardId, duration) = buildAndSave(command).bind()
    log.info("Card created: id={} duration={}ms", cardId.value, duration.inWholeMilliseconds)
    cardId
}
```

**Diagnóstico**: função > 15 linhas no Detekt; múltiplos níveis de abstração misturados.
**Técnica**: [Extract Method](#extract-method)

---

#### Large Class

Classes que acumulam muitas responsabilidades, muitos campos e muitos métodos.

```kotlin
// ❌ Large Class — mistura domínio, persistência e serialização
class BoardService {
    fun createBoard(name: String) { /* regra de negócio */ }
    fun saveToDatabase(board: Board) { /* SQL */ }
    fun toJson(board: Board): String { /* serialização */ }
    fun sendNotification(board: Board) { /* integração */ }
    fun validateBoardName(name: String): Boolean { /* validação */ }
    fun findBoardByName(name: String): Board? { /* query */ }
    // ... 20 métodos mais
}

// ✅ Cada classe com uma responsabilidade
class CreateBoardUseCase(private val repository: BoardRepository) { ... }
class JdbcBoardRepository : BoardRepository { ... }
// Serialização via @Serializable data class BoardResponse
```

**Diagnóstico**: Detekt `thresholdInClasses: 11`; múltiplos contextos de mudança.
**Técnica**: [Extract Class](#extract-class), [Move Method](#move-method)

---

#### Primitive Obsession

Uso de tipos primitivos (`String`, `Int`, `UUID`) para representar conceitos
de domínio que merecem seu próprio tipo.

```kotlin
// ❌ String genérica para IDs — nada impede de passar boardId onde se espera cardId
fun moveCard(cardId: String, targetColumnId: String, newPosition: Int)

// ✅ Value classes expressam a intenção e previnem erros em tempo de compilação
@JvmInline value class CardId(val value: UUID)
@JvmInline value class ColumnId(val value: UUID)

fun moveCard(cardId: CardId, targetColumnId: ColumnId, newPosition: Int)
```

**Diagnóstico**: `String` ou `UUID` passando entre camadas para representar identidade.
**Técnica**: [Replace Primitive with Object](#replace-data-value-with-object)

---

#### Long Parameter List

Funções com muitos parâmetros são difíceis de chamar e sujeitas a erros de ordem.

```kotlin
// ❌ 5+ parâmetros — fácil de inverter a ordem
fun createCard(columnId: String, title: String, description: String,
               position: Int, createdBy: String, priority: Int)

// ✅ Objeto de comando encapsula parâmetros relacionados
data class CreateCardCommand(
    val columnId: String,
    val title: String,
    val description: String? = null,
    val priority: Int = 0,
) : Command

fun execute(command: CreateCardCommand): Either<DomainError, CardId>
```

**Diagnóstico**: Detekt `functionThreshold: 5`; parâmetros que sempre aparecem juntos.
**Técnica**: [Introduce Parameter Object](#introduce-parameter-object), [Preserve Whole Object](#preserve-whole-object)

---

#### Data Clumps

Grupos de variáveis que sempre andam juntas e deveriam ser encapsuladas num tipo.

```kotlin
// ❌ boardId + boardName sempre aparecem juntos em vários lugares
fun logBoardEvent(boardId: String, boardName: String, eventType: String)
fun auditBoard(boardId: String, boardName: String, userId: String)

// ✅ Encapsular no tipo que já existe
fun logBoardEvent(board: Board, eventType: String)
fun auditBoard(board: Board, userId: String)
```

---

### Object-Orientation Abusers — OO Mal Aplicado

Uso incompleto ou incorreto dos princípios de orientação a objetos.

---

#### Switch Statements

`when`/`if` que ramificam por tipo e crescem a cada nova variante.

```kotlin
// ❌ Switch Statement — cresce a cada novo DomainError
fun respondWithError(error: DomainError) {
    when (error.type) {
        "validation" -> call.respond(HttpStatusCode.BadRequest, ...)
        "not_found"  -> call.respond(HttpStatusCode.NotFound, ...)
        "persistence"-> call.respond(HttpStatusCode.InternalServerError, ...)
        // nova variante → modificar aqui
    }
}

// ✅ Sealed class + when exaustivo — compilador força cobertura total
sealed class DomainError {
    data class ValidationError(val message: String) : DomainError()
    data class BoardNotFound(val id: String) : DomainError()
    data class PersistenceError(val message: String) : DomainError()
}

fun respondWithError(error: DomainError) = when (error) {
    is DomainError.ValidationError -> call.respond(HttpStatusCode.BadRequest, ...)
    is DomainError.BoardNotFound,
    is DomainError.ColumnNotFound,
    is DomainError.CardNotFound   -> call.respond(HttpStatusCode.NotFound, ...)
    is DomainError.PersistenceError -> call.respond(HttpStatusCode.InternalServerError, ...)
    // nova variante → compilador avisa se não coberta
}
```

**Técnica**: [Replace Conditional with Polymorphism](#replace-conditional-with-polymorphism)

---

#### Temporary Field

Campos de classe que só fazem sentido em alguns contextos.

```kotlin
// ❌ moveTargetColumn só é relevante durante uma operação de mover
class Card(val id: CardId, val title: String, var moveTargetColumn: ColumnId? = null)

// ✅ Campo temporário vira parâmetro da operação
data class Card(val id: CardId, val title: String, val columnId: ColumnId, val position: Int) {
    fun moveTo(targetColumn: ColumnId, newPosition: Int): Card =
        copy(columnId = targetColumn, position = newPosition)
}
```

---

#### Refused Bequest

Subclasses que herdam mas ignoram parte do comportamento do pai.

```kotlin
// ❌ ReadOnlyRepository herda mas não implementa save/delete
class ReadOnlyBoardRepository : JdbcBoardRepository() {
    override fun save(board: Board) = throw UnsupportedOperationException()
    override fun delete(id: BoardId) = throw UnsupportedOperationException()
}

// ✅ Segregar interfaces (ISP) — ReadOnlyRepository implementa apenas BoardReader
interface BoardReader { fun findById(id: BoardId): Board? }
interface BoardWriter { fun save(board: Board) }
class ReadOnlyBoardRepository : BoardReader { ... }
```

---

### Change Preventers — Resistência a Mudanças

Smells que fazem uma única mudança lógica exigir alterações em múltiplos lugares.

---

#### Divergent Change

Uma classe que precisa ser modificada por razões diferentes.

```kotlin
// ❌ BoardService muda quando as regras de negócio mudam E quando o banco muda
class BoardService {
    fun createBoard(name: String) { /* regra */ }
    fun findBoardInDb(id: String) { /* SQL */ }
}

// ✅ Separar — cada classe muda por apenas uma razão
class CreateBoardUseCase(private val repo: BoardRepository) { ... }
class JdbcBoardRepository : BoardRepository { ... }
```

---

#### Shotgun Surgery

Uma mudança lógica única exige modificar muitos arquivos diferentes.

```kotlin
// ❌ Adicionar um novo campo ao Card exige mudar: Card.kt, JdbcCardRepository.kt,
//    CreateCardCommand.kt, CardResponse.kt, CreateCardRequest.kt, todas as queries SQL
//    → 6 arquivos para uma mudança conceitual

// ✅ Encapsular a representação — Card é a fonte da verdade; adaptadores convertem
data class Card(val id: CardId, val columnId: ColumnId, val title: String,
                val description: String?, val position: Int)

// Adapters convertem de/para Card sem expor campos internos
fun ResultSet.toCard(): Card = Card(id = CardId(UUID.fromString(getString("id"))), ...)
fun Card.toResponse(): CardResponse = CardResponse(id = id.value.toString(), ...)
```

---

### Dispensables — Código Desnecessário

Elementos que podem ser removidos sem perda de funcionalidade.

---

#### Duplicate Code

A mesma estrutura lógica aparece em vários lugares.

```kotlin
// ❌ Mesmo padrão de catch em todos os use cases, copiado
fun execute(...) = either {
    catch({ measureTimedValue { repository.save(entity) } })
    { e -> raise(DomainError.PersistenceError(e.message ?: "DB error")) }
}

// ✅ Extrair helper reutilizável
private suspend fun <T> persistWithTiming(block: suspend () -> T): TimedValue<T> =
    catch({ measureTimedValue { block() } })
    { e -> raise(DomainError.PersistenceError(e.message ?: "Database error")) }
```

---

#### Dead Code

Código que nunca é executado — métodos não chamados, variáveis não usadas,
condições impossíveis.

**Diagnóstico**: KtLint `UnusedImports`; Detekt `UnreachableCode`; `@Suppress` desnecessários.

---

#### Speculative Generality

Abstrações criadas "para o futuro" que nunca foram necessárias.

```kotlin
// ❌ Interface criada para "quando tivermos múltiplas implementações"
interface BoardCreator { fun create(name: String): Board }
class DefaultBoardCreator : BoardCreator { ... }
// Nunca houve segunda implementação — abstração sem valor atual

// ✅ Criar a abstração quando a segunda implementação aparecer (YAGNI)
fun Board.Companion.create(name: String): Board = Board(id = BoardId.generate(), name = name)
```

---

#### Comments

Comentários que explicam *o quê* o código faz — sinal de que o código não é
autoexplicativo. Comentários bons explicam *por quê*.

```kotlin
// ❌ Comentário explicando o óbvio
// Verifica se o boardId está em branco
if (command.boardId.isBlank()) raise(DomainError.ValidationError("Board id must not be blank"))

// ❌ Comentário substituindo nome ruim
val x = repo.findAll() // busca todos os boards do repositório

// ✅ Nome que elimina necessidade de comentário
val existingBoards = boardRepository.findAll()

// ✅ Comentário válido — explica uma decisão de negócio não óbvia
// Position é calculado como tamanho da lista atual para garantir que
// novas colunas sempre apareçam no final sem conflito de índice
val position = existingColumns.size
```

---

### Couplers — Acoplamento Excessivo

Smells que criam dependências desnecessárias entre partes do sistema.

---

#### Feature Envy

Um método que usa mais dados de outra classe do que da própria.

```kotlin
// ❌ calculatePosition usa só dados de Card — deveria ser método de Card
fun calculateNextPosition(cards: List<Card>): Int {
    return cards.filter { it.columnId == targetColumnId }.maxOfOrNull { it.position }?.plus(1) ?: 0
}

// ✅ Mover o comportamento para onde estão os dados
data class Card(...) {
    companion object {
        fun nextPositionIn(cards: List<Card>): Int =
            cards.maxOfOrNull { it.position }?.plus(1) ?: 0
    }
}
```

---

#### Message Chains

Longas cadeias de chamadas que criam dependência do caminho de navegação.

```kotlin
// ❌ Dependência da estrutura interna — fragilidade
val boardName = request.context.session.user.currentBoard.name

// ✅ Lei de Demeter — pergunte para o objeto direto
val boardName = boardRepository.findById(boardId)?.name
```

---

#### Middle Man

Uma classe que só delega — não faz nada por si mesma.

```kotlin
// ❌ BoardFacade só repassa para o useCase — intermediário sem valor
class BoardFacade(private val createBoard: CreateBoardUseCase) {
    fun create(command: CreateBoardCommand) = createBoard.execute(command)
}

// ✅ Chamar diretamente o use case via injeção de dependência (Koin)
val createBoard: CreateBoardUseCase by inject()
```

---

## Técnicas de Refactoring

---

### Composing Methods

#### Extract Method

Isolar um fragmento de código em um método com nome que revela a intenção.

```kotlin
// Antes
fun execute(command: CreateColumnCommand): Either<DomainError, ColumnId> = either {
    command.validate().bind()
    log.debug("Creating column: boardId={} name={}", command.boardId, command.name)
    val (columnId, duration) = catch({
        measureTimedValue {
            val boardId = BoardId(command.boardId)
            val existing = columnRepository.findByBoardId(boardId)
            val column = Column.create(boardId = boardId, name = command.name, position = existing.size)
            columnRepository.save(column)
            column.id
        }
    }) { e -> raise(DomainError.PersistenceError(e.message ?: "Database error")) }
    log.info("Column created: id={} duration={}ms", columnId.value, duration.inWholeMilliseconds)
    columnId
}

// Depois — execute revela a sequência de alto nível
fun execute(command: CreateColumnCommand): Either<DomainError, ColumnId> = either {
    command.validate().bind()
    log.debug("Creating column: boardId={} name={}", command.boardId, command.name)
    val (columnId, duration) = buildAndPersist(command).bind()
    log.info("Column created: id={} duration={}ms", columnId.value, duration.inWholeMilliseconds)
    columnId
}

private suspend fun buildAndPersist(command: CreateColumnCommand): Either<DomainError, TimedValue<ColumnId>> =
    either {
        catch({ measureTimedValue { createAndSaveColumn(command) } })
        { e -> raise(DomainError.PersistenceError(e.message ?: "Database error")) }
    }

private suspend fun createAndSaveColumn(command: CreateColumnCommand): ColumnId {
    val boardId = BoardId(command.boardId)
    val position = columnRepository.findByBoardId(boardId).size
    val column = Column.create(boardId = boardId, name = command.name, position = position)
    columnRepository.save(column)
    return column.id
}
```

**Quando aplicar**: fragmento com comentário explicativo, fragmento reutilizado, fragmento com nível de abstração diferente do método pai.

---

#### Extract Variable

Nomear expressões complexas para torná-las legíveis.

```kotlin
// Antes
if (cards.filter { it.columnId == targetColumnId }.size >= maxCardsPerColumn) raise(...)

// Depois
val cardsInTargetColumn = cards.filter { it.columnId == targetColumnId }
val columnIsFull = cardsInTargetColumn.size >= maxCardsPerColumn
if (columnIsFull) raise(DomainError.ValidationError("Column has reached maximum capacity"))
```

---

#### Replace Temp with Query

Substituir variável temporária por chamada de método quando o valor é calculado.

```kotlin
// Antes
val existingCount = columnRepository.findByBoardId(boardId).size
val newPosition = existingCount

// Depois
private suspend fun nextPositionFor(boardId: BoardId): Int =
    columnRepository.findByBoardId(boardId).size
```

---

#### Replace Method with Method Object

Quando um método longo tem muitas variáveis locais que interagem, extrair para
uma classe que encapsula o estado da computação.

```kotlin
// Aplicável quando buildAndSave tem 5+ variáveis locais interdependentes
// Criar CardCreator(command, repository) com método execute()
class CardCreator(
    private val command: CreateCardCommand,
    private val repository: CardRepository,
) {
    private lateinit var columnId: ColumnId
    private lateinit var existingCards: List<Card>

    suspend fun execute(): CardId {
        resolveColumnId()
        loadExistingCards()
        return createAndPersist()
    }

    private fun resolveColumnId() { columnId = ColumnId(command.columnId) }
    private suspend fun loadExistingCards() { existingCards = repository.findByColumnId(columnId) }
    private suspend fun createAndPersist(): CardId { ... }
}
```

---

### Moving Features between Objects

#### Move Method

Mover um método para a classe que contém os dados que ele usa.

```kotlin
// Antes — método em UseCase usando só dados de Card
class MoveCardUseCase {
    private fun isValidPosition(card: Card, newPosition: Int) =
        newPosition >= 0 && newPosition != card.position
}

// Depois — método na classe Card
data class Card(...) {
    fun canMoveTo(newPosition: Int): Boolean = newPosition >= 0 && newPosition != position
}
```

---

#### Extract Class

Dividir uma classe grande em classes menores com responsabilidades distintas.

```kotlin
// Antes — DatabaseFactory mistura configuração e schema
class DatabaseFactory {
    fun init() {
        configureHikari()   // configuração do pool
        createBoardsTable() // schema
        createCardsTable()  // schema
        createColumnsTable()// schema
    }
}

// Depois — separação de responsabilidades
class DatabaseFactory(private val schema: SchemaInitializer) {
    fun init() { configureHikari(); schema.createAll() }
}

class SchemaInitializer(private val dataSource: DataSource) {
    fun createAll() { createBoardsTable(); createCardsTable(); createColumnsTable() }
    private fun createBoardsTable() { ... }
    private fun createCardsTable() { ... }
    private fun createColumnsTable() { ... }
}
```

---

#### Hide Delegate

Esconder a cadeia de delegação atrás de um método direto.

```kotlin
// Antes
val department = employee.getDepartment()
val manager = department.getManager()

// Depois
class Employee {
    fun getManager(): Manager = department.getManager()
}
val manager = employee.getManager()
```

---

### Organizing Data

#### Replace Data Value with Object

Substituir tipos primitivos por objetos de valor quando o dado tem comportamento.

```kotlin
// Antes
val boardId: String = UUID.randomUUID().toString()

// Depois
@JvmInline
value class BoardId(val value: UUID) {
    companion object {
        fun generate(): BoardId = BoardId(UUID.randomUUID())
        fun from(raw: String): BoardId = BoardId(UUID.fromString(raw))
    }
}
```

---

#### Replace Magic Number with Symbolic Constant

```kotlin
// ❌ O que significa 90?
if (coverage < 90) throw GradleException("Coverage below threshold")

// ✅ Nome revela a intenção
private const val MINIMUM_COVERAGE_PERCENTAGE = 0.90
if (coverage < MINIMUM_COVERAGE_PERCENTAGE) throw GradleException(...)
```

---

#### Replace Type Code with Sealed Class

Substituir enums ou strings de tipo por hierarquia polimórfica.

```kotlin
// ❌ Type code como String
data class DomainError(val type: String, val message: String)
val error = DomainError(type = "validation", message = "Name is blank")

// ✅ Sealed class — exaustividade garantida pelo compilador
sealed class DomainError {
    data class ValidationError(val message: String) : DomainError()
    data class BoardNotFound(val id: String) : DomainError()
    data class PersistenceError(val message: String) : DomainError()
}
```

---

### Simplifying Conditional Expressions

#### Decompose Conditional

Extrair condições complexas em funções com nomes expressivos.

```kotlin
// Antes
if (command.boardId.isNotBlank() && command.name.isNotBlank() && command.name.length <= 100) { ... }

// Depois
private fun CreateColumnCommand.isValid() =
    boardId.isNotBlank() && name.isNotBlank() && name.length <= MAX_NAME_LENGTH
```

---

#### Replace Conditional with Polymorphism

Substituir `when`/`if` por despacho polimórfico via sealed class.

```kotlin
// Antes
fun httpStatus(error: DomainError): HttpStatusCode = when (error.code) {
    400 -> HttpStatusCode.BadRequest
    404 -> HttpStatusCode.NotFound
    500 -> HttpStatusCode.InternalServerError
    else -> HttpStatusCode.InternalServerError
}

// Depois
sealed class DomainError {
    abstract val httpStatus: HttpStatusCode
    data class ValidationError(val message: String) : DomainError() {
        override val httpStatus = HttpStatusCode.BadRequest
    }
    data class BoardNotFound(val id: String) : DomainError() {
        override val httpStatus = HttpStatusCode.NotFound
    }
}
```

---

#### Replace Nested Conditional with Guard Clauses

Validações antecipadas evitam aninhamento profundo.

```kotlin
// Antes — pyramid of doom
fun execute(command: CreateCardCommand): Either<DomainError, CardId> = either {
    if (command.columnId.isNotBlank()) {
        if (command.title.isNotBlank()) {
            val columnId = ColumnId(command.columnId)
            // ... lógica principal
        } else {
            raise(DomainError.ValidationError("Title is blank"))
        }
    } else {
        raise(DomainError.ValidationError("ColumnId is blank"))
    }
}

// Depois — guard clauses com ensure
fun execute(command: CreateCardCommand): Either<DomainError, CardId> = either {
    command.validate().bind() // guard clause — falha rápido
    // ... lógica principal sem aninhamento
}
```

---

### Simplifying Method Calls

#### Rename Method

Nomes que revelam a intenção eliminam a necessidade de comentários.

```kotlin
// ❌ O que "process" faz?
fun process(command: CreateBoardCommand): Either<DomainError, BoardId>

// ✅ Nome revela contrato
fun execute(command: CreateBoardCommand): Either<DomainError, BoardId>

// ❌ Abreviação não é óbvia
fun findBrd(id: String): Board?

// ✅
fun findBoardById(id: BoardId): Board?
```

---

#### Separate Query from Modifier (CQS)

Funções que retornam dados não devem ter efeitos colaterais.
Funções que modificam estado não devem retornar dados.

```kotlin
// ❌ Viola CQS — busca E modifica
fun getAndMarkAsRead(boardId: BoardId): Board {
    val board = repository.findById(boardId)
    repository.markAsRead(boardId) // efeito colateral inesperado
    return board
}

// ✅ Query separada de Command
fun getBoardById(query: GetBoardQuery): Either<DomainError, Board>    // só lê
fun markBoardAsRead(command: MarkBoardReadCommand): Either<DomainError, Unit>  // só modifica
```

---

#### Introduce Parameter Object

Quando parâmetros relacionados aparecem juntos em múltiplos métodos.

```kotlin
// ❌ boardId + name sempre juntos
fun createColumn(boardId: String, name: String): Either<DomainError, ColumnId>
fun validateColumn(boardId: String, name: String): Boolean

// ✅ Command encapsula parâmetros coesos
data class CreateColumnCommand(val boardId: String, val name: String) : Command

fun execute(command: CreateColumnCommand): Either<DomainError, ColumnId>
fun validate(command: CreateColumnCommand): Boolean
```

---

#### Replace Constructor with Factory Method

Factory methods permitem nomes expressivos e lógica de construção encapsulada.

```kotlin
// ❌ Construtor não revela a intenção
val card = Card(CardId(UUID.randomUUID()), columnId, title, description, position)

// ✅ Factory method com nome que revela o contexto de criação
data class Card(...) {
    companion object {
        fun create(columnId: ColumnId, title: String, description: String?, position: Int): Card =
            Card(id = CardId.generate(), columnId = columnId,
                 title = title, description = description, position = position)
    }
}
val card = Card.create(columnId = columnId, title = title, description = null, position = 0)
```

---

### Dealing with Generalization

#### Extract Interface

Quando parte do comportamento de uma classe é usada por outros contextos.

```kotlin
// Situação: GetBoardUseCase usa só findById, mas BoardRepository tem save/delete também
// → Extrair interface mínima para o use case

interface BoardReader {
    suspend fun findById(id: BoardId): Board?
}

interface BoardRepository : BoardReader {
    suspend fun save(board: Board): Board
    suspend fun delete(id: BoardId): Boolean
}

class GetBoardUseCase(private val boards: BoardReader) { ... } // depende só do que usa
```

---

#### Replace Inheritance with Delegation

Composição é geralmente preferível à herança.

```kotlin
// ❌ Herança para reusar comportamento de logging
class LoggedBoardRepository : JdbcBoardRepository() {
    override fun save(board: Board): Board {
        log.info("Saving board: ${board.id}")
        return super.save(board)
    }
}

// ✅ Delegação — sem acoplamento à implementação concreta
class LoggedBoardRepository(
    private val delegate: BoardRepository,
) : BoardRepository by delegate {
    override suspend fun save(board: Board): Board {
        log.info("Saving board: ${board.id}")
        return delegate.save(board)
    }
}
```

---

## Processo de Refactoring Neste Projeto

### Antes de qualquer refactoring

```bash
# 1. Garantir que todos os testes passam
./gradlew testAll

# 2. Verificar quais smells existem
./gradlew detekt

# 3. Criar branch dedicada
git checkout -b refactor/<descrição-concisa>
```

### Ciclo de Refactoring

```
1. Identificar smell → escolher técnica
2. Aplicar UMA transformação pequena
3. ./gradlew testAll  ← testes devem passar
4. git commit -m "refactor: <técnica aplicada>"
5. Repetir
```

### Commits atômicos por transformação

```bash
# ✅ Um commit por técnica aplicada
git commit -m "refactor: extract buildAndPersist from CreateColumnUseCase.execute"
git commit -m "refactor: replace primitive String with ColumnId value class in routes"
git commit -m "refactor: introduce CreateColumnCommand parameter object"

# ❌ Nunca misturar feature com refactoring
git commit -m "feat: add column validation and clean up code"
```

---

## Checklist — Code Review com Foco em Refactoring

### Bloaters
- [ ] Algum método tem mais de 15 linhas? → Extract Method
- [ ] Alguma classe tem mais de 11 métodos ou 200 linhas? → Extract Class
- [ ] Existem parâmetros primitivos representando conceitos de domínio? → Replace Primitive with Object
- [ ] Algum método tem mais de 5 parâmetros? → Introduce Parameter Object

### OO Abusers
- [ ] `when`/`if` ramificando por tipo que pode crescer? → Replace Conditional with Polymorphism
- [ ] Campos que só são usados em alguns métodos? → Temporary Field → Extract Class
- [ ] Subclasse que lança `UnsupportedOperationException`? → Replace Inheritance with Delegation

### Change Preventers
- [ ] Uma mudança lógica exige modificar mais de 2 arquivos? → Shotgun Surgery → Move Method/Field
- [ ] Uma classe muda por múltiplas razões? → Divergent Change → Extract Class

### Dispensables
- [ ] Existe código duplicado entre classes? → Extract Method + Move Method
- [ ] Existe código comentado? → Delete (está no git)
- [ ] Existem comentários explicando *o quê* (não *por quê*)? → Rename/Extract
- [ ] Existem abstrações sem segunda implementação? → Speculative Generality → Inline Class

### Couplers
- [ ] Método que usa mais dados de outra classe? → Feature Envy → Move Method
- [ ] Cadeia longa de `.get().get().get()`? → Message Chains → Hide Delegate
- [ ] Classe que só delega? → Middle Man → Remove Middle Man

---

## Referências

- **Catálogo completo**: https://refactoring.guru/pt-br/refactoring
- **Code Smells**: https://refactoring.guru/pt-br/refactoring/smells
- **Técnicas**: https://refactoring.guru/pt-br/refactoring/techniques
- **Livro**: *Refactoring: Improving the Design of Existing Code* — Martin Fowler (2ª ed.)