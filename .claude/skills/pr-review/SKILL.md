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

> 🔴 **Ausência de parecer NÃO é aprovação — o gate de revisão pode ficar silenciosamente VAZIO.**
> O job do harness é advisory e o passo carrega `continue-on-error`: quando a `claude-code-action` falha
> (`is_error: true`, tipicamente `num_turns: 1`, `duration_ms` ~300, `total_cost_usd: 0`), **nada é postado
> no PR** e o run inteiro ainda conclui **verde** (`success`) — o único sinal é um `::warning` enterrado na
> aba Actions. Some-se o Codex silencioso e o Copilot em quota, e o PR fica **mergeável com todos os checks
> verdes e ZERO revisão, sem nada vermelho** (aconteceu no #364). É a mesma família do "✅ fabricado" do
> GAP-CC: silêncio lido como aprovação.
> **Antes de dar um PR como pronto, confirme que o parecer EXISTE para o head SHA** — não presuma:
> ```bash
> N=<n>; SHA=$(gh pr view $N --json headRefOid -q .headRefOid)
> # 1) existe parecer ancorado NESTE head? O report abre com `<!-- pr-harness-report:<sha> -->`,
> #    então filtre pelo SHA — contar `## PR Review Harness` sem filtrar aceita um parecer STALE
> #    de um push anterior e declara revisado um head que ninguém olhou.
> #    (`gh api --jq` aceita UM argumento — nada de `--arg`; interpole o SHA na própria expressão)
> gh api repos/<owner>/<repo>/issues/$N/comments --paginate \
>   --jq ".[] | select(.body | contains(\"pr-harness-report:$SHA\")) | .html_url"   # vazio ⇒ sem parecer
> # 2) saída vazia → o harness rodou e falhou, ou nem chegou a rodar? (run verde ≠ parecer emitido)
> gh run list --workflow=pr-review.yml --limit 10 --json databaseId,createdAt,conclusion
> gh run view <id> --log | grep -E 'Resolved PR|Nenhum PR aberto|is_error|::warning'
> ```
> São **três** estados, não dois, e os dois últimos concluem verde: (a) parecer postado; (b) o harness
> resolveu o PR e **falhou** (`is_error: true`); (c) o run **nem chegou** ao harness — `Nenhum PR aberto com
> head == $RUN_SHA … Pulando` (ou o job sai `skipped`). Por isso o grep inclui a linha de "pulando".
> E **não troque o `--log` por `gh run view --json jobs`**: com `continue-on-error` o passo `Run PR harness`
> reporta `conclusion: success` mesmo com `is_error: true` — a checagem barata por JSON reintroduz
> exatamente o falso-verde que este bloco existe para matar.
> Se o parecer não existe, **dispatch manual** (a exceção legítima abaixo) — ou mergeie ciente de que o PR
> não teve revisão. Nunca registre "harness APPROVE" sem ter lido um parecer real.
> ⚠️ **Parecer sem o marcador** (postado antes desta convenção) não prova nada sobre o head atual: trate como
> ausente e confirme pelo passo 2. E o mesmo vale para os **inline** — eles já ancoram no SHA via
> `<!-- pr-harness:<sha>:… -->`, então cheque o SHA ali também antes de dar um achado como endereçado.
> ⚠️ **Nota de `workflow_run`:** o `head_sha`/`head_branch` que a API mostra para os runs de `pr-review.yml`
> é o da **default branch**, não o do PR — filtrar os runs por SHA do PR dá falso "nunca rodou". Case por
> **horário** (o run nasce segundos após o CI concluir) e confirme no log a linha `Resolved PR #<n>`.

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
5. **PROPONHA as lições (não aplique aqui):** depois de relayar e resolver/responder os threads, pergunte
   *"algo aqui revelou lacuna numa skill, regra ou no rubric?"*. Se sim, **inclua a lição no parecer** como
   emenda concreta proposta (o que mudar e onde). **Esta skill é read-only** (frontmatter `allowed-tools`
   sem write; no CI o `pr-review.yml` roda com `contents: read`) — ela **não edita arquivos**. Quem
   **aplica** a lição é o agente `post-merge-harvester`, **após o merge de uma implementação real** (guard
   anti-loop: `docs/quality/lessons-learned.md`), transformando-a em emenda + linha no log. Não force lição:
   só quando há sinal real (§6 do rubric).

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
