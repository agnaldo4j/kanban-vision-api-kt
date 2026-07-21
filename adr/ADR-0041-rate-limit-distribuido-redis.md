---
status: accepted
date: 2026-07-20
decision-makers: "@agnaldo4j"
---

# ADR-0041 — Rate limit distribuído: um contador compartilhado sob escala horizontal

> **Correção (ADR-0042):** os bullets de *mecanismo* desta ADR foram corrigidos pela **ADR-0042** — o
> limiter atual do Ktor (`DefaultRateLimiter`) é **janela fixa**, não token-bucket, então as alegações
> "equivalente ao atual" e "sem mudança observável" estavam erradas. A decisão de *onde* (Redis) segue
> válida; o *mecanismo* passa a ser um token-bucket verdadeiro (endurecimento intencional).

> O rate limiter conta **na memória de cada pod**. Sob o HPA (2→8 réplicas) e um Service que faz
> round-robin sem afinidade, o teto por IP de um cliente vira `limite × réplicas` — e **cresce
> justamente quando o sistema está sob carga**, que é quando o limite deveria valer. Esta ADR decide
> **onde** o contador passa a viver para ser exato na frota, sem roubar do invariante de conexões da
> ADR-0037 nem tirar o controle de dentro do código.

## Context and Problem Statement

`http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/RateLimit.kt` instala o plugin `RateLimit`
do Ktor com dois limitadores por IP do cliente: **100 req/min global** (`RateLimit.kt:22`) e **5
req/min em `/auth`** (`RateLimit.kt:26`, aplicado em `AuthRoutes.kt`). A chave é o IP real do cliente,
derivado da cadeia `X-Forwarded-For` via `TRUSTED_PROXY_COUNT` (`clientRateLimitKey`, resistente a
spoofing). O contador é o token-bucket **in-process** padrão do Ktor — **em memória, por JVM**. Uma
varredura confirma **zero** store compartilhado: não há Redis, memcached ou qualquer backend
distribuído no repo.

Ao mesmo tempo:

- **O Service não tem afinidade.** `k8s/04-service.yml` é `ClusterIP` com `sessionAffinity: None`
  (default) ⇒ o kube-proxy balanceia **cada request** entre todos os pods; requisições do mesmo cliente
  caem em pods diferentes.
- **O HPA escala de 2 a 8 réplicas** (`k8s/06-hpa.yml:13,28`) por CPU.

Consequência: cada pod só enxerga **sua fração** do tráfego de um cliente. O teto efetivo por IP é
`100 × réplicas_vivas` — **até 800 req/min** com o HPA no máximo. Pior: o limite **afrouxa sob carga**,
porque é a carga que faz o HPA subir réplicas. O limitador entrega a proteção mais fraca exatamente no
momento em que ela mais importa.

É a **mesma classe de defeito** que a ADR-0037 registrou para o alerta `HikariPoolExhaustion` (mede
`active/max` **por instância** ⇒ estruturalmente cego para a saturação de frota) e que a ADR-0040
nomeou para o placement. Um controle por-instância num sistema que escala horizontalmente não é o
controle que o operador pensa ter.

Como nas ADR-0037 e 0040, **não há cluster real neste repositório** — `k8s/01-configmap.yml` aponta
para um `postgres-svc` cujo StatefulSet nem existe. Os manifestos são **referência**. Esta ADR decide,
então, por **coerência de um controle correto-por-default**, e diz isso.

**Pergunta:** como tornar o limite por IP **exato sob escala horizontal**, mantendo o controle **dentro
do código** (versionado, unit-testado por `RateLimitForwardedForTest`, exposto em `/metrics`) e **sem
roubar do invariante de conexões Postgres** da ADR-0037?

## Decision Drivers

- **O controle vive no código.** O limite é uma regra de segurança (OWASP A07): deve ser versionada,
  unit-testada e observável pelo `/metrics` do próprio app — não escondida em anotações de infra que o
  repo não testa nem enxerga.
- **Exatidão na frota.** O teto por IP precisa ser o mesmo com 2 ou 8 pods; caso contrário o número
  configurado é ficção.
- **Não tocar o invariante Postgres.** A ADR-0037 fixou `maxReplicas × poolSize ≤ max_connections` com
  **margem apertada** (`8×10 + 2 = 82` contra ~95 utilizáveis). Qualquer contador que consuma conexões
  Postgres estreita ainda mais essa folga.
- **Degradar, nunca cair.** Um contador remoto vira dependência no caminho quente. A falha dele não pode
  virar outage — nem barra-livre.
- **Manifestos são referência** (ADR-0037/0040). O Postgres já é um serviço **externo** referenciado, não
  definido; a mesma disciplina vale para um novo backend stateful.

## Considered Options

1. **Status quo — token-bucket in-memory por pod.** Rejeitada: é o defeito que esta ADR existe para
   corrigir. O limite dilui de 2× a 8× com o HPA.
2. **Enforcement na borda (anotações do nginx Ingress).** `nginx.ingress.kubernetes.io/limit-rpm` no
   `k8s/05-ingress.yml`. Rejeitada: (a) só **~exato** — dilui pela contagem de réplicas do próprio
   ingress-controller; (b) acopla o limite ao nginx e a um único choke-point coordenado; (c) **move o
   controle para fora do código** — o repo deixa de testá-lo (`RateLimitForwardedForTest`) e de
   observá-lo (`/metrics`), e o limite de `/auth` (por-rota) fica desajeitado em anotação. Enforcement
   de borda é defesa em profundidade **complementar** (WAF/CDN do operador), não o lugar do controle
   versionado.
3. **Contador em Postgres.** Rejeitada: colide de frente com o invariante da ADR-0037. Com a folga já em
   ~13 conexões, um contador transacional por request estreita a margem e reacopla o rate limit — um
   caminho de altíssima frequência — ao banco de dados de negócio.
4. **Limiter distribuído em Redis, via o seam `RateLimiter` do Ktor.** **(escolhida.)** Um contador
   atômico **compartilhado** por todos os pods torna o limite exato na camada de app; o Redis é um
   **orçamento de conexão separado** (não toca o Postgres); e o Ktor 3.5.1 expõe um seam público que
   torna a mudança **localizada**, sem fork nem plugin caseiro.

## Decision Outcome

**Opção 4.** A implementação é PR(s) de follow-up — esta ADR é o gate `[E]`. O que fica **fixado**:

- **Seam do Ktor, não um fork.** O `ktor-server-rate-limit-jvm:3.5.1` expõe a interface pública
  `RateLimiter { suspend fun tryConsume(tokens): State }` e a overload
  `rateLimiter(provider: (call, key) -> RateLimiter)`. A implementação troca as chamadas
  `rateLimiter(limit=, refillPeriod=)` (`RateLimit.kt:22,26`) pela overload de provider, retornando um
  `RedisRateLimiter : RateLimiter`. A `key` recebida **já é** o `clientRateLimitKey` atual; global e
  `/auth` usam **namespaces distintos** de chave Redis. Os headers `X-RateLimit-*`/`Retry-After` saem de
  graça do `State` retornado — nenhum comportamento observável do cliente muda.
- **Contador atômico e equivalente a token-bucket.** O bucket vive numa operação **atômica** no Redis —
  um **script Lua de token-bucket** (ou GCRA/janela deslizante equivalente). Atomicidade é obrigatória:
  sem ela, N pods lendo-e-escrevendo em corrida reintroduzem a diluição por outra porta. **`INCR`+`EXPIRE`
  de janela fixa é rejeitado**: não é equivalente ao limiter atual (token-bucket) — admite **2× a cota na
  virada da janela** (ex.: em `/auth`, 5 requests logo antes do expiry + 5 logo depois, o que o bucket de
  hoje não permite no mesmo burst) e diverge os headers `remaining`/`reset`. Como a decisão promete
  **comportamento observável inalterado** (bullet anterior), a semântica **tem de ser token-bucket**.
- **Client: Lettuce.** Async e coroutine-friendly (casa com o `suspend fun tryConsume`), sobre Netty —
  que **já é transitivo** no projeto (`io.netty:*:4.2.15.Final`, via `ktor-server-netty`). ⚠️ Lettuce
  historicamente mira Netty **4.1.x**; a PR de dependência **valida o alinhamento** com o 4.2.15.Final
  (risco de conflito de versão / SBOM-drift) antes de fixar a versão.
- **Resiliência — degrada para um limiter local semeado (o ponto crítico).** Em erro ou timeout do
  Redis, o `RedisRateLimiter` **cai para um token-bucket in-memory local** (timeout curto +
  circuit-breaker, no idioma do `DbCircuitBreaker` do `sql_persistence`, para não martelar um Redis morto
  a cada request). ⚠️ **O fallback NÃO pode ser um bucket vazio (cheio de tokens).** Cada `State.Available`
  do Redis carrega `remainingTokens`; o pod **memoriza o último `remainingTokens` observado por chave** e,
  ao entrar em fallback, **semeia o bucket local com esse valor** (init conservador). Sem isso, somam-se
  a cota remota **e** a local: um cliente que esgotou o bucket compartilhado (100 já admitidos) ganharia
  até +100 por pod × 8 pods = **+800**, totalizando ~900/min — **pior que o limite compartilhado e que os
  800/min de hoje**. Com o seeding, a cota remota e a local **não se somam**: o modo degradado converge ao
  **limite por-pod** (o comportamento de hoje), sem conceder uma janela local nova na transição. Não é
  *fail-open ilimitado* nem *fail-closed→outage*: reconcilia com a `security.md §6` (fail-closed) — o
  controle **continua limitando**, apenas mais fraco, e **jamais abre para ilimitado**; o app segue
  servindo. A equivalência tem de ser **coberta por teste** (esgotar via Redis → derrubar o Redis →
  assertar que o bucket local não readmite uma janela cheia).
- **Invariante paralelo (à la ADR-0037): `maxReplicas × redisPoolSize ≤ redis maxclients`**, com folga
  para manutenção. Como a ADR-0037 fez com o Postgres, esta ADR fixa **a regra e a responsabilidade** —
  quem provisionar o Redis satisfaz o invariante — e **não finge** conhecer o `maxclients` de produção
  (inverificável deste repo). O ponto que motiva a escolha: este orçamento é **disjunto** do pool
  Hikari ⇒ **não estreita** a margem de `max_connections` da ADR-0037.
- **Config graceful: `RATE_LIMIT_REDIS_URL`.** Ausente ⇒ **fallback para o limiter in-memory** (dev
  local, testes, e o `docker-compose` single-instance, onde um contador compartilhado é desnecessário).
  Preserva `RateLimitForwardedForTest` **sem exigir Redis** e mantém o boot local trivial. O limite
  numérico e a janela seguem as constantes atuais (a env controla só o *store*, não os números).
- **Infra = referência, no idioma do Postgres.** O `k8s/` referencia um `redis-svc` **externo** (não
  define StatefulSet — igual ao `postgres-svc`); o `docker-compose.yml` sobe um Redis real para
  prod-parity. Segurança: auth/ACL do Redis + TLS em produção, **por env, sem segredo hardcoded**
  (`security.md §1`).

## Consequences

- **Bom:** o teto por IP passa a ser **exato na frota** — o mesmo com 2 ou 8 pods —, e a proteção deixa
  de afrouxar sob carga. O controle **continua no código**: testável, versionado e observável pelo
  `/metrics`, em vez de migrar para anotações de infra. E o Postgres fica **intocado** — a escolha do
  Redis é precisamente o que preserva o invariante zero-margem da ADR-0037.
- **Trade-off (aceito):**
  - **Novo componente stateful + novo modo de falha.** *Mitigação:* o degrade-to-local **semeado pelo
    último `remainingTokens`** converge ao limite por-pod de hoje sem readmitir uma janela cheia na
    transição (não soma cota remota + local); o app não depende do Redis para servir.
  - **Superfície nativa nova.** O Lettuce adiciona reachability metadata ao binário Native Image
    (ADR-0032); o Netty já está na superfície, então o custo incremental é dos codecs do Lettuce. *A PR
    de infra cura a metadata e exercita o caminho no smoke nativo.*
  - **+1 hop de latência no caminho quente.** *Mitigação:* Redis é sub-ms na mesma rede; o timeout curto
    limita o pior caso, e o degrade-to-local o absorve.
  - **Nova dependência passa pelos gates.** SBOM CycloneDX + osv-scanner (job `supply-chain`, ADR-0025)
    e o smoke nativo. *É o gate funcionando, não churn.*
- **Critério de reavaliação:** se a necessidade crescer para limites **multi-dimensionais** (por
  organização + por rota + por método) ou coordenação de estado além de rate limit, reavaliar o desenho
  do contador. Se o Netty exigido pelo Lettuce divergir de forma inconciliável do usado pelo Ktor,
  reavaliar o client (ex.: um client Redis sem Netty). Mudança nos números do limite continua sendo
  decisão de código, não de ADR.

## Related

- **GAP-BZ** (board #6) — origem; a execução (app, infra, observabilidade) é o follow-up desta ADR.
- **ADR-0037** — envelope de recursos do pod: fonte do invariante de conexões (`maxReplicas × poolSize`),
  do framing "manifestos são referência", e do padrão de fixar *regra + responsabilidade* sem inventar
  números de produção. Esta ADR espelha esse padrão para o Redis.
- **ADR-0040** — spread de réplicas: nomeou a cegueira frota × por-instância no placement; aqui é a mesma
  cegueira no contador de rate limit.
- **ADR-0025** — SBOM/SCA: a nova dependência (Lettuce) entra pelo gate `supply-chain`.
- **ADR-0032** — Native Image em produção: o Lettuce amplia a superfície de reachability metadata.
- **ADR-0023** — política de ADRs (imutabilidade, supersessão, MADR 4.0).
- Docs — [Ktor RateLimit](https://ktor.io/docs/server-rate-limit.html) · [Lettuce](https://redis.github.io/lettuce/).
