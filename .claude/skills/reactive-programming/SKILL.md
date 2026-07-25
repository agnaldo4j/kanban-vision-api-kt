---
name: reactive-programming
description: >
  Avalie programação reativa — o paradigma declarativo de dataflow e propagação de
  mudança (streams/observables, push vs pull) e sua vertente funcional (FRP:
  behaviors contínuos vs events discretos). Foco em Kotlin: o espectro
  suspend → Flow (cold, back-pressure) → Rx/Reactor, quando cada um cabe. Use ao
  decidir se um caminho pede request/response (coroutine) ou stream de múltiplos
  valores no tempo (Flow); ao avaliar uma proposta rotulada "reativa"; e para não
  fazer cargo-cult de Flow/observables em CRUD single-shot. É o par local (tempo) do
  `/reactive-systems` (arquitetura/espaço). Baseado em
  https://en.wikipedia.org/wiki/Reactive_programming,
  https://en.wikipedia.org/wiki/Functional_reactive_programming e
  https://www.kodeco.com/android/paths/concurrency-networking/44456725-concurrency-with-kotlin-flow/01-learn-reactive-programming/02
argument-hint: "[caminho, endpoint ou proposta 'reativa' a avaliar (opcional)]"
allowed-tools: Read, Grep, Glob
---

# Reactive Programming — o Paradigma de Dataflow (não a arquitetura)

> "Programação reativa é um paradigma **declarativo** preocupado com **fluxos de dados** e a **propagação de
> mudança**." Como uma planilha: a célula `a = b + c` recalcula sozinha quando `b` ou `c` mudam — você declara
> a dependência, não reexecuta a fórmula na mão.
> — https://en.wikipedia.org/wiki/Reactive_programming

Este skill trata do **paradigma** (como o código local reage a valores que chegam ao longo do tempo), **não** da
arquitetura. É o par exato do `/reactive-systems`: aquele desacopla no **espaço** (distribuição, message-driven);
este desacopla no **tempo** (concorrência, dataflow). **Programação reativa não dá resiliência nem elasticidade
de sistema** — isso é `/reactive-systems`. Confundir os dois é o erro nº 1 (cf. o post da Akka lá citado).

> Complementa `/reactive-systems` (arquitetura/os 4 traços), `/fp-oo-kotlin` (os blocos funcionais — `map`/
> `filter`/`fold` — que a FRP usa; `Either`), `/opentelemetry` e `/load-testing` (evidência).

---

## 1. O que é (e o que não é) programação reativa

**A ideia central:** modelar dados como **streams** que emitem valores no tempo, e expressar a lógica como
**transformações declarativas** desses streams (`map`/`filter`/`scan`/`flatMap`), com a **mudança se propagando**
pela cadeia automaticamente. O oposto do imperativo (`statements que mudam estado` passo a passo).

| Eixo | Significado | Por que importa na avaliação |
|---|---|---|
| **Push vs Pull** | *Pull*: o consumidor pergunta (polling). *Push*: a fonte empurra quando há valor. *Push-pull*: notifica leve, consulta seletiva. | Flow do Kotlin é **push com back-pressure** (o coletor regula o ritmo). Um `List` devolvido é pull/single-shot. |
| **Cold vs Hot** | *Cold*: cada coletor dispara a produção do zero (ex.: `flow { }`). *Hot*: emite independente de haver coletor (ex.: `SharedFlow`, eventos de UI). | Escolher errado = re-executar trabalho caro (cold multi-coletor) ou perder eventos (hot sem buffer). |
| **Eager vs Lazy / glitches** | Avaliação ansiosa propaga já (risco de estado intermediário inconsistente — *glitch*); preguiçosa adia. Ordenação topológica evita glitch. | Relevante em grafos de dependência reativa (planilha); em Flow linear raramente aparece. |
| **Observable/Observer** | Observables são streams; observers assinam; a emissão notifica os assinantes. | É a forma OO da reatividade (padrão Observer) — **≠** dataflow funcional. |

**O que NÃO é reatividade de programação:** uma função `suspend` que faz uma chamada e devolve **um** valor
(request/response) é **async/non-blocking**, mas não é "reativa" no sentido de stream/propagação — é um único
resultado no tempo, não um fluxo. Chamar isso de "reativo" infla a palavra.

---

## 2. Functional Reactive Programming (FRP)

> "FRP combina programação funcional com dataflow reativo, usando `map`/`reduce`/`filter` sobre valores que
> variam no tempo." — https://en.wikipedia.org/wiki/Functional_reactive_programming

A FRP eleva o **tempo a valor de primeira classe**. Dois conceitos:
- **Behaviors / signals** — valores que variam de forma **contínua** no tempo (ex.: a posição do mouse *sempre*
  tem um valor).
- **Events** — ocorrências **discretas** em pontos do tempo (ex.: um clique).

Duas formulações: **contínua/denotacional** (funções contínuas do tempo, abstrai sampling) e **discreta/
event-driven** (updates discretos; combina behaviors+events em signals). **Nota:** ReactiveX (RxJava/RxJS) é
"funcional e reativo" mas **difere** da FRP clássica — Rx é stream-de-eventos, não modela tempo contínuo. No
mundo Kotlin/JVM, o que se usa na prática é **stream discreto** (Flow/Rx), não FRP denotacional.

**Onde a FRP encosta neste projeto:** o simulador é, no domínio, uma função **pura e discreta do tempo** —
`SimulationEngine.runDay(sim, decisions, seed, now)` produz o snapshot do dia *d*; a sequência de `DailySnapshot`
ao longo dos dias é, conceitualmente, um *behavior discreto* (a métrica do fluxo variando por dia). Isso é FRP
"na modelagem" (tempo explícito, transformação pura — ver `/fp-oo-kotlin` sobre `runDay` puro/clock injetado),
**sem** framework reativo. É o melhor de FRP (tempo como valor, pureza) sem o custo de um runtime de streams.

---

## 3. O espectro em Kotlin: `suspend` → `Flow` → Rx/Reactor

A reatividade de programação em Kotlin é um **espectro**, não um botão. Escolha pelo **formato do dado**, não pela
moda:

| Ferramenta | Formato | Back-pressure | Quando |
|---|---|---|---|
| `suspend fun (): T` | **um** valor async | n/a | request/response single-shot (a maioria deste projeto) |
| `Flow<T>` (cold) | **N** valores no tempo | **sim** (coletor regula via suspensão) | stream/streaming, produtor incremental, SSE/WebSocket |
| `SharedFlow`/`StateFlow` (hot) | N valores, multicast | buffer/estratégia | fan-out de eventos, estado observável |
| RxJava/Reactor | N valores + operadores ricos | sim | interop com libs Rx; **evitar** se o stack já é coroutines |

> **Regra Kotlin:** a ferramenta reativa idiomática aqui é **Flow** (nativo de coroutines), **não** RxJava — o
> stack já é coroutines (Ktor `suspend`). Misturar Rx e coroutines adiciona uma ponte e dois modelos de
> cancelamento/erro. Se algum dia precisar de streams, use Flow.

---

## 4. Neste projeto: coroutines request/response, **zero** dataflow reativo (por escolha)

**Fato verificado (grep):** não há **nenhum** `Flow`/`observable`/`flow { }` em produção
(`*/src/main`). O que existe:
- **Coroutines request/response:** use cases `suspend`, Ktor non-blocking, o `transaction{}` bloqueante isolado
  em `Dispatchers.IO` (`PersistenceSupport.kt:23`). Isso é **concorrência** (async), não dataflow reativo.
- **Observer síncrono:** `EventPublisherPort.publish(events)` (`EventPublisherPort.kt:6`) +
  `MicrometerEventPublisher` — é o padrão Observer, mas **síncrono e in-process** para um sink de métricas; não é
  um stream reativo (sem push assíncrono, sem back-pressure, sem coletor). Não o chame de "stream reativo".

**Isto é correto**: uma API de simulação snapshot/request-response **não precisa** de streams. Coroutine single-shot
é o idioma certo; adicionar Flow a um `GET` que devolve um objeto seria over-engineering.

**Onde Flow *se encaixaria* (candidatos honestos, se o requisito surgir):**
- **Streaming dos dias de uma simulação** — `GetSimulationDaysUseCase`/CFD hoje devolvem `List` materializada; um
  `Flow<DailySnapshot>` emitindo dia a dia caberia num endpoint **SSE/WebSocket de progresso** de uma simulação
  longa (produtor natural incremental, com back-pressure para não afogar o cliente).
- **Progresso ao vivo de `runDay`** em execuções multi-dia — emitir cada snapshot conforme calculado.
- Só vale **se** houver o requisito de *consumo incremental/tempo-real*. Para "rode N dias e me dê o resultado",
  `suspend`+`List` é mais simples e é o que está lá.

---

## 5. Rubric: request/response ou stream?

1. **Formato do dado:** a operação produz **um** resultado (mesmo que async) → `suspend fun`. Produz **N** valores
   ao longo do tempo que o cliente quer consumir **incrementalmente** → `Flow`. Não é "quantos itens há", é "eles
   chegam ao longo do tempo e o consumo é incremental?".
2. **Push ou pull?** O cliente pede e espera o todo → pull/`suspend`. A fonte empurra conforme produz e o cliente
   reage → push/`Flow`.
3. **Precisa back-pressure?** Produtor pode ser mais rápido que o consumidor e você quer que o consumidor **regule
   o ritmo** → Flow (dá isso nativo). Se não há esse descompasso, não invente stream.
4. **Cold ou hot?** Cada consumidor precisa da produção do zero → cold (`flow {}`). Múltiplos consumidores
   compartilham o mesmo fluxo de eventos → hot (`SharedFlow`), com estratégia de buffer explícita.
5. **É programming, não systems.** Nada disso te dá resiliência/elasticidade distribuída — se o requisito é
   *entre serviços/nós*, o skill é `/reactive-systems` (e provavelmente um ADR).

---

## 6. Anti-padrões

| Afirmação / código | Por que é enganoso | Correto |
|---|---|---|
| `Flow<T>` para devolver **um** valor | overhead de stream sem stream; complica erro/cancelamento | `suspend fun (): T` |
| "somos reativos porque usamos `suspend`/coroutines" | coroutine é async/concorrência, não dataflow reativo | é non-blocking local; reativo (stream) só com Flow |
| Misturar RxJava + coroutines no mesmo caminho | dois modelos de cancelamento/erro + ponte | Flow (o stack já é coroutines) |
| `EventPublisherPort.publish` chamado de "stream reativo" | é Observer **síncrono** in-process | event broadcast síncrono; ver `/reactive-systems` (Message-Driven ausente) |
| cold `Flow` caro coletado por N consumidores | recomputa tudo por consumidor | `shareIn`/`SharedFlow` (hot) se o custo justifica |
| "programação reativa vai deixar o sistema resiliente/elástico" | confunde tempo (programming) com espaço (systems) | resiliência/elasticidade = `/reactive-systems` |

---

## Quando usar este skill

- Ao projetar um endpoint/uso que **pode** ser streaming (progresso, série temporal, SSE/WebSocket) — decidir
  `suspend` vs `Flow`.
- Ao avaliar um PR/proposta rotulada "reativa" — classificar (single-shot async? stream? FRP-na-modelagem?) e
  cobrar que a ferramenta caiba no formato do dado.
- Ao ver RxJava sendo cogitada — lembrar que o idioma aqui é Flow.
- Ao explicar por que o projeto **não** usa Flow hoje (e está certo) — snapshot/request-response.

## Relação com outros skills

| Este skill | Complementa |
|---|---|
| Paradigma local / desacoplamento no **tempo** | `/reactive-systems` — arquitetura / desacoplamento no **espaço** (os 4 traços) |
| Blocos funcionais da FRP (`map`/`filter`/`fold`), pureza, `runDay` como função do tempo | `/fp-oo-kotlin` — FP, imutabilidade, `Either`, funções puras |
| Evidência de que um stream se comporta | `/load-testing` (throughput/latência), `/opentelemetry` (métricas) |
| Streaming como fronteira de efeito | `/clean-architecture` — efeito na borda, domínio puro |

## Checklist de avaliação

- [ ] Classifiquei o dado: **um** valor async (`suspend`) vs **N** valores no tempo consumidos incrementalmente (`Flow`)?
- [ ] Se escolhi `Flow`: há de fato consumo **incremental/tempo-real** e/ou necessidade de **back-pressure**? Ou é single-shot disfarçado?
- [ ] Se `Flow`: **cold** ou **hot** decidido conscientemente (recomputo por consumidor vs multicast)?
- [ ] Não estou misturando **Rx + coroutines** no mesmo caminho (idioma = Flow)?
- [ ] Não chamei `suspend`/coroutine/`publish` síncrono de "stream reativo"?
- [ ] Lembrei que isto é **programming (tempo)**, não **systems (espaço)** — resiliência/elasticidade é `/reactive-systems`?

## Referências

- Reactive programming — https://en.wikipedia.org/wiki/Reactive_programming (dataflow, propagação de mudança, push/pull, cold/hot, glitches)
- Functional Reactive Programming — https://en.wikipedia.org/wiki/Functional_reactive_programming (behaviors contínuos vs events discretos; ≠ ReactiveX)
- Concurrency with Kotlin Flow (Kodeco) — https://www.kodeco.com/android/paths/concurrency-networking/44456725-concurrency-with-kotlin-flow/01-learn-reactive-programming/02
- No projeto: `EventPublisherPort.kt` (Observer síncrono), `PersistenceSupport.kt:23` (dispatch), `GetSimulationDaysUseCase`/CFD (candidatos a Flow); **sem** Flow em produção. Par arquitetural: `/reactive-systems`.
