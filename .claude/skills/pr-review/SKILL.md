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

> 🔴 **Ausência de parecer NÃO é aprovação — mas TAMBÉM não é defeito. Normalmente é SALDO.**
> A `claude-code-action` é **paga**: sem saldo na conta, ela sai com `is_error: true` (tipicamente
> `num_turns: 1`, `duration_ms` ~300, `total_cost_usd: 0`) e **nada é postado no PR**. Isso é
> **comportamento esperado**, não incidente — volta a rodar quando há saldo.
> ⛔ **NÃO diagnostique, não abra card, não construa visibilidade para isso.** O card GAP-EK nasceu dessa
> premissa errada e foi **fechado pelo mantenedor** (2026-07-25: *"não tem nada a modificar"*). Não confunda
> com o GAP-CW, que era `--model` inválido e já foi corrigido.
> ⛔ **NUNCA dispare o `/pr-review` por conta própria** — inclusive "para puxar melhorias". O harness custa
> dinheiro; **quem decide quando rodar é o mantenedor**. Se o parecer não existe, **relate e siga**; se achar
> que vale rodar, **pergunte**.
>
> O que **continua** valendo: o job é advisory e o passo carrega `continue-on-error`, então o run conclui
> **verde** de qualquer forma. Somado ao Codex silencioso e ao Copilot em quota, um PR fica **mergeável com
> todos os checks verdes e ZERO revisão, sem nada vermelho** (aconteceu no #364). Então **não registre
> "harness APPROVE" sem ter lido um parecer real**, e decida o merge com a regra de isenção abaixo — mas
> trate a ausência como *informação*, não como alarme.
> **Antes de dar um PR como pronto, confirme que o parecer EXISTE para o head SHA** — não presuma:
> ```bash
> N=<n>; SHA=$(gh pr view $N --json headRefOid -q .headRefOid)
> # 1) existe parecer ancorado NESTE head? O report abre com `<!-- pr-harness-report:<sha> -->`,
> #    então filtre pelo SHA — contar `## PR Review Harness` sem filtrar aceita um parecer STALE
> #    de um push anterior e declara revisado um head que ninguém olhou.
> #    (`gh api --jq` aceita UM argumento — nada de `--arg`; interpole o SHA na própria expressão)
> gh api repos/<owner>/<repo>/issues/$N/comments --paginate \
>   --jq ".[] | select(.body | contains(\"pr-harness-report:$SHA\")) | .html_url"   # vazio ⇒ sem parecer
> # 1b) VAZIO em (1) não distingue "ninguém rodou" de "rodou e morreu no meio": os achados INLINE carregam
> #     o SHA no marcador (`<!-- pr-harness:<sha>:P<n>:… -->`) e sobrevivem quando o report não sai.
> #     Achar inline SEM report é sinal de run ABORTADO — diagnóstico útil (rerode), NÃO prova de revisão:
> #     quantos achados ficaram por emitir é indeterminado. Só (1) atesta revisão completa.
> gh api repos/<owner>/<repo>/pulls/$N/comments --paginate \
>   --jq '.[].body' | grep -o 'pr-harness:[a-f0-9]\{40\}' | sort -u    # SHA(s) que o harness COMEÇOU a revisar
> git diff --stat <sha-revisado> $SHA                                  # o que mudou DEPOIS da revisão
> # 2) saída vazia → o harness rodou e falhou, ou nem chegou a rodar? (run verde ≠ parecer emitido)
> gh run list --workflow=pr-review.yml --limit 10 --json databaseId,createdAt,conclusion
> gh run view <id> --log | grep -E 'Resolved PR|Nenhum PR aberto|is_error|::warning'
> ```
> São **três** estados, não dois, e os dois últimos concluem verde: (a) parecer postado; (b) o harness
> resolveu o PR e **não rodou** (`is_error: true` — quase sempre **sem saldo**, ver acima; nada a consertar);
> (c) o run **nem chegou** ao harness — `Nenhum PR aberto com head == $RUN_SHA … Pulando` (ou o job sai
> `skipped`). Por isso o grep inclui a linha de "pulando". Distinguir (b) de (c) serve para **saber**, não
> para agir: em nenhum dos dois há defeito nosso.
> E há um **quarto** estado, que o passo (1b) revela: **inline ancorado, report em head nenhum**.
> ⚠️ **Inline NÃO prova revisão completa — prova que a POSTAGEM começou.** O harness publica cada achado
> conforme o encontra e só ao final emite o veredito; se ele morrer no meio (crash, `Login expired`,
> timeout, saldo), os inline já publicados sobrevivem e o report nunca sai. **Quantos achados ficaram por
> emitir é indeterminado por construção** — nenhuma contagem de inline responde isso.
> Logo o quarto estado é **diagnóstico, não licença**: ele informa "um run começou e não terminou" — o que
> é acionável (**rerodar**) —, mas **não** converte o head antigo em revisado. Só há revisão completa com
> **report/veredito ancorado**; sem ele, o PR que exige revisão continua exigindo. Tratar inline órfão como
> parecer é fail-open da mesma família do `grep -v` engolindo `exit≠0`: transforma um **desconhecido** em
> **aprovado**.
> Com report ancorado em `X` e head `Y ≠ X`, aí sim vale a regra do delta: `git diff --name-only X Y` →
> `scripts/review-exemption.sh --paths -`; se toca produção/gates o head novo **exige** parecer; se é
> doc/test-only nascido de um achado aceito, registre "revisado em `X`, delta test-only" e siga.
> **O #381 é exemplo do caso RUIM, não do bom:** dois P3 inline em `d9641430`, **nenhum report em head
> algum**, e o dispatch manual morreu com `Login expired` — mergeou sem revisão completa de head nenhum.
> Foi justamente o que motivou esta regra (Codex P1 no #382).
> E **não troque o `--log` por `gh run view --json jobs`**: com `continue-on-error` o passo `Run PR harness`
> reporta `conclusion: success` mesmo com `is_error: true` — a checagem barata por JSON reintroduz
> exatamente o falso-verde que este bloco existe para matar.
> Se o parecer não existe, o que fazer depende do tipo de PR — mas **por allowlist, não por exclusão**:
> dispensa revisão **só** o PR em que **TODO** arquivo alterado é doc/processo puro. Qualquer outra coisa
> exige **dispatch manual antes do merge**.
> A regra é **executável e roda sozinha no CI** — o job advisory `Review Exemption Advisory` posta um
> sticky **Review Exemption Report** em todo PR. Não dependa de lembrar: leia o comentário. Para rodar
> à mão:
> ```bash
> scripts/review-exemption.sh <n>          # consulta o PR
> scripts/review-exemption.sh --paths -    # classifica caminhos do stdin (offline, sem token)
> #   exit  0  EXEMPT           → doc/processo puro; merge sem parecer é aceitável (registre no PR)
> #   exit  1  REVIEW-REQUIRED  → lista os arquivos que chegam em produção/gates ⇒ dispatch manual
> #   exit  2  INDETERMINATE    → API falhou ou lista vazia ⇒ trate como REVIEW-REQUIRED
> #   exit 64  erro de USO      → distinto de 1 de propósito: typo não pode virar "há risco"
> ```
> A tabela caminho → esperado vive em `scripts/test-review-exemption.sh` e roda como gate no CI —
> foi assim que os vazamentos da allowlist apareceram; ler o regex não bastou, duas vezes.
> O script é a fonte única da allowlist; o `SKILL.md` só explica **por que** ela tem a forma que tem — três
> decisões que vieram de defeito real, e que não devem ser "simplificadas" de volta:
> - **Fail-closed em erro de API.** `gh … | grep -v` engole `exit≠0`: token expirado, rate limit ou PR
>   inexistente devolvem **stdout vazio**, indistinguível de doc-only — e a regra criada para impedir merge
>   sem revisão passaria a **autorizá-lo**. Modo `gh api` exit-0-corpo-vazio do GAP-CC/#288 dentro do próprio
>   guard. Daí o exit 2 separado.
> - **Allowlist por TIPO, não por diretório.** `\.claude/` inteiro isentaria `hooks/guard-security.sh` (o guard
>   de segredo hardcoded declarado em `security.md`) e `settings.json`; e `[^/]+/src/test/` isentaria
>   `architecture/src/test/**`, o módulo **test-only** das fitness functions — apagar
>   `ProjectDependencyGraphTest.kt` deixa `testAll`/JaCoCo/PITest verdes porque não há `src/main` para cobrir.
>   Por isso: só `**/*.md` nos diretórios de doc, e os módulos de teste isentos **enumerados**, com
>   `architecture` fora.
> - **`pulls/<n>/files`, não `pr view --json files`.** Pagina além de 100 arquivos e expõe `previous_filename`
>   — sem ele um `git mv http_api/src/main/X.kt docs/X.kt` aparece só com o caminho novo e sai como doc-only.
> - **Nada em `.claude/**` nem `adr/**` é isento**, por mais `.md` que seja — sem isso o guard **autoriza a
>   própria remoção**: um PR que reabre o pipe fail-open ou afrouxa o rubric sairia como "doc puro". Aferido
>   retroativamente: o **#365 muda de EXEMPT para REVIEW-REQUIRED**, e de fato ele mergeou sem revisão
>   alterando o rubric. Tentei antes enumerar "só os subdiretórios perigosos"
>   (`agents|rules|skills/pr-review`) e **vazou duas vezes**: ficaram de fora `CLAUDE.md`, a skill
>   `/github-ci-health` (que documenta o próprio sintoma de gate vazio) e `/xp-kanban` — para a qual a regra
>   *protegida* `rules/workflow.md` delega o Board Protocol, um bypass por indireção. Diretório inteiro, então:
>   skill nova entra protegida por padrão. `adr/**` entra junto — ADR aceita é imutável e gateia todo `[E]`
>   (ADR-0023). (Codex P1 + harness P2 no #368.)
> - **Limitação conhecida e aceita:** `<módulo>/src/test/**` é isento, mas um PR test-only que **esvazia
>   asserções** passa — as linhas seguem executadas, então o JaCoCo não cai. Ao revisar PR de teste, olhe
>   remoção de asserção, não só cobertura. (Harness P3 no #368.)
>
> (Codex P2 no #367 · harness P2×2 no #367 · Codex P1 + harness P2 no #368.)
> **Por que allowlist.** A versão anterior desta regra perguntava "toca `*/src/main/**`?" e liberava todo o
> resto — o que dispensaria revisão de um PR que só mexe em `Dockerfile`, `k8s/**`, `.github/workflows/**`,
> `build.gradle.kts`, `buildSrc/**`, `config/**`, `scripts/**` (consumidos pelos gates) ou uma migration
> Flyway. Nenhum deles tem `src/main` e todos mudam produção, dependências ou os próprios gates. O PR #367,
> que introduziu a regra, **se auto-isentaria** por essa lógica ao alterar `.github/workflows/pr-review.yml`.
> Codex P2 no #367. Com allowlist, o caminho não-reconhecido cai no lado seguro.
> ⚠️ **Não reuse o predicado `*/src/main/**` do `post-merge-harvester`** como proxy de risco de revisão: lá
> ele existe para outra finalidade (guard anti-loop — impedir que uma melhoria de processo dispare outra), e
> ser conservador *naquela* direção é o oposto de ser conservador aqui.
>
> Nunca registre "harness APPROVE" sem ter lido um parecer real.
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
> ⚠️ **Passe o corpo por ARQUIVO, nunca interpolado:** `-F body=@resposta.md` (ou `gh pr comment --body-file`),
> jamais `-f body="…"`. Uma resposta de review é markdown com crases, `$`, `!` e regex — em `zsh`/`bash` isso
> vira substituição de comando ou glob. Mordeu duas vezes no mesmo dia: no #367 uma crase engoliu uma palavra
> do texto **já publicado**, e no #368 um `$)` de regex abortou o comando inteiro com `bad pattern`. Escreva
> num arquivo temporário e mande o arquivo.

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

## Como responder a um achado (lado AUTOR) — medir vence autoridade

O harness é criterioso, não infalível. Ao receber um achado, as respostas erradas são as duas simétricas:
**obedecer por autoridade** ("o revisor pediu") e **recusar por opinião** ("acho que não procede"). A regra
é **medir e responder com o número** — e vale nos dois sentidos, porque quem mede está certo
independentemente do papel.

### Disposição: o achado se corrige NO PR que o gerou; só a LIÇÃO é que se agenda

🔴 **Política do mantenedor (2026-07-28, no #385):** *"para estas demandas encontradas, corrigir já nesse
PR, não há necessidade de deixar para depois, o review serve para isso, somente lições aprendidas devem ser
avaliadas se resolvemos agora ou depois."* Três destinos distintos, e só um deles é adiável:

| Sinal da revisão | Destino | Quem decide "agora ou depois" |
|---|---|---|
| **Achado** P1/P2/P3 (defeito) | **corrigir no próprio PR**, com o thread respondido e resolvido | ninguém — é agora |
| **Melhoria** (§3.5, não é defeito) | candidato; relate ao mantenedor | o mantenedor |
| **Lição durável** | `post-merge-harvester`, pós-merge | o harvester/mantenedor, no PR de processo |

- **"Pré-existente na main / fora do diff" não é passe para adiar.** No #385 o próprio parecer classificou o
  P2 como fora do diff e sugeriu "candidato a card `[N]`"; o mantenedor mandou corrigir ali mesmo — e estava
  certo: o defeito era outra instância **da mesma classe** que o PR já estava consertando (tolerância de
  decode nos mesmos serializers). Adiar teria criado uma terceira rodada da mesma classe (#383 → #384 →
  #385), que é precisamente o que o ciclo mostrou custar caro.
- **Dois limites, e são estruturais — não são desculpa de escopo:**
  1. **O fix muda contrato/camada/identidade (`[E]`) ou é outro gap.** Aí não cabe no PR: vira
     **pergunta → card → priorização**, nunca execução direta (§4 de `docs/politicas-explicitas.md`).
  2. **O PR que recebeu o achado é doc/processo puro e o fix é código.** Ele *não pode* absorver — o guard
     anti-loop do `post-merge-harvester` classifica por `*/src/main/**`, então código num PR de lições faria
     uma melhoria disparar outra colheita. Foi o caso do #384 (P2 do Codex num PR de lições): o fix teve de
     sair noutro PR — e é justamente aí que o **card vem ANTES do PR**. O #385 nasceu sem card por pular esse
     passo, e o mantenedor cobrou.
- **Corrigir agora é mais barato que cardar.** O contexto está carregado, o revisor está no thread e o card
  gasta uma passagem inteira de priorização/WIP para um defeito que já está sob os olhos.

- **Antes de aplicar, cheque se a correção sugerida contradiz uma regra do repo.** Aconteceu no **#381**: o
  harness pediu `getOrNull()?.let { }` num call site **pré-guardado**, que é exatamente o *ramo morto*
  proibido por `.claude/rules/kotlin-quality.md` — regra nascida no **#350, no mesmo arquivo, por sugestão
  do próprio harness**. Um reviewer não carrega memória das regras que produziu além do que está escrito, e
  o checklist do rubric casa por **forma**. Medido: `.onRight` = 0 missed; a forma sugerida = 1 BRANCH + 2
  INSTRUCTION missed, incobríveis por construção. Se contradiz, **meça, recuse com a medição, e cite a
  regra e o PR de origem** — nunca só "não concordo".
- **Existe uma terceira saída: dissolver, não deslocar.** Quando o achado está certo na **forma** mas todo
  remédio local é barrado por outra regra, o desfecho certo não é aplicar nem descartar — é **deferir ao
  refactor que faz o problema deixar de existir**, dizendo por quê. No #381 o ramo morto some dentro de uma
  HOF (`mutateCardAt`, **GAP-EA**, já no Todo do #6), onde ele vira alcançável e **testável de verdade**.
  Registre o achado no card de destino para ele não se perder no thread.
- **Ofereça o trade oposto ao mantenedor.** Recusar não é fechar a decisão: explicite o que se ganha e o que
  se perde ("se preferir o branch incobrível agora em troca da garantia estrutural, é sua decisão e eu
  aplico"). A escolha é do mantenedor, não sua nem do revisor.
- **A enumeração de um achado é PISO, não teto — faça o grep antes de corrigir.** No #381 o achado de
  duplicação citava **duas** cópias do fixture `simulationFrom`; havia **três**
  (`…GuardBehaviorTest`, `…MetricsBehaviorTest` e a nova `…MoveGuardBehaviorTest`). Corrigir só as citadas
  é **meio-consertar**: a divergência silenciosa que o achado descreve continua possível entre as
  remanescentes. Vale para toda família (duplicação, import proibido, caminho que vaza de allowlist) —
  varra a **classe** do problema, não as instâncias listadas. Mesma família de #367/#368 ("enumerar vaza").
  Segunda ocorrência, e desta vez a varredura **pagou**: no **#385** o parecer listava os ids e as métricas
  de `FlowMetrics` como pontos que lançavam no decode do `history`; enumerar a subárvore alcançável inteira
  revelou `SimulationDay.init` (exige `>= 1`), que ninguém tinha citado — um `day: 0` legado lançava pelo
  mesmo caminho. **A unidade de varredura é o construtor, não o campo citado**: ver o procedimento em
  `.claude/rules/migrations.md` (subárvore alcançável do nó decodificado).
- **Depois de responder, resolva o thread** e garanta que o corpo do PR reflita os commits que nasceram da
  revisão — o corpo é o insumo do `post-merge-harvester` (§2.5 do rubric).

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
