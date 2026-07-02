---
status: accepted
date: 2026-07-02
decision-makers: "@agnaldo4j"
supersedes: ADR-0004
---

# ADR-0023 — Política de ADRs: imutabilidade, MADR 4.0 e separação decisão/planejamento/medição

## Context and Problem Statement

A ADR-0004 ("Avaliação de Qualidade: Nota, Gaps e Prioridades") acumulou 557 linhas e 28 commits —
a maioria `docs(adr): mark GAP-XX as complete`. Ela contém backlog com 34 checkboxes, scores por
dimensão, ordem de execução com marcações de progresso, tabela de status de outras ADRs e 5
revisões de replanejamento. Isso viola anti-patterns documentados na literatura ("ADR como
changelog/backlog", "múltiplas decisões numa ADR") e a própria skill `/adr` do projeto. A causa
raiz é de política: `docs/politicas-explicitas.md` e `.claude/rules/workflow.md` prescreviam
"marcar gap [x] na ADR-0004" a cada fechamento, criando rastreamento triplo redundante
(board #6 + checkboxes na ADR + arquivo de memória).

Como devem ser escritas, aprovadas e mantidas as ADRs deste projeto para que registrem decisões —
e apenas decisões?

## Decision Drivers

- ADRs devem preservar o **porquê** de decisões caras de reverter; editar continuamente destrói o histórico.
- O progresso de trabalho já tem fonte de verdade (GitHub Project #6, WIP 1); duplicá-lo em ADRs gera divergência (a "ordem de execução" da ADR-0004 já divergia do `workflow.md`).
- Medição de qualidade deve ser regenerável (CI) ou snapshot datado, nunca tabela editada à mão.
- Formato deve ter campo explícito de verificação da decisão (ponte para fitness functions).

## Considered Options

1. **Imutabilidade estrita + template MADR 4.0 + separação de camadas** (esta ADR).
2. Imutabilidade estrita mantendo o template atual do projeto.
3. Regra mínima: apenas proibir backlog/scores em ADRs, mantendo edição livre no resto.

## Decision Outcome

Opção 1. A partir desta ADR:

1. **Imutabilidade estrita.** ADR com status `accepted` nunca tem seu conteúdo editado. Se a
   decisão mudar, escreve-se uma **nova ADR que a supersede**; a antiga recebe uma única edição:
   a linha de status `superseded by ADR-XXXX` (com link). Correções de typo triviais são a única
   exceção tolerada.
2. **Uma decisão por ADR, ~1 página.** Documentos guarda-chuva com várias decisões, revisões
   embutidas ou planos multi-ciclo não são ADRs.
3. **Critério de significância.** Só vira ADR o que é *arquiteturalmente significativo*: estrutura
   do sistema, requisitos não-funcionais, dependências novas, interfaces públicas, técnicas caras
   de reverter. "ADRs are not *Any* Decision Records."
4. **Ciclo de status fixo:** `proposed → accepted → deprecated | superseded by ADR-XXXX`.
   Nada de status de progresso ("60% concluída").
5. **Separação de camadas** — cada tipo de informação tem uma casa, e ADR referencia as outras
   por link, nunca as contém:

   | Camada | Onde vive | Natureza |
   |---|---|---|
   | **Decisão** | `adr/` | Imutável, append-only, uma por decisão |
   | **Planejamento** (gaps, prioridades, progresso) | GitHub Project #6 | Mutável, WIP-limitado |
   | **Medição** (scores, cobertura, mutation, gates) | Output do CI + snapshots datados em `docs/quality/` | Regenerável / append-only |

6. **Template MADR 4.0** ([madr.github.io](https://adr.github.io/madr/)) para ADRs novas:
   front-matter YAML (`status`, `date`, `decision-makers`), Context and Problem Statement,
   Decision Drivers, Considered Options (≥2), Decision Outcome, **Confirmation**, Consequences.
   A seção *Confirmation* aponta o gate/fitness function de CI que verifica a decisão, quando
   automatizável. ADRs 0002–0022 **não** são convertidas (histórico preservado como está).
7. **Decisões estáveis da ADR-0004 reafirmadas** (para que nada se perca com a supersessão):
   o projeto permanece um **monólito modular** (extração de módulos só com justificativa medida),
   e gaps **[E] estruturais exigem ADR aceita antes de qualquer código** (ADR-first).

## Confirmation

- `git log --follow adr/ADR-*.md` de ADRs `accepted` não deve mostrar commits além da criação e
  da eventual linha de supersessão — verificável em code review.
- Nenhum arquivo em `adr/` contém checkboxes de progresso (`grep -rn "\- \[ \]\|\- \[x\]" adr/`
  retorna apenas ocorrências históricas nas ADRs ≤ 0022, congeladas).
- O fechamento de gap no processo (`.claude/rules/workflow.md`) referencia apenas o board #6.

## Consequences

- Bom: histórico de decisões confiável; fim do rastreamento triplo; replanejar deixa de exigir PR em `adr/`.
- Bom: a seção Confirmation cria a ponte para fitness functions (auditoria vira código, não tabela).
- Ruim: perde-se o "painel único" que a ADR-0004 oferecia — mitigado pelo board #6 (fluxo) e por `docs/quality/` (estado).
- Ruim: disciplina extra ao revisar PRs de `adr/` (verificar que nenhuma ADR aceita foi editada).

## More Information

- Michael Nygard, *Documenting Architecture Decisions* (2011) · [Martin Fowler — ADR](https://martinfowler.com/bliki/ArchitectureDecisionRecord.html)
- [MADR 4.0](https://adr.github.io/madr/) · [adr.github.io](https://adr.github.io/) · [Joel Parker Henderson — architecture-decision-record](https://github.com/joelparkerhenderson/architecture-decision-record)
- [AWS Prescriptive Guidance — ADR process](https://docs.aws.amazon.com/prescriptive-guidance/latest/architectural-decision-records/adr-process.html) (decision backlog separado da ADR)
- Neal Ford et al., *Building Evolutionary Architectures* (fitness functions)
