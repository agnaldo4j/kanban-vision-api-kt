---
name: ddd
description: >
  Aplique Domain-Driven Design (DDD) ao projetar, evoluir e comunicar modelos de domínio
  neste projeto. Use este skill ao criar Entities, Value Objects, Aggregates, Domain Services,
  Domain Events, Repositories, ao definir Bounded Contexts, ao fazer EventStorming, ou ao
  avaliar se o modelo está rico ou anêmico. Complementa clean-architecture e screaming-architecture.
argument-hint: "[domain concept or bounded context to model (optional)]"
allowed-tools: Read, Grep, Glob
---

# Domain-Driven Design (DDD)

> *"The heart of software is its ability to solve domain-related problems for its users."*
> — Eric Evans, *Domain-Driven Design: Tackling Complexity in the Heart of Software*, 2003

> *"DDD is about designing software based on models of the underlying domain."*
> — Martin Fowler
> Fontes: https://engsoftmoderna.info/artigos/ddd.html · https://martinfowler.com/bliki/DomainDrivenDesign.html

---

## Por que DDD?

DDD é recomendado quando a **complexidade reside no domínio**, não na infraestrutura:

| Usar DDD | Evitar DDD |
|---|---|
| Lógica de negócio genuinamente complexa | Sistemas predominantemente CRUD |
| Regras intrincadas com muitos casos especiais | Complexidade técnica > complexidade de domínio |
| Colaboração contínua com especialistas de domínio | Equipe sem acesso a domain experts |
| Sistema que evolui acompanhando o negócio | Prazo curto e domínio estável |

---

## I. Design Estratégico

### Ubiquitous Language (Linguagem Ubíqua)

Uma linguagem **rigorosa e compartilhada** entre desenvolvedores e especialistas de domínio, construída sobre o modelo e exercitada em toda comunicação — conversas, documentos e código.

> *"Use the model as the backbone of a language. Commit the team to exercising that language
> relentlessly in all communication within the team and in the code."*
> — Eric Evans

**Princípios:**
- Domain experts objetam termos inadequados
- Developers identificam ambiguidades prejudiciais ao design
- A linguagem evolui continuamente à medida que o entendimento se aprofunda
- O código deve falar a língua do negócio — não de frameworks ou bancos de dados

**Neste projeto — a Ubiquitous Language do Kanban:**

| Termo no negócio | No código |
|---|---|
| Quadro Kanban | `Board` |
| Coluna do quadro | `Column` |
| Cartão / Item de trabalho | `Card`, `WorkItem` |
| Cenário de simulação | `Scenario` |
| Dia de simulação | `SimulationDay` |
| Métricas de fluxo | `FlowMetrics` |
| Limite WIP | `wipLimit` (em `PolicySet`) |
| Capacidade da equipe | `teamSize` |

Se alguém disser *"executar um dia"*, o código tem `RunDayUseCase`, `RunDayCommand`, `SimulationEngine`. A linguagem flui do negócio para o código sem tradução.

---

### Bounded Context (Contexto Delimitado)

> *"Total unification of the domain model for a large system will not be feasible or cost-effective."*
> — Eric Evans

Um **limite explícito** dentro do qual um modelo de domínio específico se aplica e no qual a Ubiquitous Language tem significado preciso. A mudança natural da linguagem entre áreas indica os limites:

> *"You need a different model when the language changes."* — Martin Fowler

**Exemplo clássico (Fowler — empresa de energia):** O conceito *"meter"* tinha três significados distintos em três departamentos. Cada departamento é um Bounded Context separado, com seu próprio modelo e linguagem.

**Neste projeto — Bounded Context atual:**

```
┌─────────────────────────────────────────────────────┐
│  Bounded Context: Kanban Simulation                  │
│                                                      │
│  Board ─── Column ─── Card                          │
│  Tenant ── Scenario ── SimulationState               │
│  WorkItem ─ DailySnapshot ─ FlowMetrics             │
│  SimulationEngine ─ PolicySet ─ Decision            │
└─────────────────────────────────────────────────────┘
```

**Contextos que surgiriam com crescimento do sistema:**

```
[Kanban Simulation] ──ACL──► [Analytics / Relatórios]
[Kanban Simulation] ──ACL──► [Tenant Management / Billing]
[Kanban Simulation] ──ACL──► [Integração Jira/Trello]
```

---

### Context Map

Um documento ou diagrama que torna visível todos os Bounded Contexts e suas relações. Sem ele, integrações ficam implícitas e propensas a erros.

**Padrões de relacionamento:**

| Padrão | Quando usar |
|---|---|
| **Shared Kernel** | Dois contextos compartilham um subconjunto do modelo. Mudanças requerem coordenação |
| **Customer-Supplier** | Upstream define a API; downstream consome. Coordenação via contrato |
| **Conformist** | Downstream aceita o modelo upstream sem adaptação |
| **Anti-Corruption Layer** | Downstream traduz o modelo upstream para proteger seu modelo interno |
| **Open Host Service** | Upstream expõe protocolo público e bem documentado |
| **Published Language** | Protocolo de troca de informações bem documentado entre contextos |
| **Separate Ways** | Contextos sem integração — cada um resolve seus problemas independentemente |

---

### Anti-Corruption Layer (ACL)

Uma camada de **tradução** que isola um Bounded Context dos modelos externos, prevenindo que terminologia e conceitos externos contaminem o modelo interno.

```
Sistema Externo  →  [Fachada → Adaptadores → Serviços]  →  Domínio Interno
     Jira                        ACL                          WorkItem
    "Issue"                  tradução                          "Card"
```

**Neste projeto:** Se integrar com Jira, a ACL garante que `Issue` do Jira não vaza para o domínio como `Issue` — ele é traduzido para `Card` ou `WorkItem`, preservando a Ubiquitous Language interna.

---

## II. Design Tático — Building Blocks

### Entity (Entidade)

Objeto com **identidade distinta que persiste ao longo do tempo**. A identidade — não os atributos — define a igualdade.

**Regra:** Dois objetos com o mesmo ID são a mesma entidade, mesmo que todos os outros atributos difiram.

```kotlin
// Entity: identidade por BoardId, não por atributos
data class Board(
    val id: BoardId,   // ← identidade
    val name: String,  // atributo pode mudar
)

// equals/hashCode baseados APENAS no id
// (Kotlin data class usa todos os campos — prefira implementar equals explicitamente
//  ou usar um id-only equality pattern para Entities)
```

**Entities neste projeto:**

| Entity | Aggregate Root? | Identificador |
|---|---|---|
| `Board` | ✅ | `BoardId` |
| `Column` | ❌ (pertence a Board) | `ColumnId` |
| `Card` | ❌ (pertence a Board) | `CardId` |
| `Tenant` | ✅ | `TenantId` |
| `Scenario` | ✅ | `ScenarioId` |
| `WorkItem` | ❌ (pertence a Scenario) | `WorkItemId` |

---

### Value Object (Objeto de Valor)

> *"Value objects should be immutable."* — Martin Fowler
> Fonte: https://martinfowler.com/bliki/ValueObject.html

Objeto cujo significado é determinado pelo **valor dos atributos**, não por identidade. Dois Value Objects com as mesmas propriedades são iguais.

**Regras fundamentais:**
- Imutável — sem setters, sem mutação in-place
- `equals()` e `hashCode()` baseados nos atributos
- Substituível: ao invés de mutar, crie um novo

**Atenção de Fowler:** A classificação é contextual:
> *"An address can be a Value Object in a simple context, but a Reference Object in a sophisticated cartographic system."*

**Value Objects neste projeto:**

```kotlin
// Wrapper de ID — @JvmInline elimina overhead de objeto em runtime
@JvmInline
value class BoardId(val value: String) {
    companion object { fun generate() = BoardId(UUID.randomUUID().toString()) }
}

// Value Object composto — imutável por design
data class FlowMetrics(
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
)

// Value Object de configuração
data class ScenarioConfig(
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long,
)
```

**Kotlin idioms para Value Objects:**

| Situação | Idiom |
|---|---|
| Wrapper de tipo primitivo (ID, Email, CPF) | `@JvmInline value class` |
| Objeto composto imutável | `data class` com apenas `val` |
| Garantir imutabilidade de coleções | `List<T>` (não `MutableList`) |

---

### Aggregate (Agregado)

> *"A cluster of domain objects that can be treated as a single unit."*
> *"DDD Aggregates are domain concepts (order, clinic visit, playlist), while collections are generic."*
> — Martin Fowler · https://martinfowler.com/bliki/DDD_Aggregate.html

Um cluster de objetos de domínio tratado como **unidade de consistência**. Um dos objetos é a **Aggregate Root** — toda referência externa acessa o aggregate apenas através dela.

**Regras fundamentais:**

1. Toda referência externa ao aggregate vai **apenas para a Aggregate Root**
2. **Transações não cruzam fronteiras de aggregates**
3. Aggregates são a **unidade básica de persistência** — salva e carrega o aggregate inteiro
4. Objetos internos não são referenciados diretamente de fora

```
╔═══════════════════════════╗
║  Aggregate: Scenario      ║
║  Root: Scenario           ║      ← referências externas chegam aqui
║  ├─ ScenarioConfig        ║
║  ├─ SimulationState       ║
║  │  └─ WorkItem[]         ║
║  └─ DailySnapshot[]       ║
╚═══════════════════════════╝
```

**Aggregates neste projeto:**

```kotlin
// Scenario é a Aggregate Root
data class Scenario(
    val id: ScenarioId,          // identidade da raiz
    val tenantId: TenantId,
    val config: ScenarioConfig,  // Value Object filho
)

// SimulationState pertence ao Scenario aggregate
// Não existe repositório para SimulationState isolado
// Acesso sempre via ScenarioRepository.findState(scenarioId)
```

**Erro comum:** Criar repositório para objetos internos do aggregate (ex: `WorkItemRepository`). Objetos internos só existem dentro do aggregate — acesse-os pela raiz.

---

### Repository (Repositório)

Uma abstração que fornece acesso a Aggregates persistidos, ocultando completamente os detalhes de infraestrutura.

**Regra DDD:** Repositórios existem **apenas para Aggregate Roots**. Nunca para objetos internos ao aggregate.

**Convenção deste projeto** (definida em CLAUDE.md):
- Interfaces (ports) em `usecases/repositories/` — parte do domínio de aplicação
- Implementações em `sql_persistence/repositories/` — detalhe de infraestrutura
- O domínio nunca vê JDBC, SQL ou HikariCP

```kotlin
// Port — interface definida em usecases/
interface ScenarioRepository {
    suspend fun findById(id: ScenarioId): Either<DomainError, Scenario?>
    suspend fun save(scenario: Scenario): Either<DomainError, Unit>
    suspend fun findState(id: ScenarioId): Either<DomainError, SimulationState?>
    suspend fun saveState(id: ScenarioId, state: SimulationState): Either<DomainError, Unit>
}

// Adapter — implementação em sql_persistence/
class JdbcScenarioRepository : ScenarioRepository {
    override suspend fun findById(id: ScenarioId): Either<DomainError, Scenario?> {
        // JDBC aqui — nunca no domínio
    }
}
```

---

### Domain Service (Serviço de Domínio)

Operação **stateless** que representa um conceito do domínio mas não se encaixa naturalmente em nenhuma Entity ou Value Object — especialmente quando envolve múltiplos objetos de domínio.

**Quando criar um Domain Service:**
- A operação envolve múltiplas Entities ou Aggregates
- A operação é inerentemente stateless (não "pertence" a nenhum objeto)
- Forçar o comportamento em uma Entity produziria dependências artificiais

**Distinção crítica:** Domain Service expressa lógica de **domínio**. Application Service (Use Case) orquestra fluxo de **aplicação**. Nunca confunda os dois.

```kotlin
// Domain Service — lógica pura de negócio, sem IO
class SimulationEngine {
    fun runDay(state: SimulationState, config: ScenarioConfig): SimulationResult {
        // aplica regras de negócio: WIP limit, decisões, movimentos
        // retorna resultado sem persistência
    }
}

// Application Service (Use Case) — orquestra IO + domain
class RunDayUseCase(
    private val scenarioRepository: ScenarioRepository,
    private val snapshotRepository: SnapshotRepository,
    private val engine: SimulationEngine, // injeta o Domain Service
) {
    suspend fun execute(command: RunDayCommand): Either<DomainError, DailySnapshot> =
        either {
            val scenario = scenarioRepository.findById(command.scenarioId).bind()
            val state = scenarioRepository.findState(command.scenarioId).bind()
            val result = engine.runDay(state, scenario.config) // lógica pura
            snapshotRepository.save(result.snapshot).bind()    // IO
        }
}
```

---

### Domain Event (Evento de Domínio)

Um evento que representa algo **significativo que aconteceu no domínio** — uma mudança de estado relevante para o negócio.

**Características:**
- Nomeado no **passado**: `CardMoved`, `DaySimulated`, `ScenarioCreated`
- **Imutável** após criação
- Carrega todos os dados relevantes sobre o que ocorreu
- Representa um **fato** — algo que já aconteceu e não pode ser desfeito

**Tipos de uso (Fowler):**
- **Event Notification**: Notifica outros contextos (baixo acoplamento)
- **Event-Carried State Transfer**: O evento carrega dados completos (maior resiliência)
- **Event Sourcing**: Eventos como única fonte de verdade do estado

**Exemplo para este projeto:**

```kotlin
// Domain Events que poderiam ser modelados
sealed class KanbanEvent {
    data class ScenarioCreated(
        val scenarioId: ScenarioId,
        val tenantId: TenantId,
        val config: ScenarioConfig,
        val occurredAt: Instant,
    ) : KanbanEvent()

    data class DaySimulated(
        val scenarioId: ScenarioId,
        val day: SimulationDay,
        val snapshot: DailySnapshot,
        val occurredAt: Instant,
    ) : KanbanEvent()

    data class WorkItemCompleted(
        val workItemId: WorkItemId,
        val scenarioId: ScenarioId,
        val cycleTime: Int,
        val occurredAt: Instant,
    ) : KanbanEvent()
}
```

---

### Factory

Responsável pela **criação de objetos de domínio complexos**, encapsulando a lógica de construção e garantindo estado válido.

**Quando usar:** Quando o construtor sozinho não é adequado — envolve geração de IDs, validações cruzadas, ou montagem de objetos aninhados.

```kotlin
// Factory method via companion object — padrão idiomático em Kotlin
data class Scenario(
    val id: ScenarioId,
    val tenantId: TenantId,
    val config: ScenarioConfig,
) {
    companion object {
        fun create(tenantId: TenantId, config: ScenarioConfig): Scenario =
            Scenario(
                id = ScenarioId(UUID.randomUUID().toString()), // gerado pela Factory
                tenantId = tenantId,
                config = config,
            )
    }
}

// O Use Case chama a Factory — não conhece o ID gerado antecipadamente
// (ver CLAUDE.md: "CreateScenarioUseCase generates its own ID via Scenario.create()")
```

---

## III. Antipadrão: Anemic Domain Model

> *"Domain objects with rich structure (meaningful names, relationships) but lacking behavior
> — little more than bags of getters and setters."*
> — Martin Fowler · https://martinfowler.com/bliki/AnemicDomainModel.html

**Por que é problemático:**
1. **Viola OO:** Contradiz a combinação fundamental de dados e comportamento
2. **Custos sem benefícios:** Incorre na complexidade de um Domain Model sem seus benefícios
3. **Efetivamente Transaction Script disfarçado:** Toda a lógica vai para serviços que se tornam procedures

> *"The more behavior in services, the more you starve the domain model.
> If all logic is in services, you've robbed yourself completely."* — Fowler

**Sinal de alerta no código:**

```kotlin
// ❌ Anêmico — objeto sem comportamento, só dados
class Card {
    var columnId: ColumnId = ColumnId("")
    var position: Int = 0
    fun setColumnId(id: ColumnId) { columnId = id }
    fun setPosition(pos: Int) { position = pos }
}

// Em algum serviço:
fun moveCard(card: Card, targetColumn: ColumnId, pos: Int) {
    card.setColumnId(targetColumn)  // lógica fora do objeto
    card.setPosition(pos)
}

// ✅ Rico — objeto com comportamento que expressa o domínio
data class Card(val id: CardId, val columnId: ColumnId, val title: String,
                val description: String, val position: Int) {
    fun moveTo(targetColumn: ColumnId, newPosition: Int): Card =
        copy(columnId = targetColumn, position = newPosition)  // retorna novo estado
}
```

**Checklist — o modelo está rico ou anêmico?**

- [ ] As Entities têm **métodos que expressam intenções de negócio** (`activate()`, `moveCard()`, `runDay()`)?
- [ ] A lógica de negócio **vive nos objetos** que possuem os dados relevantes?
- [ ] Os objetos de domínio **rejeitam estados inválidos** em vez de permitir qualquer setter?
- [ ] O código lê como a linguagem do domínio, não como manipulação de dados?

---

## IV. EventStorming

EventStorming é um workshop colaborativo criado por Alberto Brandolini para **exploração visual de domínios complexos** — a ferramenta de descoberta que alimenta o design DDD.

> *"EventStorming is a flexible workshop format for collaborative exploration of complex business domains."*
> — Alberto Brandolini · https://www.eventstorming.com

### Notação (post-its)

| Elemento | Cor | O que representa |
|---|---|---|
| **Domain Event** | 🟠 Laranja | Algo que aconteceu no domínio (verbo no passado) |
| **Command** | 🔵 Azul | Intenção que dispara um evento |
| **Aggregate** | 🟡 Amarelo | Processa Commands e emite Events |
| **Policy** | 🟣 Lilás | "Quando [evento], então [command]" |
| **Read Model** | 🟢 Verde | Dado que o ator precisa ver para decidir |
| **External System** | 🩷 Rosa | Sistema externo participando do fluxo |
| **Actor** | 🟡 Amarelo claro | Pessoa que executa um Command |
| **Hotspot** | 🔴 Vermelho | Conflito, dúvida ou problema em aberto |
| **Bounded Context** | Agrupamento | Fronteira natural entre subdomínios |

### Os Quatro Estilos

| Estilo | Objetivo |
|---|---|
| **Big Picture** | Exploração macro — fluxos de negócio inteiros, identificação de hotspots |
| **Process Modeling** | Aprofunda fluxos específicos, descobre inconsistências |
| **Software Design** | Estrutura o software com modelos event-driven alinhados com DDD |
| **Value-Stream Mapping** | Examina viabilidade de novos serviços e modelos de negócio |

### Padrões de Facilitação (eventstorming.com/patterns)

- **Chaotic Exploration**: Começar sem estrutura — post-its livres antes de organizar
- **Extract Acceptance Tests**: Capturar critérios de aceite a partir do fluxo estabelecido
- **Raise the Bar**: Usar casos extremos e exceções para desafiar e fortalecer o modelo
- **Speaking Out Loud**: Narrar o fluxo verbalmente — inconsistências ficam imediatamente evidentes

### Mapeamento EventStorming → DDD → Código

```
EventStorming           DDD                  Kotlin
──────────────────────────────────────────────────────────
Domain Event    →  Domain Event       →  sealed class KanbanEvent
Command         →  Use Case Command   →  data class RunDayCommand
Aggregate       →  Aggregate Root     →  data class Scenario
Policy          →  Domain Service     →  fun applyPolicy(event)
Read Model      →  Query DTO          →  data class SimulationDashboard
Bounded Context →  Bounded Context    →  Gradle module / package
External System →  Anti-Corruption Layer →  interface ExternalAdapter
```

### Exemplo: "Executar Dia de Simulação"

```
[Actor: Usuário]
    → [Command: RunDay(scenarioId, dayNumber)]
        → [Aggregate: Scenario]
            → [Domain Event: DaySimulated(scenarioId, day, snapshot)]
                → [Policy: "Quando DaySimulated, verificar metas de fluxo"]
                    → [Command: EvaluateFlowGoals]
                        → [Domain Event: FlowGoalEvaluated]
                            → [Read Model: SimulationDashboard atualizado]

Hotspots identificados:
  ❓ "Quem define as metas de fluxo?" → necessita Domain Expert
  ❓ "O que acontece quando WIP está cheio por N dias?" → regra de negócio a descobrir
```

---

## V. Mapeamento Completo do Projeto

### Bounded Context: Kanban Simulation

```
Entities (identidade por ID)
  Board(BoardId) ────── Aggregate Root de [Column[], Card[]]
  Tenant(TenantId) ──── Aggregate Root isolado
  Scenario(ScenarioId) ─ Aggregate Root de [SimulationState, DailySnapshot[]]
  WorkItem(WorkItemId) ─ interno ao Scenario aggregate

Value Objects (imutáveis, por valor)
  BoardId, CardId, ColumnId   ← @JvmInline value class
  TenantId, ScenarioId, WorkItemId
  FlowMetrics, ScenarioConfig
  SimulationDay, WorkItemState, ServiceClass
  PolicySet, Decision, Movement

Domain Services
  SimulationEngine ── motor de simulação (puro, sem IO)

Repositories (ports em usecases/, adapters em sql_persistence/)
  BoardRepository, CardRepository, ColumnRepository
  TenantRepository, ScenarioRepository, SnapshotRepository

Use Cases / Application Services (CQS pattern)
  Commands: CreateBoardCommand, RunDayCommand, MoveCardCommand, ...
  Queries: GetScenarioQuery, GetDailySnapshotQuery, ...
```

### Gaps DDD a Considerar

| Gap | Conceito DDD | Ação |
|---|---|---|
| Ausência de Domain Events explícitos | Domain Event | Criar `DaySimulated`, `WorkItemCompleted`, `ScenarioCreated` |
| Analytics como feature crescente | Bounded Context | Separar em contexto de Analytics com Read Models próprios |
| Integração futura com ferramentas externas | ACL | Preparar Anti-Corruption Layer para Jira/Trello/etc. |
| Read Models para dashboard | CQRS (query side) | Projeções desnormalizadas para relatórios eficientes |
| Regras de WIP como política | Policy/Domain Service | Modelar `WipPolicy` como objeto explícito de domínio |

---

## VI. Checklist DDD

### Ao modelar uma nova classe de domínio

- [ ] É uma **Entity** (tem identidade)? Tem `Id` como propriedade principal?
- [ ] É um **Value Object** (sem identidade)? É imutável? `equals()` por valor?
- [ ] É um **Aggregate Root**? Tem `Repository` correspondente?
- [ ] Objetos internos ao aggregate são acessados **apenas pela raiz**?
- [ ] O nome usa a **Ubiquitous Language** — termos do negócio, não técnicos?

### Ao criar um novo comportamento

- [ ] O comportamento **pertence ao objeto** que tem os dados (Rich Model)?
- [ ] Se envolve múltiplos objetos → é um **Domain Service**?
- [ ] Se é um fato que ocorreu → modela como **Domain Event**?
- [ ] Se cria um objeto complexo → usa **Factory**?

### Ao avaliar o modelo

- [ ] O código lê como a linguagem do domínio?
- [ ] Objetos rejeitam estados inválidos (invariantes garantidos)?
- [ ] Serviços são **finos** — a lógica vive nos objetos de domínio?
- [ ] Nenhuma terminologia de infraestrutura (SQL, HTTP, JSON) vaza para o domínio?

### Ao crescer o sistema

- [ ] Há necessidade de um novo **Bounded Context** (a linguagem mudou)?
- [ ] As integrações entre contextos têm **Anti-Corruption Layer**?
- [ ] O **Context Map** está atualizado?
- [ ] Fez **EventStorming** antes de modelar features complexas?

---

## VII. Relação com Outras Skills

| Esta skill | Complementa |
|---|---|
| Ubiquitous Language, Entities, Value Objects, Aggregates | `screaming-architecture` — a estrutura grita o domínio DDD |
| Dependency Rule: domínio independente | `clean-architecture` — camadas protegem o modelo de domínio |
| Value Objects imutáveis, Either para erros | `fp-oo-kotlin` — FP reforça imutabilidade e composição segura |
| Rich Domain Model com comportamento | `solid-principles` — SRP, OCP guiam a coesão do modelo |
| Context Map, Bounded Contexts | `c4-model` — diagramas C4 visualizam os contextos |

---

## Referências

- Evans, Eric. *Domain-Driven Design: Tackling Complexity in the Heart of Software*. Addison-Wesley, 2003.
- Fowler, Martin. *DomainDrivenDesign*. https://martinfowler.com/bliki/DomainDrivenDesign.html
- Fowler, Martin. *BoundedContext*. https://martinfowler.com/bliki/BoundedContext.html
- Fowler, Martin. *DDD_Aggregate*. https://martinfowler.com/bliki/DDD_Aggregate.html
- Fowler, Martin. *ValueObject*. https://martinfowler.com/bliki/ValueObject.html
- Fowler, Martin. *AnemicDomainModel*. https://martinfowler.com/bliki/AnemicDomainModel.html
- Fowler, Martin. *CQRS*. https://martinfowler.com/bliki/CQRS.html
- Valente, Marco Tulio. *Domain-Driven Design*. https://engsoftmoderna.info/artigos/ddd.html
- Brandolini, Alberto. *EventStorming*. https://www.eventstorming.com
- Brandolini, Alberto. *EventStorming Patterns*. https://www.eventstorming.com/patterns/
- Brandolini, Alberto. *EventStorming Resources*. https://www.eventstorming.com/resources/