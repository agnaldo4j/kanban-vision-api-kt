---
name: fp-oo-kotlin
description: >
  Aplique técnicas de programação funcional combinadas com OO em Kotlin neste projeto.
  Use este skill ao escrever funções puras, modelar erros com tipos, trabalhar com
  imutabilidade, compor transformações e decidir quando usar FP vs OO. Cobre os
  princípios do Uncle Bob sobre a complementaridade dos paradigmas e as técnicas
  práticas do Arrow-kt (Either, Raise, Optics).
argument-hint: "[file or use case to apply FP/OO techniques (optional)]"
allowed-tools: Read, Grep, Glob
---

# Programação Funcional + OO em Kotlin

> "Não existe FP vs OO. Os dois paradigmas são ortogonais — não se excluem,
> se complementam. Um bom sistema deve incorporar ambos."
> — Robert C. Martin (Uncle Bob), 2018
> Fonte: https://blog.cleancoder.com/uncle-bob/2018/04/13/FPvsOO.html
> Referência técnica: https://arrow-kt.io/learn/quickstart/

---

## A Tese Central: Paradigmas Ortogonais

Uncle Bob reduz cada paradigma à sua essência irredutível:

| Paradigma | Essência | Benefício principal |
|---|---|---|
| **OO** | Polimorfismo dinâmico — `o.f()` pode ter múltiplas implementações | Desacoplamento arquitetural, inversão de dependências |
| **FP** | Transparência referencial — `f(a) == f(b)` quando `a == b` | Previsibilidade, ausência de race conditions, testabilidade trivial |

Eles não competem porque resolvem problemas diferentes:
- OO resolve **onde o código depende de quê** (arquitetura, boundaries)
- FP resolve **como o código se comporta** (previsibilidade, segurança)

Um sistema que usa só OO perde previsibilidade. Um que usa só FP perde flexibilidade
arquitetural. A sinergia maximiza os dois.

---

## Os Quatro Pilares de FP em Kotlin

### 1. Funções Puras

Uma função pura tem duas propriedades:
- **Determinismo**: mesmos argumentos sempre produzem o mesmo resultado
- **Sem efeitos colaterais**: não modifica estado externo, não faz IO, não lança exceções

```kotlin
// ✅ Pura — determinística, sem efeitos
fun validateName(name: String): Boolean = name.isNotBlank()

// ✅ Pura — transforma sem modificar
fun Board.rename(newName: String): Board = copy(name = newName)

// ❌ Impura — depende de estado externo
fun generateId(): String = UUID.randomUUID().toString() // diferente a cada chamada

// ❌ Impura — efeito colateral (IO)
fun logAndValidate(name: String): Boolean {
    println("Validating: $name")   // efeito colateral
    return name.isNotBlank()
}
```

**Regra prática**: funções em `domain/` devem ser puras sempre que possível.
Efeitos (IO, banco, log) ficam nas bordas — `sql_persistence/` e `http_api/`.

### 2. Imutabilidade

Em Kotlin, imutabilidade é o padrão natural:

```kotlin
// ✅ data class é imutável por padrão — copy() cria nova instância
data class Card(
    val id: CardId,
    val columnId: ColumnId,
    val title: String,
    val description: String,
    val position: Int,
)

// ✅ Toda "modificação" retorna um novo objeto
fun Card.moveTo(targetColumn: ColumnId, newPosition: Int): Card =
    copy(columnId = targetColumn, position = newPosition)

// ❌ Estado mutável — evite em entidades de domínio
class MutableCard {
    var position: Int = 0   // `var` em entidade = sinal de alerta
    fun move(newPos: Int) { position = newPos }  // mutação in-place
}
```

**Regras de imutabilidade:**
- Entidades em `domain/` usam `val` em todos os campos — nunca `var`
- `data class` com `copy()` para toda transformação
- Coleções internas como `List` (não `MutableList`), `Map` (não `MutableMap`)
- `var` é permitido apenas em código de infraestrutura e wiring de DI

### 3. Funções de Alta Ordem e Composição

Kotlin trata funções como cidadãos de primeira classe:

```kotlin
// Função que recebe função — transformação genérica
fun <A, B> List<A>.mapValid(f: (A) -> B?): List<B> =
    mapNotNull(f)

// Composição de validações
val validateBoard: (String) -> Boolean =
    { name -> name.isNotBlank() && name.length <= 100 }

// Pipeline de transformação
fun processCards(cards: List<Card>): List<Card> =
    cards
        .filter { it.title.isNotBlank() }
        .sortedBy { it.position }
        .map { it.copy(title = it.title.trim()) }

// Extension functions são FP em estilo Kotlin
fun String.toTrimmedOrNull(): String? = trim().ifBlank { null }
```

### 4. Separação de Efeitos

O padrão funcional central: **mantenha a lógica pura no centro e empurre os
efeitos para as bordas**.

```
┌─────────────────────────────────────────────────┐
│  Bordas (efeitos): http_api, sql_persistence    │
│  IO, banco, HTTP, log, UUID, clock              │
│  ┌───────────────────────────────────────────┐  │
│  │  Núcleo (puro): domain, usecases          │  │
│  │  Transformações, validações, regras       │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

---

## Erros como Tipos — Arrow `Either` e `Raise`

O problema com exceções: elas são invisíveis na assinatura do tipo. Uma função
que lança `IllegalArgumentException` parece idêntica a uma que nunca falha.
Erros tipados tornam as falhas **explícitas e verificadas pelo compilador**.

### `Either<E, A>` — Resultado que pode ser erro ou sucesso

```kotlin
// Sem Arrow — exceção invisível
fun findBoard(id: String): Board  // pode lançar NoSuchElementException sem aviso

// Com Either — falha explícita na assinatura
fun findBoard(id: String): Either<BoardNotFound, Board>

// Criando valores
val success: Either<BoardNotFound, Board> = board.right()
val failure: Either<BoardNotFound, Board> = BoardNotFound(id).left()
```

### `Raise<E>` — Contexto de computação com falha tipada

`Raise` é a alternativa sem wrapper — o erro é propagado pelo contexto, não
embrulhado em um tipo. Mais ergonômico para encadeamento:

```kotlin
// Definição: função que pode "levantar" um erro tipado
fun Raise<BoardNotFound>.findBoard(id: String): Board =
    repository.findById(id) ?: raise(BoardNotFound(id))

// Consumo: o bloco `either { }` cria o contexto Raise
val result: Either<BoardNotFound, Board> = either {
    val board = findBoard(boardId)     // bind implícito via Raise
    val cards = findCards(board.id)    // qualquer raise aborta aqui
    board.copy(cards = cards)
}
```

### Operações Essenciais

```kotlin
// bind() — extrai o valor ou aborta com o erro
val board: Board = findBoard(id).bind()

// fold() — trata ambos os casos explicitamente
findBoard(id).fold(
    ifLeft  = { error: BoardNotFound -> respond(404, error.message) },
    ifRight = { board: Board         -> respond(200, board.toResponse()) },
)

// getOrElse() — fallback para o caso de erro
val board = findBoard(id).getOrElse { Board.default() }

// recover() — transforma o erro em outro erro ou em sucesso
findBoard(id)
    .recover { _: BoardNotFound -> raise(ResourceNotFound("board", id)) }

// mapLeft() — transforma o tipo do erro sem tocar no sucesso
findBoard(id).mapLeft { e -> ApiError(404, e.message) }

// map() — transforma o sucesso sem tocar no erro
findBoard(id).map { board -> board.toResponse() }
```

### `ensure` e `ensureNotNull` — Validação como Raise

```kotlin
fun Raise<ValidationError>.validateCommand(cmd: CreateBoardCommand): CreateBoardCommand {
    ensure(cmd.name.isNotBlank()) {
        ValidationError("name", "Nome não pode ser vazio")
    }
    ensure(cmd.name.length <= 100) {
        ValidationError("name", "Nome não pode ter mais de 100 caracteres")
    }
    ensureNotNull(cmd.name.ifBlank { null }) {
        ValidationError("name", "Nome inválido")
    }
    return cmd
}
```

### Acumulação de Erros — Validações Independentes

Quando múltiplas validações devem ser executadas e **todos os erros devem ser
reportados juntos** (não só o primeiro):

```kotlin
// zipOrAccumulate — validações independentes em paralelo
fun validateCreateBoard(name: String, ownerId: String): Either<NonEmptyList<ValidationError>, CreateBoardCommand> =
    either {
        zipOrAccumulate(
            { ensure(name.isNotBlank()) { ValidationError("name", "Não pode ser vazio") } },
            { ensure(name.length <= 100) { ValidationError("name", "Máximo 100 caracteres") } },
            { ensure(ownerId.isNotBlank()) { ValidationError("ownerId", "Obrigatório") } },
        ) { _, _, _ -> CreateBoardCommand(name, ownerId) }
    }

// mapOrAccumulate — valida cada item de uma lista
fun validateCards(cards: List<CreateCardRequest>): Either<NonEmptyList<ValidationError>, List<CreateCardCommand>> =
    cards.mapOrAccumulate { card ->
        ensure(card.title.isNotBlank()) { ValidationError("title", "Obrigatório") }
        CreateCardCommand(card.columnId, card.title, card.description)
    }
```

### Tratando Exceções de Infraestrutura na Borda

Exceções de banco, rede e IO **não devem vazar para o domínio**. Converta-as
em erros tipados no adaptador:

```kotlin
// sql_persistence — converte SQLException em erro de domínio
suspend fun Raise<PersistenceError>.saveBoard(board: Board): Unit =
    catch({
        jdbcSave(board)
    }) { e: SQLException ->
        if (e.isUniqueViolation()) raise(PersistenceError.DuplicateKey(board.id.value))
        else raise(PersistenceError.Unknown(e.message ?: "DB error"))
    }
```

---

## Dados Imutáveis Aninhados — Arrow Optics

O problema do `copy()` em cascata para estruturas aninhadas:

```kotlin
// ❌ Verboso e frágil — copy aninhado
fun Board.updateCardTitle(cardId: CardId, newTitle: String): Board =
    copy(
        columns = columns.map { col ->
            col.copy(
                cards = col.cards.map { card ->
                    if (card.id == cardId) card.copy(title = newTitle) else card
                }
            )
        }
    )
```

Com Arrow Optics:

```kotlin
// Anotação gera lenses automaticamente via KSP
@optics data class Board(val id: BoardId, val name: String, val columns: List<Column>) {
    companion object
}

@optics data class Column(val id: ColumnId, val cards: List<Card>) {
    companion object
}

// ✅ Transformação declarativa e legível
fun Board.updateCardTitle(cardId: CardId, newTitle: String): Board =
    this.copy {
        Board.columns.every(Every.list()).cards.every(Every.list())
            .filter { it.id == cardId }
            .title transform { newTitle }
    }
```

### Quando Usar Optics

| Situação | Ferramenta |
|---|---|
| Estrutura com 1-2 níveis | `copy()` nativo do Kotlin |
| Estrutura com 3+ níveis aninhados | Arrow Optics |
| Modificar elemento em lista aninhada | Arrow `Every` + `filter` |
| Transformar campo opcional | Arrow `Optional` |

---

## FP + OO: Quando Usar Cada Um

A combinação prática segue a arquitetura do projeto:

| Camada | OO (polimorfismo) | FP (funções puras) |
|---|---|---|
| **domain/** | `sealed class` para modelar estados | `data class` imutável, funções puras de transformação |
| **usecases/** | Interface `BoardRepository` (DIP) | `Either`/`Raise` para erros, pipeline de transformação |
| **sql_persistence/** | Implementa interface de repositório | `catch {}` converte exceções em erros tipados |
| **http_api/** | Polimorfismo do Ktor (plugins, interceptors) | `fold()` para mapear `Either` em resposta HTTP |

### Polimorfismo OO para Desacoplamento

```kotlin
// Interface OO — permite múltiplas implementações (JDBC, in-memory, mock)
interface BoardRepository {
    suspend fun save(board: Board)
    suspend fun findById(id: BoardId): Board?
}

// Teste usa implementação in-memory — sem tocar no UseCase
class InMemoryBoardRepository : BoardRepository {
    private val store = mutableMapOf<BoardId, Board>()
    override suspend fun save(board: Board) { store[board.id] = board }
    override suspend fun findById(id: BoardId): Board? = store[id]
}
```

### FP para Lógica de Negócio

```kotlin
// Funções puras em domain — sem IO, sem estado, sem surpresas
fun Board.validate(): Either<BoardError, Board> = either {
    ensure(name.isNotBlank()) { BoardError.EmptyName }
    ensure(name.length <= 100) { BoardError.NameTooLong(name.length) }
    this@validate
}

// Pipeline funcional no UseCase
fun Raise<CreateBoardError>.execute(command: CreateBoardCommand): BoardId {
    val validatedName = ensure(command.name.isNotBlank()) {
        CreateBoardError.InvalidName("Nome não pode ser vazio")
    }
    val board = Board(id = BoardId.generate(), name = command.name)
    repository.save(board)
    return board.id
}
```

---

## `sealed class` — FP para Modelagem de Domínio

`sealed class` e `sealed interface` são a forma idiomática de modelar estados
e erros de domínio em Kotlin com segurança de compilação:

```kotlin
// Estados de um Board
sealed interface BoardStatus {
    data object Active : BoardStatus
    data object Archived : BoardStatus
    data class Suspended(val reason: String) : BoardStatus
}

// Erros tipados por operação
sealed interface BoardError {
    data object EmptyName : BoardError
    data class NameTooLong(val length: Int) : BoardError
    data class NotFound(val id: String) : BoardError
}

// when exaustivo — compilador garante todos os casos
fun BoardError.toMessage(): String = when (this) {
    is BoardError.EmptyName         -> "Nome não pode ser vazio"
    is BoardError.NameTooLong       -> "Nome muito longo: $length caracteres"
    is BoardError.NotFound          -> "Board não encontrado: $id"
}
```

**Regra**: `when` em `sealed class` deve ser **exaustivo** — sem `else`.
O `else` esconde casos que o compilador poderia detectar.

---

## Checklist ao Escrever Código

### Para funções em `domain/` e `usecases/`

- [ ] A função é pura? (mesmo input → mesmo output, sem efeitos colaterais)
- [ ] Erros de domínio são modelados como tipos (`sealed class`, `Either`, `Raise`)?
- [ ] Campos de `data class` são todos `val`?
- [ ] Coleções são imutáveis (`List`, `Map`, `Set` — não `Mutable*`)?
- [ ] `when` em `sealed class` é exaustivo (sem `else`)?
- [ ] Nenhuma exceção é lançada diretamente — usa `raise()` ou `Either`?

### Para adaptadores em `sql_persistence/` e `http_api/`

- [ ] Exceções de infraestrutura são convertidas em erros tipados com `catch { }`?
- [ ] `Either.fold()` é usado para mapear erros em respostas HTTP?
- [ ] IO e efeitos ficam confinados a esta camada (não vazam para `usecases/`)?

### Para novas interfaces e polimorfismo

- [ ] A interface usa OO para inversão de dependência (DIP)?
- [ ] Implementações são substituíveis sem surpresas (LSP)?
- [ ] Testes usam implementações in-memory puras, não mocks de IO?

---

## Sinais de Alerta

| Sinal | Problema | Solução |
|---|---|---|
| `var` em `data class` de domínio | Mutabilidade desnecessária | Troque por `val` + `copy()` |
| `throw` em `usecases/` ou `domain/` | Exceção em vez de erro tipado | Use `raise()` ou retorne `Either` |
| `try/catch` em `usecases/` | Tratando exceção de infraestrutura no núcleo | Mova para o adaptador com `catch { }` |
| `when (x) { ... else -> }` em `sealed class` | Caso não tratado escondido | Remova o `else`, force exaustividade |
| Função que modifica estado global | Efeito colateral no domínio | Extraia o efeito para a borda |
| `MutableList` em entidade de domínio | Coleção mutável exposta | Troque por `List` imutável |
| Lógica de negócio dentro de `catch` | Regra misturada com tratamento de erro | Separe: trate o erro, depois aplique a regra |
| Múltiplos `copy()` aninhados (3+ níveis) | Verbosidade e fragilidade | Avalie Arrow Optics |

---

## Relação com os Outros Skills

| Skill | Como se conecta com FP + OO |
|---|---|
| **clean-architecture** | Dependency Rule = DIP de OO; funções puras no núcleo = FP nas camadas internas |
| **screaming-architecture** | Nomes de domínio em `sealed class` gritam o negócio |
| **solid-principles** | SRP: funções puras têm uma razão para existir; DIP: interfaces OO para inversão |
| **kotlin-quality-pipeline** | Detekt detecta complexidade excessiva → oportunidade para funções puras menores |

---

## Referência Rápida — Receitas

```kotlin
// Criar erro tipado
sealed interface MyError {
    data class NotFound(val id: String) : MyError
    data object Unauthorized : MyError
}

// Função que pode falhar (estilo Raise)
fun Raise<MyError>.findSomething(id: String): Thing =
    repository.find(id) ?: raise(MyError.NotFound(id))

// Compor operações que podem falhar
val result: Either<MyError, Thing> = either {
    val a = findSomething(id1)   // falha aborta aqui
    val b = findSomething(id2)   // só executa se a teve sucesso
    combine(a, b)
}

// Tratar no adaptador HTTP
result.fold(
    ifLeft  = { error -> call.respond(HttpStatusCode.NotFound, mapOf("error" to error.toString())) },
    ifRight = { value -> call.respond(HttpStatusCode.OK, value.toResponse()) },
)

// Acumular erros de validação
val validated = either {
    zipOrAccumulate(
        { ensure(name.isNotBlank()) { ValidationError.EmptyName } },
        { ensure(age > 0) { ValidationError.NegativeAge } },
    ) { _, _ -> ValidatedInput(name, age) }
}

// Transformar dados imutáveis
val updated = original.copy(field = newValue)
```