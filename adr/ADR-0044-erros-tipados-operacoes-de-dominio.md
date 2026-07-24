---
status: proposed
date: 2026-07-24
decision-makers: "@agnaldo4j"
---

# ADR-0044 — Erros tipados nos métodos de operação do domain layer (adoção do `Either`)

> Hoje **todo** o domain layer sinaliza falha **lançando** (`require`/`error`); nenhum agregado retorna
> `Either`. A auditoria `/fp-oo-kotlin` flagou um `error()` no `Board.addCard`, mas o padrão real do domínio
> é o throw. Esta ADR decide **se e como** o domínio passa a expressar falhas de regra de negócio como
> **erros tipados** (`Either<KanbanError, T>`), sem virar a chave onde throw é o certo (precondições).

## Context and Problem Statement

O projeto usa `Either`/`Raise` (Arrow-kt) para erros a partir da camada `usecases` (repositórios, casos de
uso, rotas). Mas a **camada de domínio** — os três módulos `domain-common`/`domain-kanban`/`domain-simulation`
(ADR-0038) — sinaliza **toda** falha lançando exceção:

- invariantes de construtor/`init` (`require(name.isNotBlank())`, `require(wipLimit > 0)`, …);
- e também **regras/lookups de operação**: `Board.addCard` faz `?: error("Step … not found")`; `Board.addStep`
  faz `require(steps.none { it.name == name })` (nome de step duplicado); `Step`/`Card` têm checagens análogas.

Um levantamento completo (categoria (B) = método de operação que falha por **regra ou lookup de domínio**,
distinto de (A) = precondição de construtor/argumento) encontrou **exatamente 6 sites, todos em
`domain-kanban`**; `domain-common` e `domain-simulation` têm **zero** (suas exceções são só invariantes de
construção). Notável: `domain-simulation` **declara** `arrow-core` mas **não o usa** em produção.

A regra "sem `throw` em `domain/`" do skill `/fp-oo-kotlin` é, portanto, **aspiracional** — não descreve o
design atual, em que o domínio lança por decisão. A pergunta a decidir: **o domínio deve expressar falhas de
regra de negócio como erros tipados (`Either`), alinhando-se à convenção do resto do projeto — ou o throw no
domínio é intencional e a regra do skill é que deve ser corrigida?**

Contexto material que pesa na decisão: o subsistema Board Management **não está wired** (não há repositório/caso
de uso/rota — ADR-0038/GAP-BF); e dos 6 sites, **2 têm caller de produção** (`SimulationEngine`), mas ambos são
**pré-guardados** ali — a exceção é estaticamente inalcançável a partir do engine.

## Decision Drivers

- **Consistência do modelo de erro** — `Either<DomainError, T>` já é a espinha do tratamento de erro do
  projeto; ter o domínio lançando e o resto tipando é uma costura inconsistente.
- **Falhas explícitas e verificadas pelo compilador** — uma falha de regra de negócio na assinatura (`Either`)
  não pode ser esquecida pelo chamador; um `error()`/`require` é invisível no tipo.
- **Pureza do domínio preservada** — Arrow-kt é FP pura e **permitido** pelo `DomainPurityTest` (a lista
  proibida é ktor/koin/exposed/sql/slf4j/serialization/micrometer, não Arrow).
- **Não reintroduzir `throw`** — a conversão não pode fazer o `SimulationEngine` (produção) voltar a lançar.
- **Distinguir regra de negócio de erro de programação** — nem todo throw deve virar `Either`: uma precondição
  de construtor (`require(name.isNotBlank())`) sinaliza **bug do chamador**, não uma falha de domínio
  recuperável.

## Considered Options

1. **Manter o throw no domínio** e corrigir a regra do `/fp-oo-kotlin` para reconhecê-lo como intencional.
2. **Erros tipados só no `domain-kanban`** (o módulo flagado).
3. **Erros tipados como princípio do projeto todo** (transparência referencial + funções puras + `Either`/`Raise`
   no lugar de `throw` para falhas de domínio/controle), aplicado aos métodos de operação que falham por
   regra/lookup — na prática, os 6 rule-checks do `domain-kanban`; precondições de construção/argumento ficam
   `require` (fail-fast em bug, único guard através do `copy()`).
4. **Conversão total**, incluindo mover invariantes de construtor para factories `create(): Either<…>`.

## Decision Outcome

**Escolhida: Opção 3.** A decisão eleva-se a um **princípio do projeto todo** — a virtude de programação
funcional que Kotlin e este projeto já cultivam (transparência referencial, funções puras, controle forte de
efeitos): **preferir erros tipados (`Either`/`Raise`) a cortar a execução com `throw`** para falhas de
domínio/negócio e controle de fluxo. As camadas `usecases`/`sql_persistence`/`http_api` já seguem isso; o
**domain layer era a última exceção** que ainda lançava — é o que esta ADR converte.

> **Falha de domínio/regra → tipo.** Um método de **operação** de agregado/entidade que falha por **regra de
> negócio ou lookup de domínio** retorna `Either<DomainError, T>` e sinaliza com `raise(...)` (dentro de
> `either { }`), usando a hierarquia de erro do **próprio bounded context** — `CommonError`/`KanbanError`/
> `SimulationError` (ADR-0038); `domain-common` não pode referenciar `KanbanError` sem inverter o grafo
> `common → kanban`, então cada agregado levanta o `*Error` do seu contexto (todos são `DomainError`). Uma vez
> que o método retorna `Either`, **todas** as suas falhas viram `raise` — não se mistura `throw` com `Either`.
>
> **Precondição de contrato → `require`.** Invariantes de construção/factory e guards de argumento puros
> (blank, não-negativos) **permanecem `require`/`check`** — são **fail-fast em bug do chamador**, não falha de
> domínio recuperável, e são o **único** mecanismo que valida invariante através do `copy()` sintético de uma
> `data class` (removê-los deixaria o `copy()` construir objetos inválidos; value classes e serialização
> dependem igualmente do construtor). Input externo já é validado com `Either` no boundary do `usecases`.

Aplicação: `domain-kanban` ganha `arrow-core`; os **6 rule-checks em 5 métodos** de operação viram
`Either<KanbanError, T>` (`Board.addStep`/`addCard`, `Step.assignWorker` — **2 checks: elegibilidade + já-atribuído**,
`Step.executeCard`, `Card.block`), com novas variantes em `KanbanError` para as regras que ainda não têm
(duplicidade de step, elegibilidade/atribuição de worker, estado de card); `Board.addCard` reusa o `StepNotFound`
existente. (Aqui o contexto é o Kanban Management BC, logo o erro é `KanbanError`.) Os 2 call sites de produção no `SimulationEngine`
já pré-guardam a condição, então **desembrulham o `Right`** sem reintroduzir `throw`, mantendo o engine total.
`domain-common`/`domain-simulation` **adotam o princípio sem mudança de código** (não têm sites de categoria B).

Rejeitadas: **(1)/(2)** perpetuam ou criam inconsistência; **(4)** converter invariantes de construtor em
factories `Either` é enorme e semanticamente errado (blank name é bug do chamador, não erro de domínio).

### Confirmation

- Fitness/gate: `DomainPurityTest` permanece **verde** com Arrow no `domain-kanban` (Arrow não está na lista
  proibida); `ContextBoundaryTest`, `PackageCycleTest` e `DiWiringCycleTest` inalterados.
- Revisão de código (invariante da decisão): **nenhum `error()` nem `require`-de-regra-de-negócio** sobra num
  método de operação de agregado — só `require` de precondição de construtor/argumento. (Candidato a virar uma
  fitness function Konsist num gap futuro; por ora, verificação em review.)
- `SimulationEngine` permanece **total** (sem `throw`); `./gradlew testAll` verde e o comportamento do
  `runDay` é idêntico (os `Left` de `executeCard`/`block` nunca disparam por serem pré-guardados).

## Consequences

- **Bom:** o domínio passa a expressar falhas de regra de negócio de forma explícita e verificada pelo
  compilador, alinhado ao `Either` do resto do projeto; quando Board Management for wired, os casos de uso
  recebem `KanbanError` tipado direto do agregado, sem traduzir exceção.
- **Bom:** o princípio fica documentado para os três módulos — futuras operações de domínio nascem tipadas.
- **Ruim (honesto):** o **valor prático hoje é baixo** — Board Management é unwired e os 2 sites de produção são
  estaticamente inalcançáveis (pré-guardados). O custo imediato é **ripple em ~28 arquivos de teste** (os
  happy-paths desembrulham o `Right`; os `assertFailsWith` viram asserção sobre `Left`) e a adição de `arrow-core`
  ao `domain-kanban` (revertendo a stance "sem Arrow" do módulo). Mitigação: a implementação pode ser dividida
  em PRs focados (≤ 400 linhas); nenhum comportamento de runtime muda.
- **Ruim:** `domain-kanban` ganha uma dependência de biblioteca antes ausente — aceito, pois é FP pura e já é
  precedente em `domain-simulation` (que a declara).

## Pros and Cons of the Options

### Opção 1 — Manter throw + corrigir a skill
- Bom: custo zero de código; reconhece o design real.
- Ruim: mantém a costura inconsistente (domínio lança, resto tipa); não foi a direção escolhida pelo mantenedor.

### Opção 2 — Só `domain-kanban`
- Bom: escopo menor.
- Ruim: torna o `domain-kanban` o **único** módulo de domínio a retornar `Either` — cria a inconsistência que a
  própria motivação queria remover.

### Opção 3 — Princípio domain-wide (escolhida)
- Bom: consistência, falhas explícitas, base para o wiring futuro; distingue regra de negócio de precondição.
- Ruim: ripple de teste amplo para um subsistema unwired; adiciona Arrow ao `domain-kanban`.

### Opção 4 — Conversão total (incl. construtores → factories `Either`)
- Bom: pureza máxima ("nada lança").
- Ruim: enorme; converte erros de programação (blank/negativo) em erros de domínio — semanticamente errado;
  quebra a ergonomia de construção de todas as entidades.

## More Information

- Branch: `feat/adr-0044-erros-tipados-dominio` · PR: https://github.com/agnaldo4j/kanban-vision-api-kt/pull/348
- Item no board #6: **GAP-DN** (o plano de implementação vive lá e no PR, não aqui — ADR-0023)
- Relacionadas: ADR-0038 (split do domínio por bounded context), ADR-0034 (value-class IDs), ADR-0023 (política
  de ADRs). Origem: auditoria `/fp-oo-kotlin` (2026-07-24) e a discussão que reclassificou GAP-DG#1 → GAP-DN.
- Emenda de skill decorrente (no PR de implementação): a regra "sem `throw` em `domain/`" do `/fp-oo-kotlin`
  vira um **princípio do projeto todo** — transparência referencial + funções puras + `Either`/`Raise` no lugar
  de `throw` para falhas de **domínio/negócio e controle de fluxo**, em todas as camadas — distinguindo isso de
  **precondição de construção/argumento (→ `require`/`check`)**, que fica como fail-fast em bug do chamador (e é
  o único guard através do `copy()` de `data class`).
