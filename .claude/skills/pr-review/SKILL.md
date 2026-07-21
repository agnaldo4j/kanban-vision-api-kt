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

## Default: LEIA os reports do CI; dispatch manual é EXCEÇÃO

O `.github/workflows/pr-review.yml` (GAP-CT) **já roda o mesmo `pr-harness` automaticamente** após o CI e
posta o parecer como comentário **`[claude]`**. Então, num PR aberto, o **caminho padrão é VERIFICAR os
reports já postados** — não redisparar o harness. Dispatchar `/pr-review` manualmente é **exceção**:
quando o CI ainda não rodou no head SHA e quero feedback imediato, ou uma re-review pontual.

> ⚠️ **Ao ler os reports, NUNCA confie só no RESUMO do parecer** — leia os **comentários inline reais**.
> O resumo do harness pode dizer "APPROVE" enquanto os inline (e o Codex) carregam P1/P2 — inclusive
> bloqueantes. Verifique com:
> ```bash
> gh api repos/<owner>/<repo>/pulls/<n>/comments --paginate --jq '.[] | {id, author:.user.login, path, line, body}'
> gh api graphql -f query='{ repository(owner:"<owner>",name:"<repo>"){ pullRequest(number:<n>){ reviewThreads(first:40){ nodes { id isResolved comments(first:1){ nodes { databaseId author{login} } } } } } } }'
> ```
> Responder um thread: `POST .../pulls/<n>/comments/<id>/replies`. Resolver: GraphQL `resolveReviewThread`.

> ⚠️ **Se dispatchar manual:** o subagente `pr-harness` roda Bash **no mesmo working dir** e pode dar
> `git checkout` (já trocou de branch e reverteu arquivos mid-review — o commit pushado fica intacto pois
> ele revisa o SHA remoto). **Confira `git branch --show-current` depois.** Preferir os reports do CI evita
> isso de vez.

## O que fazer ao invocar (dispatch manual — a exceção)

1. **Resolva o alvo:**
   - Argumento numérico → esse PR (`gh pr diff <n>`, `gh pr view <n> ...`).
   - `branch` ou sem argumento → o PR da branch atual (`gh pr view --json number`) ou, se não houver PR,
     o diff `git diff main...HEAD`.
2. **Delegue ao subagente `pr-harness`** via a Agent tool (`subagent_type: pr-harness`), passando o alvo
   resolvido (número do PR ou instrução de usar o diff da branch). O subagente roda **em contexto isolado**
   (olhar imparcial), afere consistência/guards/negócio **e faz sua própria caça a bugs de implementação**
   (§2.5 do rubric — concorrência/TOCTOU, Either/Raise, bordas, injeção, armadilhas de CI), e devolve o parecer.
3. **Postagem (quando o alvo é um PR real):** o harness publica **cada achado P1/P2/P3 como comentário
   inline** no `arquivo:linha` (estilo Codex, com badge de severidade — §5.5 do rubric) **além** do report
   `## PR Review Harness`. Num diff de branch local sem PR, só há o parecer.
4. **Relaie o parecer** ao usuário verbatim (veredito + achados P1/P2/P3 + cruzamento com CI/Codex +
   coerência de negócio + — quando presentes — melhorias, direcionamento estratégico e **lições aprendidas**
   para as skills/o rubric). Não edite nem "amacie" — o harness é criterioso de propósito.
5. **Capture as lições (obrigatório se houver sinal real):** depois de relayar e resolver/responder os
   threads, pergunte *"algo aqui revelou lacuna numa skill, regra ou no rubric?"*. Se sim, **registre em
   `docs/quality/lessons-learned.md`** (uma linha: PR · lição durável · onde aplicada) e **aplique a emenda**
   na skill/regra/rubric no mesmo PR ou próximo (pequena), ou **abra um card** no #6 (grande). É o que fecha
   o loop — nunca deixe um miss recorrente só no comentário do PR (que é efêmero). Não force lição: só
   quando há sinal real (§6 do rubric).

## Complementaridade

- O harness **não** re-roda os gates de CI (Detekt/JaCoCo/PITest/Konsist/osv) nem o scan OWASP do hook —
  ele cruza os resultados. Não substitui o CI nem o Codex; adiciona a camada semântica/design/negócio.
- É **advisory**: o veredito informa a decisão humana de merge; nunca bloqueia por si só.

## Relação com Outras Skills

| Esta skill | Complementa |
|---|---|
| Dispara o `pr-harness` | `/definition-of-done` (checklist de completude), `/wiki-maintenance` (página do wiki atualizada?) |
| Ancora nas rubricas | `/owasp`, `/ddd`, `/clean-architecture`, `/openapi-quality`, `/db-migrations`, `/adr` e demais skills de domínio |

## Loop de lições aprendidas

O que o review ensina não pode morrer no comentário do PR. O destino durável é
**`docs/quality/lessons-learned.md`** (log append-only: PR · lição · onde aplicada) — a metade persistente
do loop que o rubric §6 dispara. Lições **genéricas** viram emenda em skill/regra/rubric; lições
**específicas da feature** ficam na ADR / nas notas do gap (não poluem as skills). Ver §6 do
`.claude/agents/pr-harness.md`.

## Referências

- Agente: `.claude/agents/pr-harness.md` (a rubrica)
- Log de lições: `docs/quality/lessons-learned.md`
- Política: `docs/politicas-explicitas.md` · regras: `.claude/rules/*`
- Também roda no CI (advisory, não-bloqueante): `.github/workflows/pr-review.yml`
