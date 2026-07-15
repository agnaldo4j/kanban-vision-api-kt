---
name: circular-dependency-control
description: >
  Detecte, classifique e elimine dependências circulares em código Kotlin.
  Use este skill ao revisar PRs, ao reestruturar pacotes, ao extrair módulos,
  ou sempre que `import` bidirecionais forem suspeitos. Abrange ciclos em nível
  de classe, pacote e módulo Gradle. Baseado em
  https://en.wikipedia.org/wiki/Circular_dependency e padrões de resolução DIP/Mediator/Events.
argument-hint: "[file, package or module to audit (optional)]"
allowed-tools: Read, Grep, Glob, Bash
---

# Circular Dependency Control — Detectar, Classificar e Eliminar

> "Dois ou mais módulos dependendo um do outro, direta ou indiretamente,
> para funcionar adequadamente. Também chamados de módulos mutuamente recursivos."
> — Circular dependency, Wikipedia

---

## O Que É e Por Que Importa

Uma dependência circular ocorre quando A depende de B e B depende de A — seja
diretamente (`A → B → A`) ou transitivamente (`A → B → C → A`). O ciclo pode
existir em três níveis, cada um com impacto diferente:

| Nível | Exemplo | Impacto |
|---|---|---|
| **Classe** | `Board` importa `Step`; `Step` importa `Board` | Alto acoplamento; inicialização recursiva em DI (StackOverflowError no Koin); testes inviáveis em isolamento |
| **Pacote** | `model/kanban/` importa de `model/` raiz; raiz importa de `kanban/` | Package-level coupling — mudanças em cascata |
| **Módulo Gradle** | `:usecases` depende de `:sql_persistence`; `:sql_persistence` depende de `:usecases` | Build falha — ciclo de compilação inquebrável |

---

## Problemas Causados

### 1. Acoplamento Forte — Módulos Inseparáveis

```
A ←→ B     Remover A exige remover B.
           Testar A em isolamento exige instanciar B.
           Mudar A pode quebrar B de forma inesperada.
```

### 2. Efeito Dominó — Mudanças em Cascata

Uma pequena alteração em `A` força recompilação de `B`, que força `C`, que força
todos os consumidores de `C`. Em projetos grandes isso se traduz em builds lentos
e medo de refatorar.

### 3. Inicialização e Testes Frágeis

```kotlin
// ❌ Ciclo de construção: qual instancia primeiro?
class BoardService(val stepService: StepService)
class StepService(val boardService: BoardService)

// Resultado: StackOverflowError ou NullPointerException na inicialização do Koin
```

### 4. Vazamento de Memória (apenas em GC por contagem de referências)

> **Nota JVM**: este problema **não se aplica ao Kotlin/JVM**. O JVM usa GC por
> tracing (mark-and-sweep, G1, ZGC) que detecta ciclos de referência normalmente.
> O problema é real em runtimes com reference counting — CPython, Swift ARC, Rust
> (sem `Weak<T>`): cada objeto mantém a contagem do outro acima de zero mesmo
> quando ambos são inacessíveis, impedindo a desalocação.

### 5. Testabilidade Quebrada

Cada vez que você precisa de `B` para instanciar `A` e precisa de `A` para
instanciar `B`, o teste unitário se torna um teste de integração involuntário.

---

## Taxonomia de Ciclos — Onde Aparecer

### Tipo 1 — Ciclo Direto entre Classes (Crítico)

```kotlin
// ❌ Board.kt importa Step; Step.kt importa Board
// domain/model/kanban/Board.kt
import com.kanbanvision.domain.model.kanban.Step  // Step usa Board em seu campo board

// domain/model/kanban/Step.kt
import com.kanbanvision.domain.model.kanban.Board // Board usa Step em steps: List<Step>
```

**Solução canônica**: quebrar o ciclo com uma referência indireta (`StepRef`
em vez de `Step`), ou inverter a dependência via interface.

---

### Tipo 2 — Ciclo de Pacote via Extension Functions (Sutil)

O ciclo que o GAP-AE corrigiu neste projeto:

```kotlin
// ❌ ANTES: Refs.kt (root) importava sub-pacotes para definir toRef()
// domain/model/Refs.kt
import com.kanbanvision.domain.model.kanban.Board      // ← root importa sub
import com.kanbanvision.domain.model.kanban.Step       // ← root importa sub

fun Board.toRef(): BoardRef = BoardRef(id = id)        // ← extensão no arquivo errado
fun Step.toRef(): StepRef = StepRef(id = id)

// domain/model/kanban/Board.kt
import com.kanbanvision.domain.model.StepRef           // sub importa root
import com.kanbanvision.domain.model.toRef             // sub importa root ← ciclo!

// Ciclo: root ↔ kanban/  (bidirecional)
```

```kotlin
// ✅ DEPOIS: toRef() como método de instância — root não importa sub-pacotes
// domain/model/Refs.kt  — apenas value types, zero imports de sub-pacotes
data class BoardRef(val id: String) { init { require(id.isNotBlank()) } }
data class StepRef(val id: String)  { init { require(id.isNotBlank()) } }

// domain/model/kanban/Board.kt — method pertence ao dono dos dados
import com.kanbanvision.domain.model.BoardRef  // sub importa root (sentido único ✅)
data class Board(val id: String, val name: String) {
    fun toRef(): BoardRef = BoardRef(id = id)  // método de instância — sem import cruzado
}
```

---

### Tipo 3 — Ciclo Transitivo entre Pacotes (Insidioso)

```
❌ (hipotético) organization/ → simulation/ → organization/

Se Scenario.kt (organization) importasse Decision/DailySnapshot (simulation)
E Simulation.kt (simulation) importasse Scenario (organization) → ciclo bidirecional
```

**Caso RESOLVIDO no projeto.** Este ciclo já existiu (documentado no PR #171) mas foi
desfeito: `decisions` e `history` vivem hoje no agregado `Simulation`
(`Simulation.kt`), e `Scenario` é config pura (`name`, `rules`, `board`) que **não**
importa nenhum tipo de `simulation`. O grafo atual é **one-way `simulation → organization`
(um DAG, sem ciclo)** — a `ContextBoundaryTest` garante que o back-edge `organization → simulation`
não pode reaparecer, e a `PackageCycleTest` (GAP-BN) detecta qualquer ciclo de pacote no grafo
inteiro de produção. Mantém-se o exemplo como padrão a reconhecer, não como débito ativo.

---

### Tipo 4 — Ciclo de Módulo Gradle (Fatal para o Build)

```kotlin
// ❌ settings.gradle.kts / build.gradle.kts
// usecases/build.gradle.kts
implementation(project(":sql_persistence"))  // usecases depende de sql_persistence

// sql_persistence/build.gradle.kts
implementation(project(":usecases"))         // sql_persistence depende de usecases

// Resultado: Circular dependency between :usecases and :sql_persistence
```

Gradle detecta e rejeita este ciclo em tempo de configuração — o build nem começa.

---

## Detecção — Como Encontrar Ciclos

### Inspeção Manual com grep

```bash
# 1. Encontrar todos os imports de um pacote alvo
grep -rn "import com.kanbanvision.domain.model.simulation" \
  domain/src/main/kotlin/com/kanbanvision/domain/model/organization/ \
  --include="*.kt"
# Se retornar resultados + organization/ importa simulation/ E simulation/ importa
# organization/ → ciclo transitivo

# 2. Verificar se arquivos de sub-pacote importam de volta para root do pacote
grep -rn "import com.kanbanvision.domain.model\." \
  domain/src/main/kotlin/com/kanbanvision/domain/model/kanban/ \
  --include="*.kt" | grep -v "\.kanban\." | grep -v "\.organization\." | grep -v "\.simulation\."
# Imports de volta para root (Refs, Audit, Domain, etc.) são aceitáveis
# Imports de root que importam de sub → indica ciclo se root também importar sub

# 3. Verificar extension functions que cruzam fronteiras de pacote
grep -rn "^fun [A-Z].*\." \
  domain/src/main/kotlin/com/kanbanvision/domain/model/ \
  --include="*.kt"
# Extension functions em arquivos fora da classe receptora → candidatas a ciclo

# 4. Detectar imports bidirecionais entre dois pacotes
PKG_A="domain/src/main/kotlin/com/kanbanvision/domain/model/organization"
PKG_B="domain/src/main/kotlin/com/kanbanvision/domain/model/simulation"
echo "=== A importa B ===" && grep -rn "import.*simulation" "$PKG_A" --include="*.kt"
echo "=== B importa A ===" && grep -rn "import.*organization" "$PKG_B" --include="*.kt"
# Se ambos retornam resultados → ciclo bidirecional entre pacotes
```

### Detecção de Ciclos no Nível de Módulo Gradle

```bash
# Gradle detecta automaticamente — verificar na saída do build
./gradlew :domain:compileKotlin 2>&1 | grep -i "circular"

# Listar dependências de um módulo
./gradlew :usecases:dependencies --configuration compileClasspath

# Visualizar grafo completo de dependências
./gradlew :http_api:dependencies | grep -E "project\(|---"
```

### Indicadores de Ciclo em Code Review

| Sinal no PR | Tipo de ciclo suspeito |
|---|---|
| Dois arquivos trocando imports entre si | Ciclo direto de classe |
| Extension function definida fora da classe receptora | Ciclo de pacote via extensão |
| Arquivo de root importando sub-pacote + sub importando root | Ciclo root↔sub |
| Um use case retornando tipo definido em sql_persistence | Ciclo de módulo |
| DomainError definido em `http_api/` e usado em `domain/` | Violação de Dependency Rule + ciclo |

---

## Resolução — Padrões por Tipo de Ciclo

### Padrão 1 — Referência Indireta (Ref Object)

Substituir a dependência bidirecional por uma referência leve que contém apenas
o identificador. O objeto "pesado" (com todos os campos e métodos) não é mais
necessário no outro lado.

```kotlin
// ❌ Ciclo: Board ↔ Step (cada um precisa do tipo completo do outro)
data class Board(val steps: List<Step>)   // Board.kt importa Step
data class Step(val board: Board, ...)    // Step.kt importa Board → ciclo!

// ✅ Step referencia Board pela ref; Board contém Step diretamente (hierarquia clara)
data class BoardRef(val id: String)       // value object leve — sem import de Board
data class Step(val board: BoardRef, ...) // Step.kt importa apenas BoardRef ✅
data class Board(val steps: List<Step>)   // Board.kt importa Step (sentido único) ✅
```

**Quando usar**: quando um lado precisa apenas do identificador do outro
(para associar, não para operar sobre ele).

---

### Padrão 2 — Inversão de Dependência (DIP)

Introduzir uma interface no módulo de nível mais alto; o módulo de nível mais
baixo implementa essa interface. A dependência concreta passa a apontar em
sentido único (de baixo para cima).

```kotlin
// ❌ Ciclo: usecases ↔ sql_persistence
// usecases/RunDayUseCase.kt
import com.kanbanvision.persistence.JdbcSimulationRepository  // UseCase → Persistence

// sql_persistence/JdbcSimulationRepository.kt
import com.kanbanvision.usecases.SimulationState              // Persistence → UseCase

// ✅ DIP: interface definida em usecases; persistence implementa
// usecases/repositories/SimulationRepository.kt
interface SimulationRepository {           // porto pertence ao UseCase (alto nível)
    fun findById(id: String): Simulation?
    fun save(simulation: Simulation)
}

// usecases/RunDayUseCase.kt
class RunDayUseCase(private val repo: SimulationRepository)  // depende da abstração

// sql_persistence/JdbcSimulationRepository.kt
class JdbcSimulationRepository : SimulationRepository { ... } // implementa a abstração
// Fluxo: sql_persistence → usecases (sentido único) ✅
```

---

### Padrão 3 — Extração de Tipo Compartilhado

Quando A e B dependem do mesmo tipo C e isso cria o ciclo, extrair C para um
terceiro módulo/pacote do qual A e B dependem sem se conhecerem.

```kotlin
// ❌ Ciclo: Scenario (organization) ↔ Simulation (simulation)
// Causa: Decision e DailySnapshot vivem em simulation/ mas Scenario os precisa

// ✅ Opção a) Mover o estado de execução para o agregado correto
// Scenario vira configuração pura (sem decisions/history)
data class Scenario(
    override val id: String,
    val name: String,
    val rules: ScenarioRules,
    val board: Board,
    // sem decisions, sem history — esses são estado de execução, não configuração
)

// Simulation é o agregado que centraliza o estado de execução
data class Simulation(
    override val id: String,
    val scenario: Scenario,           // Simulation → Scenario (sentido único) ✅
    val decisions: List<Decision>,    // decisões pertencem à simulação
    val history: List<DailySnapshot>, // histórico pertence à simulação
)
```

---

### Padrão 4 — Mediador (Mediator Pattern)

Quando dois módulos precisam colaborar sem se conhecerem, introduzir um mediador
que conhece ambos e orquestra a interação.

```kotlin
// ❌ Ciclo: BoardService ↔ NotificationService
class BoardService(val notifier: NotificationService) // Board → Notification
class NotificationService(val boardService: BoardService) // Notification → Board

// ✅ Mediador: DomainEventPublisher desacopla produtor de consumidor
interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}

// BoardService publica eventos — não conhece quem consome
class BoardService(val publisher: DomainEventPublisher) {
    fun createBoard(name: String): Board {
        val board = Board.create(name)
        publisher.publish(BoardCreatedEvent(board.id))
        return board
    }
}

// NotificationService reage a eventos — não conhece BoardService
class NotificationService {
    fun onBoardCreated(event: BoardCreatedEvent) { sendNotification(event) }
}

// DI (AppModule) é o único ponto que conhece ambos — orquestra o despacho
class CompositeDomainEventPublisher(
    private val notifier: NotificationService,
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        if (event is BoardCreatedEvent) notifier.onBoardCreated(event)
    }
}
// BoardService e NotificationService não se conhecem ✅
```

---

### Padrão 5 — Mover Extension Function para a Classe Dona

O ciclo de pacote mais comum em Kotlin surge de extension functions definidas
fora do arquivo da classe receptora. A solução é converter a extensão em método
regular de instância.

```kotlin
// ❌ Extension function em arquivo separado cria ciclo de pacote
// model/Refs.kt (root)
import com.kanbanvision.domain.model.kanban.Board   // root importa sub-pacote
fun Board.toRef(): BoardRef = BoardRef(id = id)     // extensão definida fora de Board

// model/kanban/Board.kt (sub-pacote)
import com.kanbanvision.domain.model.toRef          // sub importa root ← ciclo!

// ✅ Método de instância — nenhum import cruzado necessário
// model/kanban/Board.kt
import com.kanbanvision.domain.model.BoardRef       // sub importa root (unidirecional ✅)
data class Board(...) {
    fun toRef(): BoardRef = BoardRef(id = id)       // método pertence à classe dona
}

// model/Refs.kt (root) — apenas value types, sem imports de sub-pacotes
data class BoardRef(val id: String)
```

---

### Padrão 6 — Quebrar com Interface de Callback / Observer

Quando A precisa notificar B de um evento mas B já depende de A:

```kotlin
// ❌ SimulationEngine ↔ MetricsCollector
class SimulationEngine(val metrics: MetricsCollector) // Engine → Metrics
class MetricsCollector(val engine: SimulationEngine)  // Metrics → Engine

// ✅ Engine publica via interface; Metrics implementa sem conhecer Engine
interface SimulationListener {
    fun onDayExecuted(day: Int, result: SimulationResult)
}

object SimulationEngine {
    fun runDay(simulation: Simulation, listeners: List<SimulationListener>): SimulationResult {
        val result = execute(simulation)
        listeners.forEach { it.onDayExecuted(simulation.currentDay.value, result) }
        return result
    }
}

class MetricsCollector : SimulationListener {
    override fun onDayExecuted(day: Int, result: SimulationResult) {
        // registrar métricas sem precisar de referência ao Engine
    }
}
```

---

## Regras para Este Projeto

### Hierarquia de Dependência — Jamais Inverter

```
http_api        → usecases → domain   ← módulos Gradle
sql_persistence → usecases            ← persistence depende dos ports de usecases
sql_persistence → domain              ← persistence depende do domínio

domain/model/                          ← pacotes internos ao domain
    kanban/     → model/ (root)
    organization/ → model/ (root), kanban/
    simulation/  → model/ (root), organization/*

* organization/ ↔ simulation/ é ciclo transitivo CONHECIDO — pendente de resolução
  (Scenario precisa migrar decisions/history para Simulation — gap futuro)
```

### Regras Invioláveis

| Regra | Motivo |
|---|---|
| `domain/` nunca importa de `usecases/`, `sql_persistence/` ou `http_api/` | Dependency Rule — domínio é puro |
| `usecases/` nunca importa de `sql_persistence/` ou `http_api/` | Portas pertencem ao use case, não ao adaptador |
| Extension functions que cruzam fronteiras de pacote → converter em método de instância | Extension functions criam ciclos de pacote invisíveis |
| Value types de referência (`*Ref`) ficam em `domain/model/` (root) sem importar sub-pacotes | Root é folha de dependência para os sub-pacotes |
| Novos tipos compartilhados entre sub-pacotes de `domain/model/` → criar em `model/` root | Evita novos ciclos transitivos |

### ForbiddenImport Detekt — Proteção Automatizada

O projeto já usa a regra `ForbiddenImport` do Detekt para impedir que repositórios
JDBC sejam usados fora do `AppModule`. O mesmo padrão pode ser expandido para
proteger fronteiras de módulo:

```yaml
# config/detekt/detekt.yml (somente via ADR — imutável por política)
style:
  ForbiddenImport:
    active: true
    forbiddenImports:   # schema do Detekt 2.x (dev.detekt) — antes era 'imports:'
      - value: 'com.kanbanvision.persistence.repositories.Jdbc*'
        reason: 'Repositórios JDBC só podem ser usados em wiring de DI (AppModule)'
      # ⚠️ ATENÇÃO: ForbiddenImport bane o padrão em TODO o projeto, não apenas num pacote.
      # Para restrição por camada/pacote, use as fitness functions Konsist do módulo
      # architecture/ (ADR-0026) — é o mecanismo do projeto para regras de dependência
      # entre camadas (ver skill kotlin-quality-pipeline §8).
```

> Para adicionar novas regras ForbiddenImport: abrir ADR. `detekt.yml` é imutável por política.

---

## Processo de Eliminação de um Ciclo

```
1. IDENTIFICAR o ciclo
   grep -rn "import PKG_A" PKG_B/ && grep -rn "import PKG_B" PKG_A/

2. CLASSIFICAR o tipo
   Direto (A ↔ B) | Transitivo (A → B → C → A) | Extension (raiz ↔ sub)

3. ESCOLHER o padrão de resolução
   Ref Object | DIP | Extract Shared Type | Mediator | Move to Instance Method | Callback

4. APLICAR a transformação
   - Uma mudança de cada vez
   - ./gradlew testAll após cada passo

5. VERIFICAR que o ciclo foi eliminado
   grep -rn "import PKG_A" PKG_B/  # deve retornar vazio

6. DOCUMENTAR se ciclo foi mantido como trade-off consciente
   Comentar no PR com: motivo, alternativa rejeitada, gap futuro para resolver
```

---

## Checklist — Code Review com Foco em Ciclos

### Detecção

- [ ] Dois arquivos importam um ao outro diretamente?
- [ ] Um arquivo de sub-pacote importa de volta para o pacote pai E o pacote pai importa do sub?
- [ ] Extension functions estão definidas fora da classe receptora em outro pacote?
- [ ] Um módulo Gradle depende de outro que já o depende (direta ou transitivamente)?
- [ ] Uma interface está definida no módulo que a implementa (em vez do que a usa)?

### Avaliação de Impacto

- [ ] O ciclo é direto (alto acoplamento; risco de inicialização recursiva em DI)?
- [ ] O ciclo é de pacote (mudanças em cascata, coupling alto)?
- [ ] O ciclo é transitivo e documentado como trade-off consciente?
- [ ] O ciclo impede teste unitário de alguma das partes envolvidas?

### Resolução

- [ ] O padrão de resolução escolhido mantém a Dependency Rule intacta?
- [ ] A solução remove apenas o ciclo sem introduzir abstrações desnecessárias?
- [ ] `./gradlew testAll` passa após a resolução?
- [ ] Se o ciclo foi mantido: está documentado no PR com gap futuro registrado?

---

## Anti-Padrões Frequentes em Kotlin

### Anti-Padrão 1 — Extension Function Cruzando Fronteira

```kotlin
// ❌ Tentador mas cria ciclo imediato
// utils/Converters.kt  (pacote utils)
import com.kanbanvision.domain.model.kanban.Board   // utils importa kanban
import com.kanbanvision.domain.model.simulation.Simulation // utils importa simulation

fun Board.toSimulation(): Simulation = ...  // qual pacote "dona" essa função?
```

**Solução**: o comportamento pertence ao tipo que mais muda; mover como método de instância.

---

### Anti-Padrão 2 — Companion Object como Fábrica de Ciclos

```kotlin
// ❌ Companion com factory que depende de outro contexto — cria ciclo se Scenario também importar Simulation
// simulation/Simulation.kt
import com.kanbanvision.domain.model.organization.Scenario // simulation → organization (topo do arquivo)

data class Simulation(...) {
    companion object {
        fun fromScenario(scenario: Scenario): Simulation = Simulation(...)
        // Se Scenario.kt também importar Simulation → ciclo bidirecional organization ↔ simulation
    }
}
```

**Solução**: factory fica no use case que conhece ambos; entidades não se constroem a partir de outras entidades de contextos distintos.

---

### Anti-Padrão 3 — Sealed Class como Hub de Dependências

```kotlin
// ❌ DomainError em domain/ importa tipos de usecases/ para criar variantes ricas
sealed class DomainError {
    data class UseCaseValidation(val command: CreateBoardCommand) : DomainError()
    // domain/ importa CreateBoardCommand de usecases/ → inverte Dependency Rule + cria ciclo
}

// ✅ DomainError em domain/ contém apenas primitivos
sealed class DomainError {
    data class ValidationError(val field: String, val message: String) : DomainError()
    // Sem imports de usecases/ — domain permanece puro
}
```

---

## Referências

- **Circular Dependency — Wikipedia**: https://en.wikipedia.org/wiki/Circular_dependency
- **Mediator Pattern**: https://refactoring.guru/pt-br/design-patterns/mediator
- **Dependency Inversion Principle**: ver skill `/solid-principles` (seção DIP)
- **Dependency Rule**: ver skill `/clean-architecture`
- **Package Intent**: ver skill `/screaming-architecture`
- **ForbiddenImport Detekt**: `config/detekt/detekt.yml` — adicionar via ADR
