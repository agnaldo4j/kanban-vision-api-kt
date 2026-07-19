---
name: pr-review
description: >
  Revisa um PR ou um diff de branch DESTE repositório com o harness criterioso do projeto — consistência
  com as skills, guards de qualidade, Dependency Rule/fronteiras de contexto, gap-type, DoD e coerência
  com o objetivo de negócio (simulador Kanban). Use antes de mergear um PR, ou para uma segunda opinião
  rigorosa sobre uma branch. Read-only.
argument-hint: "[número do PR ou 'branch' (opcional; default = PR da branch atual)]"
allowed-tools: Read, Grep, Glob, Bash
---

# /pr-review — disparar o harness de revisão

Esta skill é um **dispatcher fino**. A rubrica completa vive no subagente `pr-harness`
(`.claude/agents/pr-harness.md`) — não a repita aqui.

## O que fazer ao invocar

1. **Resolva o alvo:**
   - Argumento numérico → esse PR (`gh pr diff <n>`, `gh pr view <n> ...`).
   - `branch` ou sem argumento → o PR da branch atual (`gh pr view --json number`) ou, se não houver PR,
     o diff `git diff main...HEAD`.
2. **Delegue ao subagente `pr-harness`** via a Agent tool (`subagent_type: pr-harness`), passando o alvo
   resolvido (número do PR ou instrução de usar o diff da branch). O subagente roda **em contexto isolado**
   (olhar imparcial) e devolve o parecer.
3. **Relaie o parecer** ao usuário verbatim (o veredito + achados P1/P2/P3 + cruzamento com CI/Codex +
   coerência de negócio). Não edite nem "amacie" — o harness é criterioso de propósito.

## Complementaridade

- O harness **não** re-roda os gates de CI (Detekt/JaCoCo/PITest/Konsist/osv) nem o scan OWASP do hook —
  ele cruza os resultados. Não substitui o CI nem o Codex; adiciona a camada semântica/design/negócio.
- É **advisory**: o veredito informa a decisão humana de merge; nunca bloqueia por si só.

## Relação com Outras Skills

| Esta skill | Complementa |
|---|---|
| Dispara o `pr-harness` | `/definition-of-done` (checklist de completude), `/wiki-maintenance` (página do wiki atualizada?) |
| Ancora nas rubricas | `/owasp`, `/ddd`, `/clean-architecture`, `/openapi-quality`, `/db-migrations`, `/adr` e demais skills de domínio |

## Referências

- Agente: `.claude/agents/pr-harness.md` (a rubrica)
- Política: `docs/politicas-explicitas.md` · regras: `.claude/rules/*`
- Também roda no CI (advisory, não-bloqueante): `.github/workflows/pr-review.yml`
