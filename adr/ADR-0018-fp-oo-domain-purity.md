# ADR-0018 — FP+OO Domain Purity: Decisão Sealed, Funções Puras e Engine Imutável

## Cabeçalho

| Campo     | Valor                                                            |
|-----------|------------------------------------------------------------------|
| Status    | Aceita                                                           |
| Data      | 2026-06-12                                                       |
| Autores   | @agnaldo4j                                                       |
| Branch    | —                                                                |
| Supersede | —                                                                |

---

## Contexto e Motivação

Auditoria FP+OO aplicada ao `domain/` após conclusão integral do ADR-0004 revelou 4 gaps de
pureza que comprometem a qualidade do núcleo de domínio:

1. **Segurança de tipos:** `Decision.payload: Map<String, String>` força extração de campos via
   `payload["cardId"] ?: return` — 4 null-guards não verificados pelo compilador no `SimulationEngine`.
2. **Pureza de funções:** `Card.consumeEffort` tem `now: Instant = Instant.now()` como default —
   viola transparência referencial no domínio (mesmo input → resultados diferentes a cada chamada).
3. **Imutabilidade:** `EngineContext.movements: MutableList<Movement>` usa mutação por referência
   partilhada entre todos os helpers do `SimulationEngine` — viola o princípio de que dados
   fluem por parâmetros, não por estado partilhado.
4. **Completude de domínio:** `orderTodoByPriority` não implementa scheduling para as 4 classes
   de serviço definidas por Burrows (*Kanban from the Inside*) — `FIXED_DATE` e `INTANGIBLE`
   recebem o mesmo peso que `STANDARD`, ignorando suas políticas distintas de custo de atraso.

O projeto atingiu **9.3/10** no scorecard de qualidade com ADR-0004 100% executado.
Esta ADR define o próximo ciclo de excelência focado em pureza funcional do núcleo de domínio,
com meta de atingir **9.6/10** nas dimensões `fp-oo-kotlin` (7.5 → 9.5) e
`screaming-architecture` (8.5 → 9.0).

---

## Gaps e Decisões

### GAP-AA `[N]` — `orderTodoByPriority`: 4 classes de serviço Burrows

**Arquivo:** `domain/src/main/kotlin/com/kanbanvision/domain/simulation/SimulationEngine.kt`

**Problema:** apenas `EXPEDITE` tem tratamento especial; `FIXED_DATE`, `STANDARD` e `INTANGIBLE`
são agrupados em `others` e shuffled conjuntamente. O KDoc de `ServiceClass.kt` documenta
explicitamente esse gap: `"Currently scheduled with the same weight as STANDARD."`.

Burrows define 4 classes com políticas distintas de custo de atraso:

| Classe | Custo de atraso | Política de scheduling |
|---|---|---|
| `EXPEDITE` | Altíssimo, linear | Começa antes de tudo — capacidade ilimitada |
| `FIXED_DATE` | Baixo até o prazo, depois exponencial | Segunda prioridade — deadline-driven |
| `STANDARD` | Linear | Fila normal — throughput-based |
| `INTANGIBLE` | Flat (estratégico, sem data) | Última prioridade — cede a todos os outros |

**Decisão:** implementar a ordem `EXPEDITE > FIXED_DATE > STANDARD > INTANGIBLE`:

```kotlin
private fun orderTodoByPriority(cards: List<Card>, rng: Random): List<Int> {
    val todoIndices = cards.indices.filter { cards[it].state == CardState.TODO }
    val expedite   = todoIndices.filter { cards[it].serviceClass == ServiceClass.EXPEDITE }
    val fixedDate  = todoIndices.filter { cards[it].serviceClass == ServiceClass.FIXED_DATE }
    val standard   = todoIndices.filter { cards[it].serviceClass == ServiceClass.STANDARD }.shuffled(rng)
    val intangible = todoIndices.filter { cards[it].serviceClass == ServiceClass.INTANGIBLE }.shuffled(rng)
    return expedite + fixedDate + standard + intangible
}
```

**Impacto:** zero breaking changes na API pública; testes existentes passam sem alteração;
novos testes cobrem a ordem das 4 classes e o comportamento de yield de `INTANGIBLE`.

---

### GAP-AB `[N]` — `Card.consumeEffort` pura

**Arquivos:** `domain/src/main/kotlin/com/kanbanvision/domain/model/Card.kt`,
`domain/src/main/kotlin/com/kanbanvision/domain/model/Step.kt`

**Problema:** `fun consumeEffort(ability: AbilityName, points: Int, now: Instant = Instant.now())`
tem `Instant.now()` como default — efeito colateral escondido numa função de domínio.
Chamar `card.consumeEffort(ability, points)` sem `now` produz resultados diferentes a cada
invocação, violando transparência referencial. Funções em `domain/` devem ser puras:
mesmo input → mesmo output.

**Decisão:** remover o default. O caller passa `now` explicitamente — o "relógio do dia"
pertence à borda (`SimulationEngine.runDay`), não ao domínio:

```kotlin
// Card.kt — sem default (pura)
fun consumeEffort(ability: AbilityName, points: Int, now: Instant): Card

// Step.executeCard (ou SimulationEngine) — efeito na borda
val now = Instant.now()
card.consumeEffort(ability, capacityPoints, now)
```

**Impacto:** 1–2 callers em `Step.kt`; testes que chamam `consumeEffort` precisam passar `now`
— usar `Instant.EPOCH` ou qualquer `Instant` fixo para manter determinismo.

---

### GAP-AC `[N]` — `EngineContext` com acumulador imutável

**Arquivo:** `domain/src/main/kotlin/com/kanbanvision/domain/simulation/SimulationEngine.kt`

**Problema:** `EngineContext` é um `data class` mas contém `movements: MutableList<Movement>`
e `rng: Random` — dois campos com estado mutável partilhado por referência entre todos os
helpers `applyMove`, `applyBlock`, `applyUnblock`, `autoAdvance`, `applySingleWorkerExecution`.
Isso viola o princípio FP de que dados fluem como valores, não por mutação de estado partilhado.

**Decisão:** cada fase do pipeline retorna seus movimentos; `runDay` acumula com `+` (list
concatenation) — sem referência partilhada:

```kotlin
// Sem EngineContext mutável
private fun applyDecisions(
    cards: List<Card>,
    board: Board,
    decisions: List<Decision>,
    day: Int,
    rng: Random,
): Pair<List<Card>, List<Movement>>

private fun autoAdvance(
    cards: List<Card>,
    wipLimit: Int,
    day: Int,
    rng: Random,
): Pair<List<Card>, List<Movement>>

// runDay acumula
val (afterDecisions, movDecisions) = applyDecisions(initialCards, board, decisions, day, rng)
val (afterAutoAdvance, movAutoAdvance) = autoAdvance(afterDecisions, wipLimit, day, rng)
val allMovements = movDecisions + movAutoAdvance
```

`EngineContext` pode ser simplificado para carregar apenas campos imutáveis (`day`, `seed`)
ou eliminado por completo — `rng` é passado explicitamente.

**Impacto:** refactoring interno ao `SimulationEngine`; API pública (`runDay`) e
`SimulationResult` inalteradas; todos os testes passam sem alteração de asserção.

---

### GAP-AD `[M]` — `Decision` como sealed hierarchy

**Arquivos:**
- `domain/src/main/kotlin/com/kanbanvision/domain/model/Decision.kt` (refatorar)
- `domain/src/main/kotlin/com/kanbanvision/domain/model/DecisionType.kt` (deletar)
- `domain/src/main/kotlin/com/kanbanvision/domain/simulation/SimulationEngine.kt`
- Todos os testes e callers de `Decision.block/unblock/move/addItem()`

**Problema:** `Decision(type: DecisionType, payload: Map<String, String>)` separa o tipo de
seus dados, forçando o `SimulationEngine` a extrair campos via string-key com null-guards:

```kotlin
// 4 ocorrências — null-guards não verificados pelo compilador
val cardId = payload["cardId"] ?: return
val title  = payload["title"]  ?: return
```

Esses null-guards são os principais **survivors do PITest** (mutantes que sobrevivem porque
testes não exercitam payloads ausentes). `DecisionType` como enum duplica informação que
o subtipo sealed já carrega.

**Decisão:** substituir por `sealed interface Decision` com subtipos tipados; `DecisionType`
é deletado:

```kotlin
// Decision.kt — sealed hierarchy tipada
sealed interface Decision {
    data class MoveItem(val cardId: String) : Decision
    data class BlockItem(
        val cardId: String,
        val reason: String = "blocked",
    ) : Decision
    data class UnblockItem(val cardId: String) : Decision
    data class AddItem(
        val title: String,
        val serviceClass: ServiceClass = ServiceClass.STANDARD,
    ) : Decision
}
```

`SimulationEngine.applyDecisions` usa `when (decision)` exaustivo por subtipo:

```kotlin
when (decision) {
    is Decision.MoveItem    -> applyMove(current, decision.cardId, ctx)
    is Decision.BlockItem   -> applyBlock(current, decision.cardId, decision.reason, ctx)
    is Decision.UnblockItem -> applyUnblock(current, decision.cardId, ctx)
    is Decision.AddItem     -> applyAdd(current, board, decision.title, decision.serviceClass)
}
```

Fábricas do companion (`Decision.block()`, `Decision.addItem()` etc.) são substituídas pelos
construtores diretos (`Decision.BlockItem("c1", "motivo")`).

**Impacto:** `[M]` — todos os callers e testes que constroem `Decision` precisam migrar para
a nova sintaxe. O compilador guia a migração (erros de tipo). `DecisionType.kt` é deletado.
Após a mudança, o PITest score deve subir: null-guards eliminados → mutantes mortos.

---

## Execução por sessão (protocolo 1-gap-por-sessão)

| Sessão | Gap | Tipo | Dependência | Branch sugerida |
|---|---|---|---|---|
| 1 | GAP-AA | N | nenhuma | `feat/gap-aa-service-class-scheduling` |
| 2 | GAP-AB | N | nenhuma | `feat/gap-ab-card-consume-effort-pure` |
| 3 | GAP-AC | N | nenhuma | `feat/gap-ac-engine-context-immutable` |
| 4 | GAP-AD | M | GAP-AA e GAP-AC recomendados antes | `feat/gap-ad-decision-sealed-hierarchy` |

GAP-AA e GAP-AC podem ser executados em qualquer ordem independentemente.
GAP-AD é recomendado após GAP-AC porque ambos tocam `SimulationEngine.kt` — fazê-los em
sequência minimiza conflitos de merge.

---

## J-Curve Safety

| Medida | Limite | Observação |
|---|---|---|
| JaCoCo | ≥ 97% por módulo | Inalterado |
| Detekt | 0 violações | Inalterado |
| KtLint | 0 erros | `./gradlew ktlintFormat` antes do commit |
| PR size | ≤ 400 linhas | GAP-AD pode ser maior — monitorar |
| PITest threshold | ≥ 58% | GAP-AD deve elevar o score (null-guards eliminados) |

---

## Consequências

**Positivas:**
- `decision.cardId` em vez de `payload["cardId"] ?: return` — compilador garante segurança
- `Card.consumeEffort` determinística — testes mais simples e estáveis
- `SimulationEngine` sem estado partilhado — cada fase testável isoladamente
- Scheduling Burrows-completo — alinhamento com a teoria do Kanban

**Negativas / riscos:**
- GAP-AD requer migração de todos os callers de `Decision.*` — mudança transversal
- PITest pode revelar novos survivors após GAP-AC (refactoring de `SimulationEngine`)
  que exijam testes adicionais em GAP-AD

---

## Referências

- Burrows, M. *Kanban from the Inside*, cap. 4 — The Four Classes of Service
- Martin, R.C. *FP vs OO are Orthogonal*, 2018 — https://blog.cleancoder.com/uncle-bob/2018/04/13/FPvsOO.html
- Arrow-kt — https://arrow-kt.io/learn/quickstart/
- ADR-0004 — Avaliação de Qualidade: Gaps e Prioridades (roadmap anterior)
- Skill `/fp-oo-kotlin` — auditoria completa que motivou esta ADR
