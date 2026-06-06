# ADR-0010 — Domain Events para CP5 Feedback Loops

## Cabeçalho

| Campo     | Valor                                                              |
|-----------|--------------------------------------------------------------------|
| Status    | Aceita                                                             |
| Data      | 2026-06-06                                                         |
| Autores   | @agnaldo4j                                                         |
| Branch    | feat/gap-h-domain-events                                           |
| PR        | https://github.com/agnaldo4j/kanban-vision-api-kt/pull/134         |
| Supersede | —                                                                  |

---

## Contexto e Motivação

O domínio possui mutações de estado ricas e bem modeladas — `Board.addStep()`,
`Card.advance()`, `Card.block()`, `Simulation.advanceDay()` — mas nenhum mecanismo
permite que essas mudanças sejam observadas fora do agregado. O resultado é que
toda observabilidade de negócio está ausente ou inacessível:

- `DailySnapshot.movements` captura o que ocorreu dentro de um dia de simulação,
  mas apenas para o contexto de `SimulationEngine`. Movimentos de board (criação,
  adição de step/card) não têm equivalente.
- Métricas Prometheus atualizadas em `http_api/` (`kanban_simulation_days_executed_total`)
  refletem chamadas HTTP, não eventos de domínio. Uma execução via CLI ou teste de
  integração não incrementaria o contador.
- **CP5 — Feedback Loops** (quinta prática core do Kanban) requer que os fluxos de
  trabalho sejam continuamente visíveis: itens completados, itens bloqueados, throughput
  por dia, aging médio. Sem Domain Events, esses dados só existem como campos em
  `DailySnapshot`, inacessíveis para roteamento assíncrono futuro.

Esta ADR é necessária agora porque a implementação do GAP-H (P3, Ciclo Domínio) está
em execução e a decisão de arquitetura — onde os eventos nascem e quem os publica —
impacta diretamente a estrutura de `domain/`, `usecases/` e `http_api/`.

---

## Forças (Decision Drivers)

- [x] **Dependency Rule**: `domain/` permanece zero imports de framework após a entrega
- [x] **Imutabilidade**: agregados continuam retornando novas instâncias — zero mutação interna
- [x] **Testabilidade**: eventos verificáveis em testes unitários sem banco ou broker real
- [x] **Mínima intrusão**: mudança não deve quebrar contratos existentes nem exigir reescrita em cascata
- [x] **CP5 observabilidade imediata**: métricas Prometheus atualizadas por eventos de domínio
- [x] **Extensibilidade para assíncrono**: estrutura permite futura integração com Kafka/RabbitMQ sem
  alterar o domínio (conforme GAP-R considera separação de módulos)
- [x] **JaCoCo ≥ 96% por módulo**, zero violações Detekt/KtLint

---

## Opções Consideradas

- **Opção A**: Eventos gerados nos Use Cases — `DomainEvent` em `domain/`, publicação em `usecases/`
- **Opção B**: Agregados retornam par estado + eventos — `Board.addStep()` retorna `Pair<Board, StepAdded>`
- **Opção C**: Agregado acumula eventos internamente — `pendingEvents: List<DomainEvent>` no `data class`

---

## Decisão

**Escolhemos Opção A** (eventos gerados nos use cases) porque preserva todos os contratos
existentes de agregado sem exigir mudanças em cascata nas assinaturas, alinha-se ao estilo
CQS já estabelecido (use case como orquestrador), e entrega CP5 observabilidade imediata
com o menor risco de regressão. Os eventos de domínio são `data class` puras em `domain/`,
sem dependências de framework. O `EventPublisherPort` fica em `usecases/`, igual aos
repositórios — mesmo padrão já dominado pelo time.

A Opção B seria mais "pura" do ponto de vista DDD, mas mudaria todas as assinaturas de
agregado (impacto alto, valor incremental baixo neste estágio). A Opção C introduz
estado mutável acumulado no `data class`, contrariando o princípio de imutabilidade.

---

## Análise das Opções

### Opção A — Eventos gerados nos Use Cases

Use cases conhecem o contexto suficiente para emitir eventos: eles chamam o agregado,
recebem o novo estado e então constroem e publicam os eventos correspondentes.

```
CreateBoardUseCase.execute(cmd)
  → board = Board.create(name)
  → boardRepository.save(board)
  → publisher.publish(listOf(BoardCreated(board.id, board.name, cmd.organizationId)))
  → Right(board)
```

Para `RunDayUseCase`, os `DailySnapshot.movements` já existem e podem ser mapeados
diretamente para eventos de domínio:

```
MovementType.COMPLETED → CardCompleted(cardId, simulationId, day)
MovementType.BLOCKED   → CardBlocked(cardId, simulationId, day, reason)
MovementType.MOVED     → CardMoved(cardId, simulationId, day)
```

**Prós:**
- Zero mudança nas assinaturas dos agregados — `Board.addStep()` continua retornando `Board`
- Use cases já são os orquestradores — é natural que publiquem eventos
- `DomainEvent` em `domain/` como `sealed class` pura (zero framework)
- `EventPublisherPort` em `usecases/` — mesmo padrão dos repositórios
- Testes unitários de use cases já usam MockK para repositórios; `EventPublisherPort` é mais um mock

**Contras:**
- Eventos não são garantidos se alguém chama o agregado diretamente sem o use case
  (mitigado: agregados são chamados apenas de use cases, via ports-and-adapters)
- Acoplamento semântico: o use case precisa "saber" qual evento corresponde a qual mutação

---

### Opção B — Agregados retornam par estado + eventos

```kotlin
// Assinatura modificada
fun addStep(name: String, requiredAbility: AbilityName): Pair<Board, StepAdded>

// Uso no use case
val (updatedBoard, event) = board.addStep(name, ability)
boardRepository.save(updatedBoard).bind()
publisher.publish(listOf(event))
```

**Prós:**
- Eventos são garantidos pelo tipo — impossível esquecer de publicar
- Autossuficiente: o agregado decide quais eventos decorrem de cada mutação
- Mais alinhado com DDD tático clássico

**Contras:**
- Quebra todas as assinaturas existentes — `Board.addStep()`, `Board.addCard()`,
  `Card.advance()`, `Card.block()`, `Simulation.advanceDay()` — alto impacto de reescrita
- `SimulationEngine.runDay()` já retorna `SimulationResult`; seria `SimulationResult` + eventos
  ou um tipo envelope, criando inconsistência
- Testes existentes de `Board` e `Card` todos falhariam e precisariam de atualização
- Risco de regressão alto; valor incremental baixo em relação à Opção A neste estágio

---

### Opção C — Agregado acumula eventos internamente

```kotlin
data class Board(
    val id: String,
    val name: String,
    val steps: List<Step> = emptyList(),
    val pendingEvents: List<DomainEvent> = emptyList(),  // ← novo campo
    ...
)

fun addStep(...): Board {
    val newStep = ...
    val event = StepAdded(id, newStep.id, newStep.name)
    return copy(steps = steps + newStep, pendingEvents = pendingEvents + event)
}
```

**Prós:**
- Eventos ficam junto ao agregado — clara localidade

**Contras:**
- `pendingEvents` é estado acidental no `data class` — não é estado de domínio,
  é infraestrutura de comunicação; viola SRP
- Serialização do agregado (para persistência) carregaria eventos pendentes por engano
- `equals()` e `hashCode()` do `data class` incluem `pendingEvents` — testes quebram
  se compararem `Board` antes e depois sem drenar eventos
- Conceptualmente errado: imutabilidade significa que cada instância é um snapshot,
  não um buffer de mensagens

---

## Consequências

**Positivas:**
- CP5 feedback loops implementados via Prometheus (Micrometer `Counter`) atualizado
  por cada evento: `kanban.board.created`, `kanban.card.completed`, `kanban.card.blocked`,
  `kanban.simulation.day.executed` — visibilidade imediata no Grafana existente
- Eventos estruturados em JSON log (já temos `logstash-logback-encoder`) permitem
  rastrear o ciclo de vida de cada card e board por correlationId
- `EventPublisherPort` como abstração permite trocar a implementação (`InMemory` → Kafka)
  sem alterar nenhum use case
- Extensibilidade para webhooks, projections (read models) e auditoria futura

**Negativas / Trade-offs:**
- Uso cases ficam ligeiramente mais longos (chamam publisher após persistir) — mitigado
  pelo fato de que a lógica é mecânica e testável via mock
- Não há garantia de tipos de que um use case *esqueceu* de publicar um evento —
  mitigado por testes unitários que verificam que `publisher.publish()` foi chamado
  com os eventos esperados

**Neutras:**
- `Movement` em `DailySnapshot` continua existindo como registro de auditoria interno
  da simulação; não é eliminado — os `DomainEvent` de simulação são derivados dos
  `Movement`, não os substituem

---

## Estrutura de Implementação

### 1. Hierarquia de eventos (`domain/`)

```kotlin
// domain/src/main/kotlin/com/kanbanvision/domain/events/DomainEvent.kt
package com.kanbanvision.domain.events

import java.time.Instant

sealed class DomainEvent {
    abstract val occurredAt: Instant

    // ── Board ────────────────────────────────────────────────────────────────
    data class BoardCreated(
        val boardId: String,
        val boardName: String,
        val organizationId: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class StepAdded(
        val boardId: String,
        val stepId: String,
        val stepName: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class CardAdded(
        val boardId: String,
        val stepId: String,
        val cardId: String,
        val cardTitle: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    // ── Simulation ───────────────────────────────────────────────────────────
    data class SimulationCreated(
        val simulationId: String,
        val simulationName: String,
        val organizationId: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class SimulationDayExecuted(
        val simulationId: String,
        val day: Int,
        val throughput: Int,
        val wipCount: Int,
        val blockedCount: Int,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class CardMoved(
        val simulationId: String,
        val cardId: String,
        val day: Int,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class CardBlocked(
        val simulationId: String,
        val cardId: String,
        val day: Int,
        val reason: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class CardCompleted(
        val simulationId: String,
        val cardId: String,
        val day: Int,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()
}
```

### 2. Port de publicação (`usecases/`)

```kotlin
// usecases/src/main/kotlin/com/kanbanvision/usecases/ports/EventPublisherPort.kt
package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.events.DomainEvent

interface EventPublisherPort {
    fun publish(events: List<DomainEvent>)
}
```

### 3. Adaptador de métricas (`http_api/`)

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/events/MicrometerEventPublisher.kt
package com.kanbanvision.httpapi.events

import com.kanbanvision.domain.events.DomainEvent
import com.kanbanvision.usecases.ports.EventPublisherPort
import io.micrometer.core.instrument.MeterRegistry

class MicrometerEventPublisher(private val registry: MeterRegistry) : EventPublisherPort {
    override fun publish(events: List<DomainEvent>) {
        events.forEach { event ->
            when (event) {
                is DomainEvent.BoardCreated ->
                    registry.counter("kanban.board.created").increment()
                is DomainEvent.StepAdded ->
                    registry.counter("kanban.step.added").increment()
                is DomainEvent.CardAdded ->
                    registry.counter("kanban.card.added").increment()
                is DomainEvent.SimulationCreated ->
                    registry.counter("kanban.simulation.created").increment()
                is DomainEvent.SimulationDayExecuted ->
                    registry.counter("kanban.simulation.day.executed").increment()
                is DomainEvent.CardMoved ->
                    registry.counter("kanban.card.moved").increment()
                is DomainEvent.CardBlocked ->
                    registry.counter("kanban.card.blocked").increment()
                is DomainEvent.CardCompleted ->
                    registry.counter("kanban.card.completed").increment()
            }
        }
    }
}
```

### 4. Padrão de uso nos use cases

```kotlin
// Exemplo: CreateBoardUseCase (após persistir)
val board = Board.create(command.name)
boardRepository.save(board, command.organizationId).bind()
publisher.publish(listOf(
    DomainEvent.BoardCreated(board.id, board.name, command.organizationId.value)
))

// Exemplo: RunDayUseCase (após persistir)
val events = snapshot.movements.map { movement ->
    when (movement.type) {
        MovementType.COMPLETED -> DomainEvent.CardCompleted(simulation.id, movement.cardId, snapshot.day.value)
        MovementType.BLOCKED   -> DomainEvent.CardBlocked(simulation.id, movement.cardId, snapshot.day.value, movement.reason)
        MovementType.MOVED, MovementType.UNBLOCKED -> DomainEvent.CardMoved(simulation.id, movement.cardId, snapshot.day.value)
    }
} + DomainEvent.SimulationDayExecuted(
    simulation.id, snapshot.day.value,
    snapshot.metrics.throughput, snapshot.metrics.wipCount, snapshot.metrics.blockedCount
)
publisher.publish(events)
```

---

## Plano de Implementação

- [ ] **1.** Criar `domain/src/main/kotlin/com/kanbanvision/domain/events/DomainEvent.kt`
       com a hierarquia `sealed class DomainEvent` conforme estrutura acima
- [ ] **2.** Criar `usecases/src/main/kotlin/com/kanbanvision/usecases/ports/EventPublisherPort.kt`
- [ ] **3.** Criar `http_api/src/main/kotlin/com/kanbanvision/httpapi/events/MicrometerEventPublisher.kt`
       implementando `EventPublisherPort` com `MeterRegistry`
- [ ] **4.** Registrar `MicrometerEventPublisher` no `AppModule` (Koin) como singleton
- [ ] **5.** Injetar `EventPublisherPort` nos use cases afetados e chamar `publish()` após cada persistência:
       `CreateBoardUseCase`, `AddStepUseCase`, `AddCardUseCase`, `CreateSimulationUseCase`, `RunDayUseCase`
- [ ] **6.** Escrever testes unitários para `DomainEvent` (construção, campos obrigatórios)
- [ ] **7.** Escrever testes unitários para cada use case verificando que `publisher.publish()` é chamado
       com os eventos esperados (MockK `verify { publisher.publish(listOf(...)) }`)
- [ ] **8.** Escrever teste unitário para `MicrometerEventPublisher` com `SimpleMeterRegistry`
- [ ] **9.** Executar `./gradlew testAll` — Detekt + KtLint + testes + JaCoCo ≥ 96% verde
- [ ] **10.** Preencher campo PR na tabela de cabeçalho desta ADR e atualizar status para `Aceita`

---

## Garantias de Qualidade

### DOD — Definition of Done

- [ ] **1. Contrato e Rastreabilidade**: GAP-H ↔ `feat/gap-h-domain-events` ↔ PR ↔ build rastreáveis
- [ ] **2. Testes Técnicos**: testes unitários para `DomainEvent`, `EventPublisherPort` (mock),
  `MicrometerEventPublisher` (SimpleMeterRegistry), e verificação em cada use case
- [ ] **3. Versionamento e Compatibilidade**: nenhum contrato de API HTTP alterado; nenhum schema de DB alterado
- [ ] **4. Segurança e Compliance**: `DomainEvent` não carrega dados sensíveis (sem PII, sem tokens)
- [ ] **5. CI/CD**: `./gradlew testAll` verde no CI; sem testes flaky
- [ ] **6. Observabilidade**: novos contadores Prometheus documentados em `docs/metrics.md`
  (ou equivalente); visíveis no dashboard Grafana existente
- [ ] **7. Performance e Confiabilidade**: `publish()` é síncrono e in-process — sem latência
  adicional mensurável na rota HTTP
- [ ] **8. Deploy Seguro**: nenhuma migration de banco; rollback = reverter PR
- [ ] **9. Documentação**: `CLAUDE.md` e `stack.md` sem impacto; README sem impacto

### Qualidade de Código

| Ferramenta | Requisito | Ação se falhar |
|---|---|---|
| Detekt | zero violações | Refatorar — nunca suprimir sem justificativa |
| KtLint | zero erros | `./gradlew ktlintFormat` antes do commit |
| JaCoCo | ≥ 96% por módulo | Escrever o teste faltante |

### Aderência à Arquitetura

- [ ] **Dependency Rule**: `domain/events/` sem imports de framework; `usecases/ports/` sem imports de Ktor/Koin
- [ ] **Ports-and-Adapters**: `EventPublisherPort` em `usecases/ports/`, implementação em `http_api/events/`
- [ ] **CQS**: `publish()` é um efeito colateral — não retorna valor; não modifica estado de domínio
- [ ] **Domain puro**: `DomainEvent` usa apenas `java.time.Instant` — zero imports de framework
- [ ] **ForbiddenImport Detekt**: se necessário, adicionar `MicrometerEventPublisher` à lista de
  imports permitidos apenas em `AppModule` (seguindo o padrão dos repositórios JDBC)

---

## Referências

- ADR-0004 — Avaliação de qualidade e gaps priorizados (GAP-H origem)
- ADR-0007 — Métricas Micrometer/Prometheus (base para `MicrometerEventPublisher`)
- ADR-0009 — OpenTelemetry Agent (contexto de observabilidade)
- *Kanban From the Inside* — Mike Burrows (CP5: Feedback Loops)
- *Implementing Domain-Driven Design* — Vaughn Vernon (Cap. 8: Domain Events)
- Skill: [ddd](../.claude/skills/ddd/SKILL.md)
- Skill: [clean-architecture](../.claude/skills/clean-architecture/SKILL.md)
