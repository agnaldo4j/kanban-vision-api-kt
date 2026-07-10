---
status: accepted
date: 2026-07-09
decision-makers: "@agnaldo4j"
---

# ADR-0034 — Maturidade FP e IDs como value classes (opaque types)

> Registra a avaliação da postura de **programação funcional** do projeto (controle de efeitos,
> transparência referencial, imutabilidade) e dos **recursos de sistema de tipos** viáveis em
> Kotlin. **Decisão:** manter a postura FP atual (forte) e **adotar `@JvmInline value class` para os
> identificadores de domínio** (opaque types) — eliminando a *primitive obsession* de IDs `String`.
> Recursos exclusivos de Scala 3/HKT (union types abertos, structural types, dependent function
> types, typeclasses higher-kinded) **não** são perseguidos: não existem em Kotlin. Implementação
> rastreada como GAP-BT `[M]`.

## Context and Problem Statement

Avaliamos "como o projeto está" em FP e quais construções de tipos fazem sentido adotar. Estado
atual, medido no código (`main`):

- **Imutabilidade — forte.** `data class` com `val`; apenas **1 `var`** e ~10 coleções mutáveis no
  domínio, todas dentro do núcleo imperativo de `SimulationEngine.runDay`, encapsulado numa fronteira
  pura (imperative core / functional shell). Nenhuma entidade é mutável externamente.
- **Controle de efeitos — forte.** Efeitos nas bordas (repositórios Exposed, `MicrometerEventPublisher`,
  logging, DB). `DomainPurityTest` (Konsist, ADR-0026) falha o CI se `domain` importar frameworks.
  `runDay` é determinístico (seed estável).
- **Transparência referencial — alta nas fronteiras.** Erros como valores: `Either<DomainError, T>`
  (42 usos), `either { }` (15), `ensure`/`zipOrAccumulate`/`Raise`. Sem exceção para controle de
  fluxo entre camadas. **Ressalva:** métodos de agregado (`Board.addStep`) e conversores de fronteira
  (`MovementType.valueOf`, `DecisionSurrogate.toDomain`, `error("Step not found")`) **lançam** — a
  totalização acontece na camada de use-case. Blend OO+FP deliberado (skill `/fp-oo-kotlin`).

**Recursos de tipos** — o que é viável em Kotlin (≠ Scala 3):

| Recurso | Kotlin | No projeto hoje |
|---|---|---|
| Inferred Types | ✅ | idiomático |
| Generics (sem HKT) | ✅ | `Either<E,T>`, `Page<T>`, `Raise<E>`, ports |
| Intersection Types | ⚠️ só implícito (bounds / smart-cast) | via bounds |
| Union Types (abertos `A\|B`) | ❌ | aproximado por `sealed` + `Either` |
| Algebraic Data Types | ✅ `sealed`+`data class` | `Decision`, `DomainError`, `DomainEvent` |
| Variance (`in`/`out`) | ✅ | nenhuma declarada |
| **Opaque Types** | ✅ `@JvmInline value class` | **só `SimulationDay`** (IDs são `String`/`data class`) |
| Structural Types | ❌ (nominal) | n/a |
| Dependent Function Types | ❌ | n/a |
| Monoids/Monads/typeclasses | ⚠️ monads **concretos** (Arrow `Either`/`Raise`/`either{}`); **sem HKT** → sem typeclass genérica | monádico via `either { }`; sem `Monoid` formal |

A lacuna concreta e de maior valor: **IDs de entidade são `String` cru** e os `*Ref` são
`data class(String)` — permitem passar um id de um agregado onde se espera outro. É a *primitive
obsession* já apontada como resíduo de DDD em `docs/quality/scorecard-2026-07.md`.

## Decision Drivers

- Type-safety de identidade a **custo zero** de runtime (o alvo de produção é GraalVM Native Image).
- Manter-se **idiomático em Kotlin**; não importar recursos que a linguagem não tem.
- Não regredir a postura FP forte já existente (imutabilidade, efeitos-nas-bordas, erro-como-valor).

## Considered Options

**Para a postura FP geral:**
1. Manter como está (forte; efeitos nas bordas; `Either`/`Raise`).
2. Totalizar os métodos de agregado (`addStep` → `Either`) empurrando totalidade para o domínio.
3. Buscar abstrações FP genéricas (Functor/Monad/Monoid como typeclasses).

**Para a modelagem de IDs:**
A. Manter `String`/`data class *Ref` (status quo).
B. **`@JvmInline value class` para IDs** (opaque types) — zero-cost, type-safe. **(escolhida)**
C. Wrappers `data class` regulares (têm overhead de alocação; sem ganho sobre B).

## Decision Outcome

- **Postura FP: manter a Opção 1.** É forte e adequada. A Opção 2 contraria o blend OO+FP
  deliberado (os `require`/`error` são guardas de invariante idiomáticos) — **rejeitada**. A Opção 3
  é **inviável**: Kotlin não tem higher-kinded types, então não há `Monad`/`Functor` genérico; o
  Arrow 2.x abandonou a simulação de HKT em favor de `Raise`/context — o projeto já usa a forma
  concreta correta (`either { }` = monad comprehension). Formalizar um `Monoid` para acumulação
  (`FlowMetrics`, `movements`) fica como melhoria opcional de baixo valor, **não** priorizada.
- **IDs: adotar a Opção B — `@JvmInline value class`** (opaque types). Implementação em **GAP-BT
  `[M]`**.

Recursos Scala-3-only (union abertos, structural, dependent function types, HKT) **não** entram —
não existem em Kotlin.

### Confirmation

- GAP-BT entrega os IDs como value classes; `testAll` (incl. fitness functions Konsist e cobertura
  ≥98%) permanece verde; a dimensão *fp-oo-kotlin* do scorecard e o resíduo de DDD (primitive
  obsession de IDs) deixam de existir.
- Esta ADR não altera código por si; a Confirmation se cumpre quando GAP-BT estiver mergeado.

## Pros and Cons — IDs como value class (opção escolhida)

- ✅ **Type-safety de identidade**: o compilador impede passar `CardId` onde se espera `BoardId`.
- ✅ **Zero-cost**: `@JvmInline` não aloca no caminho quente; sem custo em memória/throughput
  (relevante para o Native Image).
- ✅ Elimina o resíduo de DDD; alinha com o skill `/ddd` (Value Objects para identidade).
- ⚠️ **Migração transversal**: toca `domain/model`, os serializers (`kotlinx.serialization` de value
  class exige `@Serializable` no wrapper e cuidado com o `$serializer`), DTOs e mapeamentos de repo.
  Escopo controlado, mas não trivial — daí ser `[M]`, não `[N]`.
- ⚠️ **GraalVM native**: value classes são resolvidas em compile-time; a reachability metadata
  existente cobre o padrão (validar no smoke da imagem, como manda o DoD do gap).

## Consequences

- Nenhuma mudança imediata de build/runtime nesta ADR (é registro + direção).
- Follow-up: **GAP-BT `[M]`** — IDs como `@JvmInline value class`.
- **Não** perseguimos recursos ausentes em Kotlin; se um dia migrarmos parte do core para outra
  linguagem/FP-first, reabrir a discussão (improvável dado o alvo Kotlin+Native).
- `SimulationDay` já é value class — serve de referência de padrão para a migração.

## Related

`/fp-oo-kotlin` skill · `/ddd` skill · ADR-0018 (FP/OO domain purity, sealed `Decision`) ·
ADR-0026 (fitness functions) · ADR-0030/0032 (GraalVM Native Image) ·
`docs/quality/scorecard-2026-07.md` (resíduo de primitive-obsession de IDs)
