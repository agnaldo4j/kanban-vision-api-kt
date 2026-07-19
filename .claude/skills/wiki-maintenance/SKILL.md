---
name: wiki-maintenance
description: >
  Ponto de entrada único para atualizar o GitHub wiki deste projeto (repo separado). Use sempre que
  uma mudança precise ser refletida na documentação do wiki — nova rota, métrica, gate, módulo Gradle,
  ADR aceita, entidade de domínio, ou re-score do scorecard. Centraliza a mecânica (clone, house style,
  commit `docs(wiki): …`, o mantenedor faz o push) e o mapa página↔skill, delegando os diagramas ao
  /c4-model e o conteúdo de cada página à sua skill de tópico. Complementa /c4-model e todas as skills
  donas de página.
argument-hint: "[página ou mudança a refletir no wiki (opcional)]"
allowed-tools: Read, Grep, Glob, Edit
---

# Wiki Maintenance — atualização do GitHub wiki

> **Regra de ouro:** o PR que muda o comportamento/estrutura do projeto **atualiza a página do wiki
> correspondente no mesmo change**. Sem a página atualizada, o PR está **incompleto**
> (ver skill `/definition-of-done`). Esta skill diz *onde* editar, *como* editar e *qual skill é dona*
> do conteúdo de cada página.

Esta skill é o guarda-chuva: **a mecânica** (abaixo) e o **mapa página↔skill** vivem aqui; o *conteúdo*
de cada página pertence à skill de tópico (ex.: diagramas → `/c4-model`, observabilidade →
`/opentelemetry`). Não reescreva aqui o que a skill de tópico já cobre — delegue.

---

## Mecânica (idêntica para toda página)

- **Onde:** o wiki é um **clone git separado**, irmão do repo principal, em
  `../kanban-vision-api-kt.wiki` (remote `.wiki.git`, mantido manualmente). Não há espelho dentro do
  repo principal. URL pública: `https://github.com/agnaldo4j/kanban-vision-api-kt/wiki/<Page>`.
- **Como editar:** edite os arquivos `.md` no clone; a navegação é o `_Sidebar.md`.
- **Commit:** local, mensagem `docs(wiki): <resumo>`.
- **Push:** **NUNCA faça push do wiki.** Você faz o commit local; **o mantenedor faz o push**. Ao
  terminar, avise o mantenedor (ex.: `git -C ../kanban-vision-api-kt.wiki push`).
  - Se o clone estiver *ahead/behind* (o mantenedor já publicou algo), **não force-push**: replay a sua
    correção como um novo commit em cima de `origin/master` (`git reset --soft origin/master` + commit).
- **Diagramas:** house style Mermaid (`flowchart`/`graph`, `sequenceDiagram`, `classDiagram`,
  `erDiagram`, `stateDiagram-v2`) — **nunca** a sintaxe nativa `C4Context/Container/Component`. **Valide
  cada diagrama** (MCP Mermaid ou Mermaid Live) antes de commitar. Detalhes e o mapa de diagramas: ver
  skill `/c4-model` — não os repita aqui.
- **Fidelidade ao código:** leia o código antes de escrever; não documente componentes/rotas que não
  existem (ex.: Board Management não está wired — não desenhe repos/rotas dele).

---

## Mapa página → skill dona

Antes de editar uma página, invoque a skill de tópico dona do conteúdo. Os **diagramas** de qualquer
página são de `/c4-model` (ver o mapa de diagramas lá).

| Página do wiki | Skill dona do conteúdo | Notas |
|---|---|---|
| `Home` | *(geral — esta skill)* | landing/índice; badges e pitch |
| `Architecture` | `/c4-model` | context L1 + container L2 + mapa de módulos + request-flow |
| `Diagrams` | `/c4-model` | hub que indexa os diagramas por senioridade |
| `Architecture-Domain` | `/ddd` (+ `/c4-model` p/ o classDiagram) | agregados, VOs, `Refs`, erros por contexto |
| `Architecture-Usecases` | `/clean-architecture` (+ `/c4-model`) | CQS, ports, sequência RunDay |
| `Architecture-HTTP-API` | `/openapi-quality` (+ `/c4-model`) | rotas, plugins, DTOs |
| `Architecture-SQL-Persistence` | `/db-migrations` (+ `/c4-model` p/ o ERD) | Exposed DSL, Flyway, schema |
| `Architecture-Fitness-Functions` | `/kotlin-quality-pipeline` + `/circular-dependency-control` | Konsist/JUnit; grafo de deps |
| `API-Reference` | `/openapi-quality` | endpoints, JWT, OpenAPI |
| `Development-Guide` | `/kotlin-quality-pipeline` + `/testing-and-observability` | setup, testes, gates |
| `JVM` | `/graalvm` (JIT) + `/kotlin-quality-pipeline` | JDK único, convention plugin, fat JAR |
| `GraalVM` | `/graalvm` | Native Image, reachability metadata (+ `/c4-model` p/ o pipeline) |
| `Observability` | `/opentelemetry` | logs JSON, Prometheus, traces (+ `/c4-model` p/ event→metric) |
| `Operations` | `/local-and-production-environment` | Docker, k8s, CI/CD (+ `/c4-model` p/ topologia) |
| `Performance-Load-Testing` | `/load-testing` | k6, perfis, baselines (ADR-0027) |
| `Quality-Analysis` | *scorecard* `docs/quality/scorecard-*.md` + `/definition-of-done` | espelha o snapshot in-repo imutável (ADR-0023) |
| `Security-Supply-Chain` | `/owasp` | SBOM/SCA, CVE gate, runbook |

> **Lacuna que esta skill fecha:** as páginas Observability/Operations/GraalVM/API-Reference/
> Security-Supply-Chain têm dono de *tópico*, mas historicamente as skills não apontavam para a página
> do wiki. Ao mexer no tópico, a skill dona deve atualizar a página — a mecânica está aqui.

---

## Fluxo

1. **Identifique a página** pelo mapa acima (pela mudança que você fez).
2. **Invoque a skill de tópico** dona do conteúdo; para diagramas, `/c4-model`.
3. **Edite no clone** `../kanban-vision-api-kt.wiki`, mantendo o `_Sidebar.md` e o hub `Diagrams.md`
   consistentes se adicionou/renomeou página.
4. **Valide** os diagramas Mermaid; confira que a página reflete o código real.
5. **Commit** `docs(wiki): …` local. **Não faça push** — avise o mantenedor.
6. No PR do repo principal, mencione o commit do wiki pendente de push (o PR só está *done* com a
   página atualizada — `/definition-of-done`).

---

## Relação com Outras Skills

| Esta skill | Complementa |
|---|---|
| Mecânica + mapa página↔skill | `/c4-model` — dono de **todos os diagramas** e do mapa de diagramas |
| Regra "PR atualiza o wiki" | `/definition-of-done` — o PR não está *done* sem a página |
| Conteúdo por página | `/opentelemetry`, `/graalvm`, `/local-and-production-environment`, `/load-testing`, `/owasp`, `/openapi-quality`, `/db-migrations`, `/ddd`, `/clean-architecture`, `/kotlin-quality-pipeline`, `/circular-dependency-control` |

## Referências

- Skill: [c4-model](.claude/skills/c4-model/SKILL.md) — diagramas do wiki
- Skill: [definition-of-done](.claude/skills/definition-of-done/SKILL.md)
- Docs: `docs/quality/scorecard-*.md` (fonte da página `Quality-Analysis`, imutável — ADR-0023)
- Wiki: `https://github.com/agnaldo4j/kanban-vision-api-kt/wiki`
