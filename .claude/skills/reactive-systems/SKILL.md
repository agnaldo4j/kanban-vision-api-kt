---
name: reactive-systems
description: >
  Avalie a reatividade do sistema pelos quatro traços do Reactive Manifesto
  (Responsive, Resilient, Elastic, Message-Driven) e pela distinção crítica entre
  *reactive programming* (dataflow assíncrono local — coroutines/streams) e *reactive
  systems* (arquitetura distribuída message-driven). Use ao avaliar uma mudança que
  toca resiliência, escalabilidade, latência ou acoplamento; ao decidir se uma ideia
  "reativa" é barata (nível-programação, local) ou cara (nível-sistema, distribuição,
  exige ADR); e para evitar cargo-cult de message-driven num monólito modular onde
  location transparency já é de graça. Baseado em https://www.reactivemanifesto.org e
  https://akka.io/blog/reactive-programming-versus-reactive-systems
argument-hint: "[componente, mudança ou traço a avaliar (opcional)]"
allowed-tools: Read, Grep, Glob
---

# Reactive Systems — Avaliar Reatividade (Manifesto + Programming vs Systems)

> "Reactive Systems são Responsivos, Resilientes, Elásticos e Message-Driven.
> Responsividade é o objetivo; message-driven é a fundação que torna resiliência e
> elasticidade possíveis."
> — https://www.reactivemanifesto.org

> "Decoupling in time allows for concurrency, but it is decoupling in space that allows
> for distribution." — reactive **programming** dá o primeiro; reactive **systems** dá o segundo.
> — https://akka.io/blog/reactive-programming-versus-reactive-systems

Este skill é uma **lente de avaliação**, não um mandato para "tornar tudo reativo". Ele existe para responder
com honestidade três perguntas sobre qualquer mudança: **(1)** qual dos quatro traços ela toca? **(2)** ela é
*nível-programação* (local, barata) ou *nível-sistema* (distribuição, cara, exige ADR)? **(3)** ela é
apropriada para o que este projeto **é** — um **monólito modular** (Ktor/Netty), não um sistema distribuído?

> Complementa `/microservices-modular-monolith` (as costuras de extração — o caminho para um reactive system
> real), `/opentelemetry` (evidência de responsividade), `/load-testing` (evidência de elasticidade),
> `/local-and-production-environment` (HPA/PDB no k8s), `/fp-oo-kotlin` (Either = falha-como-valor = resiliência),
> `/clean-architecture` + `/circular-dependency-control` (isolamento/desacoplamento).

---

## A distinção que organiza tudo: Programming vs Systems

O erro mais comum é confundir os dois. Eles resolvem problemas **diferentes** e são complementares — um roda
*dentro* dos componentes, o outro *entre* eles.

| | **Reactive Programming** | **Reactive Systems** |
|---|---|---|
| **Escopo** | local, in-process, um nó | distribuído, arquitetura, múltiplos nós/serviços |
| **Desacopla** | no **tempo** (concorrência) | no **espaço** (distribuição) |
| **Técnicas** | Futures, Streams+back-pressure, Rx/Reactor, **coroutines** | message-passing assíncrono, location transparency, componentes endereçáveis, supervisão, bulkheads |
| **Resolve** | uso de CPU/IO, produtividade do dev | resiliência + elasticidade, cloud-native, escala |
| **Neste projeto** | **parcial** — Ktor/Netty non-blocking, use cases `suspend`, o `transaction{}` bloqueante empurrado para `Dispatchers.IO` (`PersistenceSupport.kt:23`). **Sem** streams/`Flow`/back-pressure de dataflow — é request/response com coroutines, não Rx. | **ausente por design** — é um monólito; **não** há message-passing entre serviços, ator, supervisão nem location transparency de rede. |

> **Por que programming sozinho não basta (a tese do artigo da Akka):** callbacks/futures são *anônimos e
> não-endereçáveis* — dão decoupling no tempo, mas não no espaço; recuperação de falha e self-healing exigem
> componentes **endereçáveis** (o que só a arquitetura message-driven dá). Corolário para nós: adotar mais
> "reactive programming" (ex.: trocar coroutines por `Flow`) **não** move a agulha de resiliência/elasticidade
> de *sistema* — essas vêm da arquitetura (k8s, circuit breakers, statelessness), que já temos.

---

## Os quatro traços — avaliados contra o código real

A estrutura do Manifesto: **Message-Driven** (fundação) → habilita **Resilient** + **Elastic** (meios) →
que entregam **Responsive** (objetivo). Avaliamos de baixo (fundação) para cima (objetivo).

### 🧱 Message-Driven — a fundação (ABSENTE POR DESIGN; o que existe é event-driven in-process)

**Definição:** componentes comunicam-se por **mensagens assíncronas endereçadas** (destino claro), estabelecendo
loose coupling, isolamento e location transparency.

**No projeto:** `EventPublisherPort.publish(events: List<DomainEvent>)` (`EventPublisherPort.kt:5-6`) +
`MicrometerEventPublisher` (`MicrometerEventPublisher.kt:7-10`). Mas isto é **síncrono e in-process**: publica
para um *sink de métricas*, não passa mensagem endereçada a outro componente. Pela distinção do artigo:
`DomainEvent` é um **evento** ("fato para outros observarem", sem destino), não uma **mensagem** ("destino único,
claro"). **Isso é correto para um monólito** — não é um defeito.

**Perguntas de avaliação:**
- A mudança introduz comunicação **entre processos/serviços**? Se sim, é nível-sistema → **ADR** (é o começo de
  um reactive system real; ver `/microservices-modular-monolith`).
- Alguém está chamando `publish()` de "message-driven"? Corrija: é event broadcast síncrono. Não prometa
  garantias (entrega, isolamento de falha, back-pressure) que uma chamada de método não tem.
- **Não** adote broker/ator/message bus só para "ser reativo": num monólito com chamadas in-process você já tem
  location transparency de graça e ordem/consistência triviais. Só pague a complexidade quando houver uma costura
  de extração real (`/microservices-modular-monolith`).

### 🛡️ Resilient — responsivo sob falha (REALIZADO, forte para um monólito)

**Definição:** mantém-se responsivo diante de falhas, via replicação, contenção, **isolamento** e delegação;
falha é **reificada** e tratada *fora* do componente que falhou.

**No projeto (bem coberto):**
- **Circuit-breaking + timeouts:** `CircuitBreakerDataSource` (decora o `DataSource`, rejeita checkout
  com circuito aberto em vez de esperar o timeout do pool — `CircuitBreakerDataSource.kt`), `DbCircuitBreaker`,
  `RedisCircuitBreaker` (resilience4j). Contém a falha do banco/Redis quando o circuito **já abriu**.
  ⚠️ **Circuit breaker ≠ bulkhead.** Com o circuito *fechado*, N chamadas lentas concorrentes ainda ocupam todas
  as threads/conexões até seus timeouts — cascata que o breaker sozinho não previne. O **bulkhead** de verdade é
  **concorrência limitada** (pool/semáforo dedicado): aqui, o `maximumPoolSize` do HikariCP dá um teto de
  conexões, mas não há isolamento por-dependência além disso. Ao avaliar, exija o limite de concorrência
  explicitamente — não trate o breaker como prova de bulkhead.
- **Falha-como-valor (reificação):** `Either<DomainError, T>` em todas as camadas (ADR-0044) — a exceção não
  propaga como control-flow; a falha é um valor tratado na borda (`/fp-oo-kotlin`).
- **Degradação graciosa:** `buildFallbackSimulation` (`JdbcSimulationRepository.kt:103`) devolve um resultado
  utilizável quando o estado rico está **ausente/vazio** (`stateJson.isNullOrBlank()`, `:99-100`) — mas note o
  **limite**: JSON malformado ainda **lança** no `decode` → `PersistenceError`/500 (é justamente a lacuna de
  resiliência que o card de decode-tolerante ataca; ver abaixo). No rate-limit, a queda do Redis **degrada para
  um bucket local semeado** que continua limitando **por-pod** (nunca abre para ilimitado, nunca 5xx — GAP-BZ):
  degradação graciosa que **preserva o limite**, não "fail-closed" de negar tudo — mas o teto *global* dilui até
  `maxReplicas` (ver Elastic). ⚠️ Cuidado com o termo: "fail-closed" de **segurança** (`/owasp` A10 /
  `security.md` §6) é *negar acesso* quando o controle falha; aqui a falha é de *disponibilidade da dependência*,
  e a resposta certa é **degradar preservando a garantia** (continuar limitando), não bloquear a requisição.
  São eixos diferentes — não use "fail-closed" para descrever esta degradação.
- **Contenção na borda:** `StatusPages` converte `Throwable` em resposta controlada (nunca stack trace ao
  cliente — `/owasp` A10); crash → reinício pelo k8s.

**Perguntas de avaliação:**
- A dependência externa nova (banco, Redis, HTTP) tem **circuit breaker + timeout** E **concorrência limitada**
  (pool/semáforo dedicado = bulkhead real)? Só breaker/timeout não basta: com o circuito fechado, chamadas lentas
  concorrentes ainda podem esgotar threads/conexões antes de o breaker abrir.
- A falha é **valor tipado** (`Either`/`raise`) ou uma exceção que vaza? (regra do projeto: falha-de-domínio →
  `Either`; precondição → `require`.)
- Há **fallback** ou a falha de um campo torna o recurso inteiro indisponível? (o **decode intolerante a legado**
  — `require`/`valueOf` que lança em dado histórico → 500 na simulação inteira — é exatamente uma falha de
  resiliência; a regra "decode tolerante a legado" está em `.claude/rules/migrations.md`.)

### 📈 Elastic — responsivo sob carga variável (REALIZADO na infra; app stateless)

**Definição:** escala recursos para cima/baixo distribuindo a carga sem gargalos nem pontos de contenção.

**No projeto:**
- **HPA** (`k8s/06-hpa.yml`) escala réplicas por carga; **PDB** (`k8s/07-pdb.yml`) + `topologySpreadConstraints`
  (`k8s/03-deployment.yml`, ADR-0040) preservam disponibilidade durante o scaling.
- **App stateless** → horizontalmente escalável; o **rate-limit distribuído em Redis** (token bucket, ADR-0041/42)
  mantém o limite *global* sob N réplicas **enquanto o Redis está saudável** (não dilui — GAP-BZ). ⚠️ **Numa queda
  do Redis** cada pod cai no bucket local (`RedisRateLimiter.degradeTo()`), e o teto global pode **diluir até
  `maxReplicas`×local (`RedisCircuitBreaker.kt:42-49` documenta isso)** — degradação graciosa (nunca ilimitado,
  nunca 5xx), mas a garantia global é condicional ao Redis, não incondicional.
- **Startup rápido** (GraalVM Native Image, ~0,12s — `/graalvm`) = scale-up elástico de verdade (réplica nova
  serve tráfego em sub-segundo).

**Ponto fino — admission control ≠ back-pressure:** o rate limiter (`LocalTokenBucketRateLimiter`/`RedisRateLimiter`)
faz **load-shedding** (429 ao estourar), que é *controle de admissão*, **não** back-pressure (não há sinal de
profundidade de fila propagado ao produtor upstream). Chame pelo nome certo ao avaliar.

**Perguntas de avaliação:**
- A mudança introduz **estado local** (cache em memória, sticky session, contador in-process) que quebraria sob
  múltiplas réplicas? (O único estado mutável aceitável é infra-singleton documentado.)
- Sob 2× a carga, onde está o **gargalo**? (pool de conexões, Redis, CPU?) Há evidência em `/load-testing`
  (baseline p95/p99) ou é suposição?
- Precisa de **back-pressure real** (fila com sinal ao upstream) ou load-shedding (429) basta? Só o primeiro é
  "reativo" no sentido de streams.

### 🎯 Responsive — o objetivo (TRATADO COMO META, monitorado)

**Definição:** responde em tempo hábil sempre que possível; detecção rápida de problema e confiança do usuário.

**No projeto:**
- **Sondas** `/health/live` + `/health/ready` (`HealthRoutes.kt:14,17`) — liveness vs readiness separados
  (readiness checa o banco → tira do balanceador sem matar o pod).
- **Métricas** Prometheus/`/metrics` + Grafana (`Metrics.kt`), latência medida por `timed { }` (`Timed.kt`),
  budgets **p95/p99** no k6 (GAP-DE, `/load-testing`).
- **Fail-fast > espera:** o circuit breaker rejeita imediatamente em vez de aguardar o timeout do pool — preserva
  responsividade sob falha (liga Resilient→Responsive).

**Perguntas de avaliação:**
- Existe um **SLO/budget** de latência para o caminho tocado, ou "rápido" é aspiracional? A regressão seria
  detectada (`perf-regression.yml`, `/load-testing`)?
- Sob falha da dependência, o caminho **degrada rápido** (fail-fast/fallback) ou **pendura** (espera timeout)?
- A readiness reflete a real capacidade de servir (ex.: banco indisponível → `not ready`)?

---

## Rubric: avaliando uma mudança "reativa"

Aplique em ordem — a maioria das ideias para neste funil sem virar arquitetura distribuída:

1. **Qual traço?** Responsive / Resilient / Elastic / Message-Driven. Se nenhum, não é sobre reatividade.
2. **Programming ou System?** Toca só dataflow local/concorrência (coroutine, `Flow`, dispatcher)? → **programming**,
   barato, sem ADR. Introduz comunicação entre processos/serviços, estado distribuído, ou message-passing? →
   **system**, caro, **exige ADR** (e provavelmente `/microservices-modular-monolith`).
3. **É apropriado para um monólito?** Se a proposta é broker/ator/event-bus para desacoplar dois componentes que
   hoje se chamam in-process: **pare**. Você trocaria consistência trivial e location transparency grátis por
   complexidade de sistema distribuído sem um driver real. Só siga se houver uma costura de extração de BC.
4. **Há evidência?** Resiliência e elasticidade são afirmações **verificáveis** — circuit breaker tem teste de
   estado aberto? o limite distribuído tem teste multi-réplica? o p95 tem baseline? Sem evidência, é aspiração.

---

## Anti-padrões (nomear errado esconde a lacuna)

| Afirmação | Por que é enganosa | O nome certo |
|---|---|---|
| "temos arquitetura message-driven" (por causa de `DomainEvent`) | `publish()` é síncrono, in-process, para um sink de métricas | **event broadcast** síncrono; não há message-passing endereçado |
| "o rate limit dá back-pressure" | não há sinal de fila ao upstream; só corta com 429 | **load-shedding / admission control** |
| "somos reativos porque usamos coroutines" | coroutine é reactive **programming** (tempo), não **system** (espaço) | non-blocking/async local — não implica resiliência/elasticidade de sistema |
| "vamos adicionar um broker para ser reativo" | num monólito, introduz complexidade distribuída sem driver | reactive **system** só quando há costura de extração (ADR + `/microservices-modular-monolith`) |
| "circuit breaker = self-healing" | ele isola/rejeita; não recupera o componente sozinho | **bulkhead/isolamento**; self-healing exige supervisão (que não temos) |

---

## Quando usar este skill

- Ao avaliar um PR/ADR que mexe em **resiliência** (timeouts, retries, circuit breakers, fallback),
  **escalabilidade** (estado, cache, réplicas, rate-limit) ou **latência**.
- Ao ver uma proposta rotulada de "reativa" — para classificá-la (programming vs system) e cobrar evidência.
- Ao considerar **message bus / ator / broker** — para checar se o driver justifica sair do monólito.
- Ao redigir um ADR de **extração de bounded context** (é a transição monólito → reactive system).

## Relação com outros skills

| Este skill | Complementa |
|---|---|
| Traço Message-Driven / caminho para sistema distribuído | `/microservices-modular-monolith` — costuras de extração, `docs/context-map.md` |
| Traço Responsive (evidência) | `/opentelemetry` (métricas/traces/health), `/load-testing` (p95/p99, baselines) |
| Traço Elastic (infra) | `/local-and-production-environment` (HPA/PDB/spread), `/graalvm` (startup = scale-up) |
| Traço Resilient (falha-como-valor) | `/fp-oo-kotlin` (Either/Raise), `/owasp` (fail-closed, A10) |
| Isolamento / desacoplamento | `/clean-architecture`, `/circular-dependency-control` |

## Checklist de avaliação

- [ ] Classifiquei a mudança em **um** dos quatro traços (ou concluí que não é sobre reatividade)?
- [ ] Decidi se é **programming** (local, barata) ou **system** (distribuída, ADR)?
- [ ] Se propõe message-driven/broker/ator: há **costura de extração** real, ou é cargo-cult num monólito?
- [ ] Dependência externa nova está **isolada** (circuit breaker/timeout) e **fail-closed**?
- [ ] Falha é **`Either`/valor tipado**, não exceção que vaza?
- [ ] A mudança preserva **statelessness** (nada quebra sob N réplicas)?
- [ ] Chamei back-pressure/message-driven/self-healing pelo nome **correto** (não inflei garantias)?
- [ ] Resiliência/elasticidade afirmadas têm **evidência** (teste de circuito aberto, teste multi-réplica, baseline p95)?

## Referências

- The Reactive Manifesto — https://www.reactivemanifesto.org (Responsive · Resilient · Elastic · Message-Driven; glossário: back-pressure, location transparency, isolation)
- Reactive Programming vs. Reactive Systems (Akka) — https://akka.io/blog/reactive-programming-versus-reactive-systems
- Realizações/ausências no código: `EventPublisherPort.kt`, `MicrometerEventPublisher.kt`, `CircuitBreakerDataSource.kt`, `PersistenceSupport.kt`, `HealthRoutes.kt`, `k8s/06-hpa.yml`, `k8s/07-pdb.yml`; ADRs 0040/0041/0042; skills `/microservices-modular-monolith`, `/load-testing`, `/opentelemetry`
