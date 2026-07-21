---
status: accepted
date: 2026-07-20
decision-makers: "@agnaldo4j"
supersedes: "ADR-0041 (apenas os bullets de mecanismo)"
---

# ADR-0042 — Token bucket verdadeiro: corrigindo o mecanismo do rate limit

> A ADR-0041 decidiu **onde** o rate limit passa a contar (Redis compartilhado) e, de passagem,
> afirmou que o limiter atual do Ktor "é token-bucket" e que trocar o store "não muda nada observável".
> Ao implementar, o fonte do Ktor 3.5.1 refutou as duas: o `DefaultRateLimiter` é uma **janela fixa**.
> Esta ADR corrige o registro e decide o mecanismo — um **token-bucket verdadeiro**, um endurecimento
> pequeno e intencional. A decisão de *onde* (Redis) da ADR-0041 fica **inalterada**.

## Context and Problem Statement

A ADR-0041 (`Decision Outcome`) fixou duas afirmações sobre o mecanismo:

1. *"o limiter atual … é token-bucket"* e
2. *"nenhum comportamento observável do cliente muda"* ao migrar o store.

Ambas vieram de um P2 do Codex/harness cuja **premissa era falsa**. O `DefaultRateLimiter` do
`ktor-server-rate-limit:3.5.1` (verificado no fonte do jar) é um **contador de janela fixa**:

```kotlin
private fun refillIfNeeded() {
    if (timeToWaitMillis() > 0) return
    tokens.value = limit                 // reset DURO — nada de refill proporcional
    lastRefillTimeMillis = clock()
}
```

Consequências factuais:

- **Ele já admite o *boundary burst*.** Um cliente pode gastar 5 tokens em `t = 00:59` e mais 5 em
  `t = 01:01` — **10 em ~2s** — porque a virada da janela faz um reset total. Isto é exatamente o que o
  P2 atribuiu, erradamente, só ao `INCR`+`EXPIRE`. O `/auth` (5/min) tem esse buraco **hoje**.
- **`DefaultRateLimiter` é `internal` e seu `initialSize` é ignorado** (`@Suppress("UNUSED_PARAMETER")`,
  "will be removed"), então ele **não pode** semear o bucket de fallback que a ADR-0041 exige — um
  limiter próprio é necessário de qualquer forma.

Logo, implementar "equivalente ao atual" seria replicar uma janela fixa **com** o boundary burst.
Implementar um token-bucket verdadeiro (refill contínuo) **remove** esse burst — é **mais estrito** que
hoje, não idêntico. A promessa "sem mudança observável" da ADR-0041 é, portanto, insustentável: alguma
mudança há de existir, e a pergunta é **qual**.

**Pergunta:** qual mecanismo o rate limit deve usar — replicar a janela fixa atual (com o burst), ou
adotar um token-bucket verdadeiro que o elimina?

## Decision Drivers

- **Corretude do registro.** Uma ADR aceita não pode carregar uma afirmação factualmente errada sobre o
  código; a correção é obrigatória, e ADRs são imutáveis ⇒ nova ADR (ADR-0023).
- **Segurança do `/auth`** (OWASP A07 — brute-force). O boundary burst dobra a taxa efetiva na virada da
  janela justamente na rota de autenticação. Fechá-lo é um ganho de segurança real e barato.
- **Menor surpresa a longo prazo.** Um token-bucket verdadeiro é o que a maioria das pessoas *acha* que
  um rate limit faz; a janela fixa é a pegadinha.
- **O bucket seedável já é requisito** (fallback semeado da ADR-0041) ⇒ escrever um limiter próprio não é
  custo novo; a escolha do algoritmo dele é de graça.

## Considered Options

1. **Replicar a janela fixa do `DefaultRateLimiter`** (com o boundary burst) para honrar "sem mudança
   observável". Rejeitada: preserva um defeito de segurança conhecido no `/auth`, e "sem mudança" já é
   falso na prática (o store distribuído muda o timing de qualquer modo).
2. **Token-bucket verdadeiro (refill contínuo).** **(escolhida.)** Remove o boundary burst; endurece o
   `/auth`; alinha o comportamento à expectativa comum. Mudança observável **intencional e documentada**.
3. **Adiar e só corrigir o texto da ADR-0041.** Rejeitada: deixaria o mecanismo indefinido bem no ponto
   que o P2 (equivocado) tentou fixar; a decisão de mecanismo precisa existir.

## Decision Outcome

**Opção 2.** O rate limit passa a usar um **token-bucket verdadeiro** — refill **contínuo**
(`tokens = min(cap, tokens + elapsed × cap / period)`), sem reset duro. Fixado:

- **Substitui o `DefaultRateLimiter`** por um `LocalTokenBucketRateLimiter` próprio (in-memory,
  `AtomicReference` + CAS, `clock` injetável, **seedável**), plugado pela overload de provider do Ktor.
  O `State.Available(remainingTokens, limit, refillAtTimeMillis)` retorna `refillAtTimeMillis` **absoluto
  em epoch-ms** (o plugin faz `ceil(/1000)` para o header `X-RateLimit-Reset`).
- **Endurecimento intencional:** o `/auth` (5/min) deixa de permitir o burst de 5+5 na virada. Esta é a
  **mudança observável** que a ADR-0041 negava; aqui ela é assumida como **melhoria**, não regressão.
- **Corrige a ADR-0041:** superseão **escopada aos bullets de mecanismo** ("equivalente a token-bucket",
  "nenhum comportamento observável muda", "o atual é token-bucket"). A decisão de *onde* (Redis,
  orçamento disjunto do Postgres, fallback semeado) permanece **integralmente válida**.
- **Entrega faseada** (a ADR-0041 já foi o gate `[E]`; esta refina o mecanismo de uma decisão já aceita):
  - **Passo 1 (este PR):** o token-bucket verdadeiro **in-memory** + o seam plugável + esta ADR + testes.
    Sem dependência nova, sem risco de build nativo — já entrega o endurecimento do `/auth`.
  - **Passo 2 (follow-up):** o backing **distribuído em Redis** (Lettuce + Lua de token-bucket +
    circuit-breaker + fallback semeado + metadata nativa + smoke com Redis), atrás do mesmo seam.

### Confirmation

O comportamento é coberto por teste de unidade do `LocalTokenBucketRateLimiter` (permite `limit`, depois
`Exhausted`; refill contínuo via `clock` injetável; seed parcial → sem readmissão de janela cheia) e por
teste de integração (`testApplication`: 200 → 429/406, headers `X-RateLimit-*`). O gate contínuo é o
`testAll` (JaCoCo ≥98%, Detekt 0). O boundary burst **não** deve reaparecer: um teste que consome na
virada da janela e assere ausência do 2× é a trava.

## Consequences

- **Bom:** o registro fica correto; o `/auth` deixa de ter o buraco de burst na virada (OWASP A07); e o
  mecanismo passa a ser o que a intuição espera. O seam in-memory já é útil por si só e destrava o Redis.
- **Trade-off (aceito) — é uma mudança de comportamento observável.** Clientes que dependiam
  (conscientemente ou não) do burst de virada verão `429` um pouco mais cedo nesses instantes. É
  **intencional** e restrito à borda da janela; o teto por minuto é o mesmo. Documentado aqui para que
  ninguém trate como regressão.
- **Trade-off — terceira correção da linha do rate limit** (0041 aberta → P1/P2 corrigidos → 0042). *Não
  é churn:* cada passo foi disparado por uma verificação da anterior (o P2 do review, e depois o fonte do
  Ktor refutando o P2). É o gate funcionando.
- **Critério de reavaliação:** se algum cliente legítimo quebrar com o fim do burst, reavaliar o tamanho
  da janela/limite (decisão de número, não de mecanismo) — nunca reintroduzir a janela fixa.

## Related

- **ADR-0041** — superseão **parcial** (só o mecanismo); a decisão de *onde* (Redis) segue válida.
- **GAP-BZ** (board #6) — origem; esta ADR + o Passo 1 saem no mesmo PR, o Passo 2 é follow-up.
- **ADR-0023** — política de ADRs (imutabilidade ⇒ correção via nova ADR; superseação).
- Fonte — `io.ktor:ktor-server-rate-limit:3.5.1`, `DefaultRateLimiter.kt` (janela fixa) e
  `RateLimiter.default(initialSize ignorado)`.
