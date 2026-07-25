---
name: design-patterns
description: >
  Catálogo de padrões de projeto (GoF: criacionais, estruturais, comportamentais)
  visto pela lente Kotlin + FP/OO deste projeto. Use este skill ao decidir como
  estruturar uma colaboração entre objetos, ao nomear uma solução recorrente num
  PR/ADR, ou ao avaliar se um padrão clássico se aplica — ou se dissolve num idioma
  Kotlin (função, `sealed`, `object`, `copy()`). Mostra quais padrões o código já
  realiza (Strategy/DIP via ports, Factory via smart constructors, Decorator no
  circuit breaker, Observer no event publisher, Command no `Decision`) e quais
  evitar (herança de implementação). Baseado em https://refactoring.guru/design-patterns
argument-hint: "[padrão, colaboração ou arquivo a avaliar (opcional)]"
allowed-tools: Read, Grep, Glob
---

# Design Patterns — Padrões de Projeto pela Lente Kotlin + FP/OO

> "Um padrão de projeto é uma solução típica para um problema comum de design.
> Cada padrão é como uma planta que você personaliza para resolver um problema
> particular no seu código."
> — Fonte: https://refactoring.guru/design-patterns

Os padrões de projeto (GoF) descrevem colaborações recorrentes entre objetos. Mas eles nasceram num mundo
**OO com herança e mutabilidade** (C++/Java anos 90). Em **Kotlin** — com funções de primeira classe,
`data class` imutável, `sealed`, `object`, `when` exaustivo e Arrow `Either` — **muitos padrões viram uma
linha de código idiomático** (Strategy = uma função; Singleton = `object`; Command = um `sealed`). Este skill
é o mapa: **o problema que cada padrão resolve continua real**; a *forma* muda.

> Complementa `/refactoring` (smells + técnicas), `/solid-principles` (o *porquê* — DIP/OCP/SRP),
> `/fp-oo-kotlin` (a tese ortogonal FP+OO), `/clean-architecture` e `/ddd` (onde cada objeto mora).
> `/refactoring` cobre o catálogo **de refactoring** da mesma fonte; este cobre o catálogo **de padrões**.

---

## A regra do projeto: prefira o idioma antes do padrão

Antes de "aplicar um padrão", pergunte se a linguagem já resolve. A hierarquia deste projeto:

1. **Uma função pura ou `val: (A) -> B`** resolve? → é o seu Strategy/Command/Template Method. Pare aqui.
2. **Um `sealed` + `when` exaustivo** modela o conjunto fechado de casos? → é o seu State/Visitor/Command.
3. **Uma `interface` (port) + `data class` imutável** desacopla? → é o seu Strategy/Adapter/Bridge (DIP).
4. **Só então** um padrão "clássico com classes" — e, mesmo assim, **por composição, nunca por herança de
   implementação** (`domain-*` é `data class`/`sealed`/`object`, sem `open class` de domínio).

> **Por que não herança?** A regra de imutabilidade + pureza do domínio (`/fp-oo-kotlin`) e
> *composition-over-inheritance* (`/solid-principles`) tornam a maioria dos padrões GoF baseados em subclasse
> (Template Method, Decorator-por-herança, Factory Method-por-subclasse) um **anti-idioma** aqui. A forma
> Kotlin — HOF, `sealed`, delegação por composição — entrega o mesmo desacoplamento sem a árvore de tipos.

---

## Padrões Criacionais

> https://refactoring.guru/design-patterns/creational-patterns — *como* objetos são criados, escondendo o
> `new`/construtor e garantindo invariantes na origem.

| Padrão | No projeto | Forma idiomática |
|---|---|---|
| **Factory Method / Simple Factory** | ✅ **realizado** | `companion object { fun create(...) }` — `Board.create`, `Card.create`, `Simulation.create` (`Simulation.kt:20-35`), `Scenario.create`, `ScenarioRules.create` (7 factories no domínio). É uma **factory de conveniência**: monta o `UUID`/defaults e devolve já-válido, mas **não esconde** o construtor primário (a `data class` é pública → o construtor e o `copy()` seguem acessíveis, ex.: hidratação na persistência). Encapsula a *montagem*, não a *impossibilidade de burlar*. |
| **Smart constructor (invariante imposta pelo tipo)** | ✅ **realizado** | `@JvmInline value class NonBlankName(val value)` com `init { require(...) }` — **este** sim é o smart constructor que *impõe* a invariante: **todo** caminho de construção (inclusive `copy()`) passa pelo `init`, então um nome em branco é irrepresentável (GAP-DH). É a diferença de garantia frente à factory de conveniência acima. |
| **Builder** | ⚠️ dissolve | `data class` + **named/default args** + `copy()` já é o Builder do Kotlin. Não escreva builder-com-setters (viola imutabilidade). |
| **Prototype** | ⚠️ dissolve | `data class.copy()` **é** clonagem prototípica — `card.advance()` = `copy(state = ...)`. |
| **Singleton** | ✅ **realizado** | `object` — `object SimulationEngine` (`SimulationEngine.kt:22`), `object DatabaseFactory`, `object DbCircuitBreaker`. Thread-safe por construção; sem *double-checked locking*. **Só para infra/stateless-de-domínio** — nunca para guardar estado mutável de aplicação. |
| **Abstract Factory** | 🔷 via DI | Não há família de produtos; o papel de "montar a família de implementações" é do **Koin `AppModule`** (o único seam que liga ports a adapters). |

```kotlin
// Factory Method de conveniência no companion (Simulation.kt) — monta UUID/defaults, devolve já-válido.
// NÃO esconde o construtor: a data class é pública (o `copy()` e o construtor primário seguem acessíveis,
// ex.: hidratação na persistência). A invariante "nome não-branco" é imposta pelo TIPO do campo, não aqui.
data class Simulation(val name: NonBlankName, /* … demais campos val … */) : Domain<SimulationId> {
    companion object {
        fun create(name: String, organization: Organization, scenario: Scenario, /* … */): Simulation =
            Simulation(name = NonBlankName(name) /* ← o guard real: value class */, /* … */)
    }
}
// A garantia forte mora no value class NonBlankName (init { require(...) }) — todo caminho de construção
// passa por ele. A factory apenas conveniência; quem torna o estado ilegal irrepresentável é o tipo.
```

---

## Padrões Estruturais

> https://refactoring.guru/design-patterns/structural-patterns — *como* objetos se compõem em estruturas
> maiores mantendo tudo flexível.

| Padrão | No projeto | Forma idiomática |
|---|---|---|
| **Adapter** | ✅ **realizado** | Ports-and-adapters: `DefaultSimulationEngine : SimulationEnginePort` (`DefaultSimulationEngine.kt:9`), `MicrometerEventPublisher : EventPublisherPort` (`MicrometerEventPublisher.kt:9`), os repositórios em `sql_persistence/internal/repositories` adaptam Exposed → porta de `usecases`. O adapter converte o mundo externo (Micrometer, JDBC) no contrato do domínio. |
| **Decorator** | ✅ **realizado** | `CircuitBreakerDataSource(delegate: DataSource, circuitBreaker: CircuitBreaker) : DataSource by delegate` (`CircuitBreakerDataSource.kt:19-42`) **decora** um `DataSource`: sobrescreve **ambas** as sobrecargas de `getConnection` para rejeitar o checkout com o circuito aberto (`rejectWhenOpen()` privado) e delega todo o resto via `by delegate`. Composição pura, zero herança. É o Decorator canônico do repo. |
| **Facade** | ✅ **realizado** | Cada **use case** (`RunDayUseCase`, `CreateSimulationUseCase`) é uma fachada: esconde a orquestração de repositório + engine + publisher atrás de uma operação única (CQS). |
| **Composite** | ✅ **realizado** (dados) | A árvore `Board → Step → Card` e `Organization → Tribe → Squad` é uma composição hierárquica navegada por transformações puras (`Board.withCards`, `SimulationEngine.kt:255`). |
| **Proxy** | 🔷 parcial | O circuit breaker também tem sabor de *protection proxy* (controla acesso ao recurso). Um proxy "puro" não é necessário — a resiliência mora no Decorator acima. |
| **Bridge** | 🔷 via ports | Separar abstração (use case) de implementação (adapter) que varia independentemente = o eixo port↔adapter. Não precisa de forma extra. |
| **Flyweight** | ⚪ não aplicável | `value class` já elimina alocação de wrappers; sem pressão de memória que justifique pool de instâncias. |

```kotlin
// Decorator real (CircuitBreakerDataSource.kt) — decora DataSource por COMPOSIÇÃO, não por herança de impl
class CircuitBreakerDataSource(
    private val delegate: DataSource,            // ← o componente decorado
    private val circuitBreaker: CircuitBreaker,  // resilience4j (não o object DbCircuitBreaker)
) : DataSource by delegate {                     // Kotlin: delegação de interface = boilerplate zero
    override fun getConnection(): Connection {
        rejectWhenOpen()                         // comportamento adicionado
        return delegate.connection               // resto delegado
    }

    override fun getConnection(username: String?, password: String?): Connection {
        rejectWhenOpen()                         // a MESMA guarda na sobrecarga credenciada
        return delegate.getConnection(username, password)
    }

    private fun rejectWhenOpen() {               // lê circuitBreaker.state; lança se OPEN/FORCED_OPEN
        val state = circuitBreaker.state
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            throw CallNotPermittedException.createCallNotPermittedException(circuitBreaker)
        }
    }
}
```

> **Kotlin dá Decorator/Proxy quase de graça** com **delegação de interface** (`class X : Iface by delegate`):
> você sobrescreve só o método que muda e o compilador delega o resto. Prefira sempre a `by`-delegation a
> reescrever N métodos passa-adiante.

---

## Padrões Comportamentais

> https://refactoring.guru/design-patterns/behavioral-patterns — *como* objetos se comunicam e distribuem
> responsabilidade. É aqui que Kotlin/FP mais "dissolve" padrões.

| Padrão | No projeto | Forma idiomática |
|---|---|---|
| **Strategy** | ✅ **realizado** (3 formas) | (a) **Port**: `SimulationEnginePort` troca a estratégia de simulação (`DefaultSimulationEngine` vs. mock). (b) **Enum-carrega-comportamento**: `ServiceClass` guarda `schedulingRank`/`shuffleWithinTier`, e `orderTodoByPriority` (`SimulationEngine.kt:239-253`) ordena genericamente — a política de agendamento mora no enum, não num `when` espalhado. (c) **Função-valor**: `val validate: (String) -> Boolean`. |
| **Command** | ✅ **realizado** | `sealed interface Decision` (`Decision.kt:7`: `MoveItem`/`BlockItem`/`UnblockItem`/`AddItem`) — cada comando é um objeto-dado imutável que o engine interpreta. E os **Commands de CQS** (`RunDayCommand`, `CreateSimulationCommand`) reificam a intenção do caller. |
| **Observer / Pub-Sub** | ✅ **realizado** | `EventPublisherPort` (`EventPublisherPort.kt:5`) + `DomainEvent` + `MicrometerEventPublisher`: o use case publica eventos de domínio; o observador (métricas) reage sem o domínio conhecê-lo. DIP + eventos. |
| **State** | ✅ **realizado** | `CardState` (enum) + transições como métodos do agregado, máquina de estados **imutável** (sem objetos-estado mutáveis). Duas formas convivem: `Card.advance()` (`Card.kt:70`) é uma transição **total** — `when(state)` cobre todo caso e devolve `copy(state = ...)`; `Card.block()` (`Card.kt:79`) é uma transição **parcial com falha tipada** — `ensure(state == IN_PROGRESS) { KanbanError.CardNotInProgress }` e devolve `Either<KanbanError, Card>` (ADR-0044). Regra do projeto: transição total → `copy()` direto; transição que pode ser inválida por regra → `Either`/`raise`. |
| **Template Method** | ⚠️ vira HOF | O "esqueleto com passos variáveis" é uma **função de alta ordem**: `dbQuery { … }` (`either { catch { transaction { } } }`) fixa o esqueleto (transação + captura de erro) e recebe o passo variável como lambda. Sem `abstract class` + `override`. |
| **Iterator** | ⚠️ dissolve | `Sequence`/`Iterable` + `map`/`filter`/`fold` da stdlib. Nunca escreva `Iterator` à mão. |
| **Visitor** | ⚠️ vira `when` | `sealed` + `when` exaustivo externo dá *double-dispatch* sem a cerimônia `accept(visitor)`. O compilador força a exaustividade (novo caso = erro de compilação). |
| **Chain of Responsibility** | ✅ **realizado** (infra) | O **pipeline de plugins do Ktor** (Observability → Auth → RateLimit → Routing) é uma cadeia de interceptors. |
| **Mediator** | 🔷 referência | Ver `/circular-dependency-control` (link `refactoring.guru/design-patterns/mediator`): mediador para quebrar acoplamento N↔N quando um ciclo aparecer. Não usado hoje. |
| **Memento** | ⚠️ dissolve | `DailySnapshot` guarda o estado do dia; capturar/restaurar é `copy()` de `data class` imutável. |
| **Chain / Interpreter / Observer** clássicos com mutação | ❌ evite | A versão com estado mutável e callbacks registrados em `MutableList` conflita com a pureza — use eventos tipados + ports. |

```kotlin
// Strategy idiomático: o enum CARREGA a estratégia; o algoritmo é genérico (SimulationEngine.kt:239-253)
enum class ServiceClass(val schedulingRank: Int, val shuffleWithinTier: Boolean) {
    EXPEDITE(0, false), FIXED_DATE(1, false), STANDARD(2, true), INTANGIBLE(3, true);
}
private fun orderTodoByPriority(cards: List<Card>, rng: Random): List<Int> =
    ServiceClass.entries.sortedBy { it.schedulingRank }.flatMap { sc ->
        val tier = /* índices TODO desse tier */ emptyList<Int>()
        if (sc.shuffleWithinTier) tier.shuffled(rng) else tier
    }
// Adicionar uma nova classe de serviço = uma linha no enum; o algoritmo não muda (OCP via soma fechada).

// Command idiomático: cada comando é um dado imutável de um `sealed` (Decision.kt)
sealed interface Decision {
    data class MoveItem(val cardId: CardId) : Decision
    data class AddItem(val title: NonBlankTitle, val serviceClass: ServiceClass) : Decision
}
// O engine "executa" via `when (decision)` exaustivo — sem objeto-comando com `execute()` mutável.
```

---

## Padrões que dissolvem em Kotlin/FP — resumo

| Padrão GoF | Forma clássica (Java) | Idioma Kotlin/FP neste projeto |
|---|---|---|
| Strategy | interface + N classes | `val: (A)->B` · enum-com-comportamento · port |
| Command | interface `execute()` | `sealed interface` de dados + `when` |
| Singleton | classe + `getInstance()` | `object` |
| Factory Method | subclasse que instancia | `companion.create()` (smart constructor) |
| Builder | classe com setters | named/default args + `copy()` |
| Prototype | `clone()` | `data class.copy()` |
| Template Method | `abstract` + `override` | HOF recebendo o passo como lambda |
| Iterator | classe `Iterator` | `Sequence`/`Iterable` + stdlib |
| Visitor | `accept(visitor)` double-dispatch | `sealed` + `when` exaustivo |
| Observer | listeners registrados (mutável) | `EventPublisherPort` + `DomainEvent` tipado |
| State | objetos-estado mutáveis | enum/`sealed` + método que devolve `copy()` |
| Memento | objeto memento + caretaker | `data class` imutável + `copy()` (`DailySnapshot`) |

> Isto **não** significa "padrões são inúteis". Significa: **nomeie a intenção pelo padrão** (comunica o
> design num PR/ADR), mas **realize-a pelo idioma**. "Isto é um Strategy" é uma boa frase de review; escrever
> uma hierarquia de classes para consegui-lo, aqui, seria over-engineering.

---

## Padrões a usar com cuidado (o custo real)

| Padrão | Risco no projeto | Alternativa |
|---|---|---|
| **Template Method / Factory Method por subclasse** | exige `open class` + herança de implementação → quebra imutabilidade e a pureza de `domain-*`; `DomainPurityTest`/composição-sobre-herança reprovam | HOF, `sealed`, delegação por composição |
| **Decorator por herança** | subclassear para "adicionar comportamento" acopla à impl-base | **delegação de interface** (`by delegate`) — como `CircuitBreakerDataSource` |
| **Observer com estado mutável** | listeners num `MutableList` + callbacks = efeitos ocultos, race conditions | eventos tipados publicados por port (fronteira de efeito explícita) |
| **Singleton com estado de aplicação** | `object` guardando estado mutável = estado global escondido, não-testável | `object` só para stateless/infra; estado vive no agregado imutável passado adiante |
| **Abstract Factory "manual"** | duplica o papel do DI | deixe o Koin `AppModule` montar a família de adapters |

---

## Quando usar este skill

- Ao **decidir a forma** de uma colaboração nova ("preciso variar o algoritmo de X") — cheque a hierarquia da
  seção *"prefira o idioma"* antes de introduzir classes.
- Ao **nomear** uma solução num PR/ADR ("isto é um Decorator sobre o `DataSource`") — comunica intenção.
- Ao **revisar** um PR que introduz uma hierarquia de classes — pergunte se um `sealed`/HOF/port não entrega
  o mesmo com menos tipos.
- Ao **avaliar** se o código já *tem* um padrão (para documentar/reforçar), como o inventário acima.

## Relação com outros skills

| Este skill | Complementa |
|---|---|
| *Qual* colaboração e *que forma* Kotlin ela toma | `/solid-principles` — o *porquê* (DIP habilita Strategy/Adapter; OCP vem da soma fechada) |
| Padrões vs. idiomas FP/OO | `/fp-oo-kotlin` — a tese ortogonal, `sealed`/`Either`/value class, tell-don't-ask |
| Padrões vs. smells | `/refactoring` — o catálogo de *refactoring* da mesma fonte; muitos refactorings *introduzem* um padrão |
| Onde cada objeto/porta mora | `/clean-architecture`, `/ddd`, `/screaming-architecture` |
| Mediator para quebrar ciclos | `/circular-dependency-control` |

## Checklist rápido

- [ ] Antes de criar classes: uma **função**, um **`sealed`+`when`**, ou um **port** já resolve?
- [ ] O padrão escolhido é realizado por **composição/delegação** (`by`), nunca por herança de implementação no domínio?
- [ ] `object` só guarda comportamento **stateless**/infra — não estado mutável de aplicação?
- [ ] Uma factory nova é um **smart constructor** que devolve estado já-válido (invariante na origem)?
- [ ] Um comando/estado novo entra como **caso de um `sealed`** (o compilador força a exaustividade)?
- [ ] O nome do padrão aparece no PR/ADR para **comunicar a intenção**, sem virar cerimônia de tipos?
- [ ] Estado é **imutável** — transições devolvem `copy()`, não mutam in-place?

## Referências

- Catálogo: https://refactoring.guru/design-patterns (Criacionais · Estruturais · Comportamentais)
- Fonte da tese FP+OO: `/fp-oo-kotlin` · Uncle Bob, *FP vs OO* (paradigmas ortogonais)
- Realizações no código: `SimulationEngine.kt`, `CircuitBreakerDataSource.kt`, `EventPublisherPort.kt`,
  `Decision.kt`, `Simulation.kt`, `DefaultSimulationEngine.kt`
