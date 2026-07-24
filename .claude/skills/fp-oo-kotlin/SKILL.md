---
name: fp-oo-kotlin
description: >
  Aplique técnicas de programação funcional combinadas com OO em Kotlin neste projeto.
  Use este skill ao escrever funções puras, modelar erros com tipos, trabalhar com
  imutabilidade, compor transformações e decidir quando usar FP vs OO. Cobre os
  princípios do Uncle Bob sobre a complementaridade dos paradigmas, funções de alta
  ordem e composição (tipos de função, lambdas, referências, currying, andThen/compose),
  a teoria de Algebraic Data Types (tipos soma/produto, cardinalidade,
  illegal-states-unrepresentable), a Teoria de Categorias que fundamenta map/flatMap/either
  (Functor, Applicative, Monad e suas leis) e as técnicas práticas do Arrow-kt (Either, Raise, Optics).
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

Kotlin trata funções como **cidadãos de primeira classe**: toda função tem um **tipo**, pode ser guardada
em `val`, passada como argumento e devolvida como resultado. Uma **função de alta ordem (HOF)** é qualquer
função que recebe ou retorna outra função. É esta a técnica que a Teoria de Categorias (seção adiante)
formaliza: uma função pura `(A) -> B` é um **morfismo** de `A` para `B`, e compor funções é o ato central
de FP.

> Referências: [Kotlin — Higher-order functions & lambdas](https://kotlinlang.org/docs/lambdas.html) ·
> [Kodeco — Function Fundamentals](https://www.kodeco.com/books/functional-programming-in-kotlin-by-tutorials/v1.0/chapters/2-function-fundamentals)

#### Tipos de função, lambdas e referências

```kotlin
// Tipo de função (A) -> B — um tipo como qualquer outro, com valor de primeira classe
val validateBoard: (String) -> Boolean = { name -> name.isNotBlank() && name.length <= 100 }

// `it` — o parâmetro único implícito de um lambda; trailing lambda sai dos parênteses
cards.filter { it.title.isNotBlank() }

// Tipo com receiver A.(B) -> C — o corpo roda "dentro de" um A; é a base dos DSLs (ex.: `either { }`)
val describe: Card.() -> String = { "$title @ $position" }   // `this` é o Card, `title` é seu campo

// Referência de função (::) — reaproveita função/membro existente como valor, sem reescrever o lambda
cards.map(Card::title)                     // referência de membro
val parse: (String) -> Int = String::toInt // referência de função

// Closure — o lambda captura o ambiente léxico (aqui, `max`), virando uma função configurável
fun titleUnder(max: Int): (Card) -> Boolean = { it.title.length <= max }

// HOF genérica — recebe a transformação como parâmetro
fun <A, B> List<A>.mapValid(f: (A) -> B?): List<B> = mapNotNull(f)
```

#### Composição — o coração da FP

Compor é encadear funções ponta a ponta: a saída de uma vira a entrada da próxima. É exatamente a
**composição de morfismos** da categoria (seção adiante): **associativa** e com a **função identidade**
como elemento neutro. Só vale para funções **puras** (pilar 1) — um efeito colateral quebra as leis.

```kotlin
// Defina os operadores uma vez — nem a stdlib nem o Arrow 2.x trazem `andThen`/`compose` para (A) -> B:
infix fun <A, B, C> ((A) -> B).andThen(g: (B) -> C): (A) -> C = { a -> g(this(a)) }
infix fun <A, B, C> ((B) -> C).compose(f: (A) -> B): (A) -> C = { a -> this(f(a)) }

// andThen: (f andThen g)(x) == g(f(x))   |   compose: (g compose f)(x) == g(f(x))
val trim: (String) -> String = String::trim
val toUpper: (String) -> String = String::uppercase
val normalize: (String) -> String = trim andThen toUpper   // trim primeiro, depois uppercase

// Identidade — o neutro da composição, valendo por extensão (para todo x), não por igualdade de objeto:
//   (f andThen ::id)(x) == f(x) == (::id andThen f)(x)
fun <A> id(a: A): A = a

// Pipeline de transformação — composição idiomática via as coleções (cada passo é puro)
fun processCards(cards: List<Card>): List<Card> =
    cards
        .filter { it.title.isNotBlank() }
        .sortedBy { it.position }
        .map { it.copy(title = it.title.trim()) }
```

> **Nota de stack (pinada):** `(A) -> B` **não** ganha `andThen`/`compose` prontos — nem da stdlib do
> Kotlin, nem do Arrow. Na série 2.0 o Arrow **removeu** esses helpers; em `arrow-core:2.2.3` (versão deste
> projeto) `arrow.core` expõe só `identity`. Portanto **defina os operadores localmente** (acima) ou componha
> direto via as coleções/`let` — não confie num `import arrow.core.andThen`, que não resolve.

#### `inline`, currying e aplicação parcial

```kotlin
// inline — HOFs de biblioteca (map/filter/let/…) são inline: o lambda não vira objeto em runtime
inline fun <T> T.applyIf(cond: Boolean, f: (T) -> T): T = if (cond) f(this) else this

// Currying: (A, B) -> C  vira  (A) -> (B) -> C  — fixa argumentos um a um
val add: (Int) -> (Int) -> Int = { a -> { b -> a + b } }
val inc: (Int) -> Int = add(1)             // aplicação parcial: `add` com o 1º argumento já fixado

// Extension functions são a forma idiomática Kotlin de HOF/composição
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

> **Princípio do projeto (ADR-0044) — transparência referencial + funções puras + erros tipados no lugar de
> `throw`.** Em **todas as camadas**, uma falha de **regra de negócio / domínio ou de controle de fluxo** é
> expressa como `Either`/`Raise` — nunca cortando a execução com exceção. Isso inclui os **agregados de
> domínio** (`domain-*`): um método de **operação** que falha por regra/lookup retorna `Either<DomainError, T>`
> e usa `raise(...)`, com a hierarquia de erro do próprio bounded context (`CommonError`/`KanbanError`/
> `SimulationError`). A exceção fica reservada a **precondição de construção/argumento** (`require`/`check`
> em `init`/factory — **bug do chamador**, fail-fast): é o único guard que roda através do `copy()` sintético
> de uma `data class` (removê-lo deixaria o `copy()` construir estado inválido; value classes e serialização
> dependem igual do construtor). Regra prática: **falha-de-domínio → `Either`; precondição → `require`.**

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

## Algebraic Data Types (ADTs) — a Álgebra dos Tipos

Um tipo é um **conjunto de valores**. Quantos valores ele admite é sua **cardinalidade** — e essa
cardinalidade obedece a uma **álgebra**. Modelar bem um domínio é escolher o tipo cuja cardinalidade seja
**exatamente** o conjunto de estados válidos: nem mais (estados ilegais representáveis), nem menos.

> ADTs: https://softwaremill.com/functional-programming-in-kotlin/ ·
> https://www.kodeco.com/11593767-functional-programming-with-kotlin-and-arrow-algebraic-data-types ·
> https://kotlinlang.org/docs/sealed-classes.html

### Tipo Produto (`×`) — "E"

`data class`, `Pair`, `Triple`: **todos** os campos coexistem. A cardinalidade é o **produto** das
cardinalidades dos campos.

```kotlin
enum class Triage { NONE, LOW, HIGH }              // 3 valores

// Produto: todos os campos ao mesmo tempo
data class Struct(val enabled: Boolean, val triage: Triage, val value: Byte)
// |Struct| = |Boolean| × |Triage| × |Byte| = 2 × 3 × 256 = 1 536

// No domínio: uma entidade É um produto dos seus campos (com value-class IDs, ADR-0034)
data class Card(val id: CardId, val title: String, val serviceClass: ServiceClass)
// |Card| = |CardId| × |String| × |ServiceClass|
```

`Unit` — e todo `object` — tem cardinalidade **1**: é a **identidade do produto** (`1 × n = n`), não carrega
informação, só "existe".

### Tipo Soma (`+`) — "OU"

`sealed interface`/`sealed class`, `enum class` e `data object`: exatamente **uma** variante existe em
runtime. A cardinalidade é a **soma** das cardinalidades das variantes.

```kotlin
// Soma real do projeto: uma decisão é UMA de quatro variantes (cada variante é um produto)
sealed interface Decision {
    data class MoveItem(val cardId: CardId) : Decision
    data class BlockItem(val cardId: CardId, val reason: String) : Decision
    data class UnblockItem(val cardId: CardId) : Decision
    data class AddItem(val title: String, val serviceClass: ServiceClass) : Decision
}
// |Decision| = |MoveItem| + |BlockItem| + |UnblockItem| + |AddItem|

// enum é uma soma de variantes nulárias (cada constante vale 1)
enum class ServiceClass { STANDARD, FIXED_DATE, EXPEDITE, INTANGIBLE }   // 1+1+1+1 = 4
```

`Nothing` tem cardinalidade **0** — não é instanciável. É o **absorvente do produto** (`0 × n = 0`) e o
**neutro da soma** (`0 + n = n`); é o tipo de `raise()`/`throw`/loop infinito (nunca produz um valor).

**Os tipos do Arrow são ADTs:** `Either<E, A>` = `E + A` (soma: `Left(E)` **ou** `Right(A)`);
`Option<A>` = `A + 1` (`Some(A)` ou `None`, com `None ≡ Unit`); `NonEmptyList<A>` = `A × List<A>`. É por isso
que `Either`/`Option`/`Raise` compõem exatamente como a álgebra prevê.

### Tipo Função (`exponente`)

`(A) -> B` tem `|B|^|A|` habitantes — uma escolha de `B` para cada um dos `|A|` inputs. Ex.:
`(Boolean) -> Boolean` = `2² = 4` funções possíveis. Por isso funções de alta ordem (acima) são "dados"
tanto quanto qualquer `data class`.

### Mapa: construto Kotlin → papel algébrico

| Construto | Papel | Cardinalidade |
|---|---|---|
| `data class` / `Pair` / `Triple` | Produto (`×`) | produto dos campos |
| `sealed interface` / `sealed class` | Soma (`+`) | soma das variantes |
| `enum class` | Soma de nulárias | nº de constantes |
| `object` / `data object` / `Unit` | Unidade | **1** |
| `Nothing` | Vazio | **0** |
| `(A) -> B` | Exponente | `card(B) ^ card(A)` |
| `A?` | `A + 1` | `card(A) + 1` |

### O Payoff: *Make Illegal States Unrepresentable*

A álgebra tem uma consequência de design: **um produto de flags/nuláveis quase sempre admite combinações
inválidas** — cardinalidade maior que o conjunto legal. Troque o produto por uma **soma** cujas variantes
sejam só os estados válidos.

```kotlin
// ❌ Produto: |LoadState| = 2 × (|Board|+1) × (|String|+1) habitantes (cada `?` é A+1, ver acima).
//    Olhando só a PRESENÇA/ausência de cada campo já são 2 × 2 × 2 = 8 "formas", das quais só 3 são
//    legais. O que significa loading=true E error != null? Estado ilegal — porém compila.
data class LoadState(
    val loading: Boolean,
    val data: Board?,
    val error: String?,
)

// ✅ Soma: |LoadState| = 1 + |Board| + |String| — exatamente os 3 estados legais; a forma ilegal
//    (loading E erro ao mesmo tempo) simplesmente não é representável.
sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val data: Board) : LoadState
    data class Failed(val error: String) : LoadState
}
```

O `when` exaustivo (sem `else`) sobre a soma força tratar cada variante — ver a seção seguinte. É a mesma
razão pela qual os grupos de erro do projeto (`CommonError`/`KanbanError`/`SimulationError`) e o `Decision`
são `sealed`: a soma **fecha o conjunto de variantes** — nenhuma variante fora dela é representável e o `when`
é exaustivo.

> **Fechar as variantes ≠ validar os campos.** A soma elimina *formas* ilegais (variantes fora do conjunto),
> não valores de campo inválidos *dentro* de uma variante: `Decision.AddItem(title)` ainda aceita um `title`
> em branco que só o `Card.create()` rejeita depois. Validade de campo é papel de **smart constructors** /
> tipos refinados (ex.: um value class `NonBlankTitle`), complementar — não substituto — do tipo-soma.

---

## Teoria de Categorias — a estrutura por trás de `map`, `flatMap` e `either { }`

Category theory é a **álgebra da composição**. O projeto **já** a usa toda vez que encadeia `map`,
`flatMap` ou abre um `either { }` — esta seção **nomeia** a estrutura para aplicá-la com intenção, não por
acaso. É a contraparte "comportamento" da seção **ADTs** (que trata os tipos como "dados"): ADTs dão os
**objetos**, a Teoria de Categorias dá os **morfismos** entre eles e as leis de composição.

> Referência: [arrow-kt/Category-Theory-for-Programmers.kt](https://github.com/arrow-kt/Category-Theory-for-Programmers.kt)
> — a versão Kotlin do clássico de Bartosz Milewski. Fundamenta os tipos do Arrow que o projeto usa.

### A categoria: tipos são objetos, funções são morfismos

Uma **categoria** tem objetos e **morfismos** (setas) entre eles, com três leis:

- **Composição** — dados `f: A -> B` e `g: B -> C`, existe `g ∘ f: A -> C` (é o `andThen` do pilar 3).
- **Identidade** — todo objeto tem `id: A -> A`, o neutro da composição.
- **Associatividade** — `h ∘ (g ∘ f) == (h ∘ g) ∘ f`.

Em Kotlin os **objetos** são os tipos (`String`, `Card`, `BoardId`…) e os **morfismos** são as funções
**puras** `(A) -> B`. É por isso que pureza (pilar 1) + composição (pilar 3) são a fundação: sem pureza a
composição não obedece às leis — um efeito colateral quebra a associatividade.

Da seção **ADTs**: `Unit` (cardinalidade **1**) é o **objeto terminal** — de todo tipo existe exatamente
uma função `(A) -> Unit`; `Nothing` (cardinalidade **0**) é o **objeto inicial** — existe uma única função
`(Nothing) -> A` para qualquer `A` (nunca chamada, pois `Nothing` não tem valores). É a mesma dualidade
`1`/`0` que fecha a álgebra dos tipos.

### Functor — `map`: eleva uma função a um contexto, preservando a forma

Um **functor** é um construtor de tipo `F<_>` com um `map` que aplica `(A) -> B` **dentro** do contexto
sem mudar a estrutura. `List`, `Either<E, _>`, `Option` e `A?` são todos functores:

```kotlin
listOf(1, 2, 3).map { it * 2 }             // List é functor
findBoard(id).map { it.toResponse() }      // Either<E, _> — map só toca o Right; Left passa reto
maybeName.map { it.uppercase() }           // Option — None passa reto
```

**Leis do functor** (o que garante que `map` "só transforma o conteúdo"):

- **Identidade**: `xs.map { it } == xs`
- **Composição**: `xs.map(f).map(g) == xs.map { g(f(it)) }` — dois `map` encadeados = compor as funções.

### Applicative — combinar contextos independentes

Um **applicative** acrescenta `pure` (embrulha um valor puro: `a.right()`, `Some(a)`) e uma forma de
**combinar efeitos independentes**. No projeto isso é o `zipOrAccumulate` — a razão pela qual validações
de campo independentes **acumulam todos os erros** em vez de parar no primeiro:

```kotlin
either {
    zipOrAccumulate(
        { ensure(name.isNotBlank()) { ValidationError("name", "vazio") } },
        { ensure(ownerId.isNotBlank()) { ValidationError("ownerId", "vazio") } },
    ) { _, _ -> CreateBoardCommand(name, ownerId) }
}
```

Independente ⇒ applicative (`zip`/`zipOrAccumulate`); dependente ⇒ monad (abaixo). É a estrutura, não uma
convenção, que dá a acumulação "de graça".

### Monad — `flatMap`/`bind`: sequenciar passos onde um depende do anterior

Um **monad** acrescenta `flatMap` (`(A) -> F<B>` achatado em `F<B>`) — sequência com **dependência**: o
próximo passo precisa do resultado do anterior, e qualquer falha **curto-circuita**. O `either { }` é a
**notação-do** monádica do projeto; cada `.bind()` sobre um `Either` **é** o `flatMap` do monad — extrai o
`Right` ou aborta no `Left` (chamar uma função `Raise<E>`-receiver faz o mesmo bind, aí implícito):

```kotlin
val result: Either<DomainError, Board> = either {
    val board = findBoard(id).bind()          // não depende de nada antes
    val cards = findCards(board.id).bind()    // depende de `board` — por isso flatMap, não zip
    board.copy(cards = cards)                 // qualquer .bind() que dê Left aborta aqui
}
```

**Leis do monad** (garantem que `either { }`/`flatMap` compõem sem surpresa):

- **Identidade à esquerda**: `pure(a).flatMap(f) == f(a)`
- **Identidade à direita**: `m.flatMap { pure(it) } == m`
- **Associatividade**: `m.flatMap(f).flatMap(g) == m.flatMap { f(it).flatMap(g) }`

### Por que isso importa aqui — aplicar com firmeza

- `Either`/`Option`/`List` compõem **porque são** Functor/Applicative/Monad — não é coincidência, é a
  álgebra (ver **ADTs**: `Either = E + A`, `Option = A + 1`).
- **Escolha a estrutura mais fraca que resolve**: transformar → `map` (functor); combinar independentes →
  `zipOrAccumulate` (applicative, **acumula** erros); sequenciar dependentes → `either { }`/`bind` (monad,
  **curto-circuita**). Usar monad onde applicative bastava **perde a acumulação de erros** — é o erro de
  design mais comum nas validações.
- É a mesma lei em todo lugar: a composição associativa com identidade é o que torna pipelines de
  `map`/`flatMap` refatoráveis sem medo — a base de `usecases/` e das bordas HTTP com `fold`.

---

## `sealed class` — o mecanismo de tipo-soma para Modelagem de Domínio

`sealed class` e `sealed interface` são o **mecanismo de tipo-soma** de Kotlin (ver ADTs acima) — a forma
idiomática de modelar estados e erros de domínio com segurança de compilação:

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

### `sealed` vs `enum` — quando cada um

Ambos são somas (`+`), mas diferem no que cada variante carrega (docs Kotlin):

| | `enum class` | `sealed interface`/`class` |
|---|---|---|
| Variante carrega dados? | Não — constante única (instância singleton) | Sim — cada variante pode ser `data class` com campos próprios |
| Cardinalidade | nº de constantes (soma de `1`s) | soma das cardinalidades das variantes |
| Herança | não estende `sealed class`; **pode implementar** `sealed interface` | variantes são `data class`/`data object`/subclasses |

- **`enum`** quando as variantes são rótulos sem dados (ex.: `ServiceClass`, `SortOrder`).
- **`sealed`** quando as variantes carregam payloads distintos (ex.: `Decision`, `SimulationError`).
- **Colocação (regra de compilação):** subclasses **diretas** de um `sealed` devem estar no **mesmo módulo e
  pacote** — é o que fecha a soma e habilita o `when` exaustivo. (No projeto, cada grupo `*Error`/`Decision`
  fica junto do seu agregado — ver `screaming-architecture`.)

---

## Checklist ao Escrever Código

### Para funções em `domain/` e `usecases/`

- [ ] A função é pura? (mesmo input → mesmo output, sem efeitos colaterais)
- [ ] Erros de domínio são modelados como tipos (`sealed class`, `Either`, `Raise`)?
- [ ] Campos de `data class` são todos `val`?
- [ ] Coleções são imutáveis (`List`, `Map`, `Set` — não `Mutable*`)?
- [ ] `when` em `sealed class` é exaustivo (sem `else`)?
- [ ] Modelou com o **menor tipo** que representa exatamente os estados válidos (illegal states unrepresentable)? Preferiu uma **soma** (`sealed`) a um produto de flags/nuláveis quando as combinações têm casos inválidos?
- [ ] Nenhuma exceção é lançada diretamente — usa `raise()` ou `Either`?
- [ ] Preferiu **compor** funções puras (`map`/`andThen`/pipeline de coleção) a lógica imperativa com estado temporário?
- [ ] Escolheu a estrutura **mais fraca** que resolve — `map` (functor) < `zipOrAccumulate` (applicative, **acumula** erros) < `either { }`/`bind` (monad, **curto-circuita**)? Usar monad onde applicative bastava perde a acumulação.

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
| `throw`/`error()` para falha de **regra/negócio** (qualquer camada, incl. operações de agregado) | Corta a execução em vez de tipar a falha | `raise()`/`Either` (ADR-0044); `require`/`check` só para precondição de construção/argumento |
| `try/catch` em `usecases/` | Tratando exceção de infraestrutura no núcleo | Mova para o adaptador com `catch { }` |
| `when (x) { ... else -> }` em `sealed class` | Caso não tratado escondido | Remova o `else`, force exaustividade |
| Função que modifica estado global | Efeito colateral no domínio | Extraia o efeito para a borda |
| `MutableList` em entidade de domínio | Coleção mutável exposta | Troque por `List` imutável |
| Lógica de negócio dentro de `catch` | Regra misturada com tratamento de erro | Separe: trate o erro, depois aplique a regra |
| Múltiplos `copy()` aninhados (3+ níveis) | Verbosidade e fragilidade | Avalie Arrow Optics |
| Produto de `Boolean`/`?`-nuláveis com combinações inválidas | Cardinalidade > estados legais (illegal states representáveis) | Troque o produto por uma **soma** (`sealed interface`) só com as variantes válidas |
| `either { }`/`flatMap` para validações **independentes** | Curto-circuita no 1º erro — perde a acumulação | Use `zipOrAccumulate`/`mapOrAccumulate` (applicative) para reportar todos os erros |
| `.map { }` seguido de `.bind()`/`getOrNull()!!` para achatar `Either` aninhado | Functor onde faltava monad → `Either<E, Either<E, A>>` | Use `flatMap` (monad) — achata em um passo |
| Encadeamento imperativo com `var`/estado temporário onde caberia composição | Ignora a composição de morfismos (associativa, pura) | Componha funções puras: `andThen`/pipeline de coleção/`map` |

---

## Exposed ORM + FP — Persistência Funcional

O projeto usa **Exposed DSL** (não DAO) no módulo `sql_persistence/`. O modo DSL é FP-friendly:
transações retornam valores, queries são compostas sem mutação, e `ResultRow` é mapeado para
data classes imutáveis do domínio.

Reference: https://www.jetbrains.com/help/exposed/home.html

### Princípio central: `transaction {}` retorna valor

```kotlin
// ✅ transaction devolve o último valor — sem variável mutável
val simulation: Simulation? = transaction {
    SimulationsTable
        .selectAll()
        .where { SimulationsTable.id eq id.value }
        .singleOrNull()
        ?.toDomain()    // mapeado ainda dentro do bloco
}
```

### Padrão obrigatório: `either {}` + `catch {}` + `transaction {}`

Todo método de repositório retorna `Either<DomainError, T>`.
A conversão de `Exception` em erro tipado acontece na borda, nunca em `usecases/` ou `domain/`:

```kotlin
import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

// Repositório — borda de infraestrutura
fun findById(id: SimulationId): Either<DomainError, Simulation?> =
    either {
        catch({
            transaction {
                SimulationsTable
                    .selectAll()
                    .where { SimulationsTable.id eq id.value }
                    .singleOrNull()
                    ?.toDomain()
            }
        }) { e: Exception ->
            raise(DomainError.PersistenceError(e.message ?: "DB error"))
        }
    }

// Use case — consome sem ver Exception
fun Raise<DomainError>.execute(query: GetSimulationQuery): Simulation {
    return repository.findById(query.id).bind()
        ?: raise(DomainError.NotFound("Simulation", query.id.value.toString()))
}
```

### Mapeamento puro: `ResultRow.toDomain()`

O mapeamento de `ResultRow` para entidade de domínio é uma **função pura de extensão**.
Nunca inline lógica de mapeamento na chain de query:

```kotlin
// sql_persistence/mappers/SimulationMapper.kt

// ✅ Puro — mesmo input, mesmo output, sem efeitos
fun ResultRow.toDomain(): Simulation =
    Simulation(
        id             = SimulationId(this[SimulationsTable.id]),
        organizationId = OrganizationId(this[SimulationsTable.organizationId]),
        wipLimit       = this[SimulationsTable.wipLimit],
        teamSize       = this[SimulationsTable.teamSize],
        seedValue      = this[SimulationsTable.seedValue],
        state          = Json.decodeFromString(this[SimulationsTable.state]),
    )

// ✅ Usado na query como transformação final
transaction {
    SimulationsTable.selectAll()
        .where { SimulationsTable.organizationId eq orgId.value }
        .map { it.toDomain() }   // lista imutável de domain objects
}
```

### Composição funcional de queries

Exposed DSL permite composição sem mutação — cada operador retorna a query modificada:

```kotlin
// Pipeline declarativo — sem `var query`
fun findActiveSimulations(
    orgId: OrganizationId,
    status: SimulationStatus?,
    limit: Int
): List<Simulation> = transaction {
    SimulationsTable
        .selectAll()
        .where { SimulationsTable.organizationId eq orgId.value }
        .apply { status?.let { andWhere { SimulationsTable.status eq it.name } } }
        .orderBy(SimulationsTable.createdAt to SortOrder.DESC)
        .limit(limit)
        .map { it.toDomain() }
}
```

Para filtros condicionais sem `apply`, use `andIfNotNull`:

```kotlin
.where { SimulationsTable.organizationId eq orgId.value }
.andWhere { status?.let { SimulationsTable.status eq it.name } ?: Op.TRUE }
```

### INSERT como operação pura de efeito explícito

```kotlin
fun save(simulation: Simulation): Either<DomainError, Unit> =
    either {
        catch({
            transaction {
                SimulationsTable.insert {
                    it[id]             = simulation.id.value
                    it[organizationId] = simulation.organizationId.value
                    it[wipLimit]       = simulation.wipLimit
                    it[teamSize]       = simulation.teamSize
                    it[seedValue]      = simulation.seedValue
                    it[state]          = Json.encodeToString(simulation.state)
                }
                Unit
            }
        }) { e: Exception ->
            when {
                e.isUniqueViolation() -> raise(DomainError.Conflict("Simulation already exists"))
                else                  -> raise(DomainError.PersistenceError(e.message ?: "DB error"))
            }
        }
    }
```

### Modo DSL vs DAO — por que só DSL

| Critério | DSL ✅ | DAO ❌ |
|---|---|---|
| Imutabilidade | Retorna `ResultRow` → mapeado para `data class` | Entidades mutáveis (`var`) |
| FP style | Funções puras de transformação | Efeitos colaterais em propriedades |
| Testabilidade | Mock do repositório inteiro | Acoplado ao framework |
| Aderência ao projeto | Alinha com `Either`/`Raise`/`copy()` | Viola regra de `val` em entidades |

### Sinais de alerta específicos de Exposed

| Sinal | Problema | Solução |
|---|---|---|
| `ResultRow` acessado fora de `transaction {}` | LazyLoading lança exceção | Chamar `.map { it.toDomain() }` dentro do bloco |
| `transaction {}` em coroutine sem dispatcher | Bloqueia thread de coroutine | `withContext(Dispatchers.IO) { transaction { ... } }` |
| `SchemaUtils.create()` em código de produção | Conflito com Flyway | Remover — Flyway gerencia schema |
| Entity DAO com `var` | Mutabilidade no repositório | Usar DSL + `data class` imutável |
| `Exception` capturada em `usecases/` | Infraestrutura vazando | Mover `catch {}` para o repositório |

---

## Relação com os Outros Skills

| Skill | Como se conecta com FP + OO |
|---|---|
| **clean-architecture** | Dependency Rule = DIP de OO; funções puras no núcleo = FP nas camadas internas |
| **screaming-architecture** | Nomes de domínio em `sealed class` gritam o negócio; as somas (`*Error`/`Decision`) ficam junto do agregado |
| **ddd** | Modelar-com-tipos = ADTs: entidades/VOs são produtos, estados/erros são somas — a cardinalidade fecha exatamente o conjunto de estados válidos do domínio |
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