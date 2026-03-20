# Políticas Explícitas — Kanban Vision API

> **Princípio Kanban**: Políticas invisíveis criam comportamentos imprevisíveis.
> Políticas explícitas e visíveis permitem que qualquer membro do time — humano ou LLM —
> tome decisões consistentes sem precisar perguntar a cada vez.

Estas políticas são a **fonte canônica de verdade** sobre como o trabalho flui neste projeto.
Estão versionadas em `docs/politicas-explicitas.md` e referenciadas no `CLAUDE.md`.
Qualquer mudança deve ser feita via PR com justificativa explícita.

---

## 1. Board — Critérios de Step

### Backlog
**Entrada**: identificado como gap, melhoria ou tarefa, independente de prioridade.
**Saída para Todo**: precisa de todas as condições abaixo:
- [ ] Escopo definido (1-2 frases claras)
- [ ] Tipo classificado: `[N]` normativo, `[M]` médio, `[E]` estrutural
- [ ] Se `[E]`: ADR com status `Aceita` já existe
- [ ] Dependências anteriores concluídas (ex.: GAP-G antes de GAP-O)

### Todo
**WIP limit**: sem limite fixo — mas apenas o item **no topo** é o próximo a ser puxado.
**Entrada**: passou pelos critérios de saída do Backlog.
**Saída para Doing**: quando uma sessão LLM inicia **e** não há item em Doing.
**Ordem**: itens são priorizados de cima para baixo. O topo = maior prioridade.

### Doing
**WIP limit: 1** — nunca mais de um item em Doing simultaneamente.
**Entrada**: puxado do topo do Todo no início de uma sessão LLM.
**Saída para Done**: PR mergeado em `main` + branch deletada + `main` atualizado localmente.
**Bloqueio**: se o item travar (bloqueio técnico, dependência externa), documentar o bloqueio
no card e mover de volta para Todo com nota de bloqueio — não deixar em Doing parado.

### Done
**Entrada**: PR mergeado + branch deletada + `main` local atualizado.
**Imutável**: itens em Done nunca retrocedem. Se surgir regressão, cria-se um novo card.

---

## 2. Pull Policy — Como Iniciar Trabalho

```
INÍCIO DE SESSÃO LLM:
  1. gh project item-list 6 --owner agnaldo4j → verificar se há item em Doing
  2. Se há item em Doing → continuar aquele item (não iniciar novo)
  3. Se Doing está vazio → puxar o PRIMEIRO item do topo de Todo
  4. Mover o item para Doing (atualizar GitHub Project via gh api graphql)
  5. Criar branch: git checkout -b feat/gap-X-slug (a partir de main atualizado)
  6. Reler CLAUDE.md + arquivos alvo antes de escrever qualquer código
```

**Regra de ouro**: nunca escolha qual item iniciar com base no que parece mais fácil
ou mais interessante. Sempre o **topo do Todo**. A prioridade já foi decidida.

---

## 3. Política de Encerramento — Como Fechar Trabalho

```
APÓS MERGE DO PR:
  1. git checkout main && git pull origin main
  2. git branch -d feat/gap-X-slug
  3. git push origin --delete feat/gap-X-slug (se necessário)
  4. Mover card no GitHub Project: Doing → Done (via gh api graphql)
  5. Marcar gap [x] no ADR-0004
  6. Atualizar memory/project_adr_progress.md com PR número e SHA
  7. Encerrar a sessão LLM
```

---

## 4. Políticas de Qualidade (J-Curve Safety Limits)

Estas políticas são **não negociáveis**. O CI as enforça automaticamente.
Nenhum PR pode ser mergeado se qualquer uma delas for violada.

| Política | Limite | Enforçado por |
|---|---|---|
| Cobertura de instruções JaCoCo | ≥ 95% por módulo | CI — bloqueia merge |
| Detekt violations | 0 (`warningsAsErrors = true`) | CI — bloqueia merge |
| KtLint formatting | 0 erros | CI — bloqueia merge |
| `./gradlew testAll` | verde | CI — bloqueia merge |
| Tamanho de PR | ≤ 400 linhas alteradas | Heurística — acima, dividir |
| WIP (itens em Doing) | máximo 1 | Política de pull |
| Gaps por sessão LLM | máximo 1 | Protocolo de sessão |

**Regra absoluta**: nunca editar `detekt.yml`, `.editorconfig`, `build.gradle.kts`,
`gradle.properties` ou o convention plugin para contornar uma violação. Corrija o código.

---

## 5. Políticas de ADR

| Tipo de gap | Política |
|---|---|
| `[N]` Normativo | Execute diretamente. Sem ADR obrigatória. |
| `[M]` Médio | 1 sessão de design + 1 PR focado. ADR recomendada se novo conceito. |
| `[E]` Estrutural | ADR com status `Aceita` **antes de qualquer código**. Sem ADR = fica no Backlog. |

**ADR-first**: nenhum gap `[E]` pode entrar em Todo sem ADR aprovada.

---

## 6. Políticas de Sessão LLM

| Política | Regra |
|---|---|
| Contexto | Reler CLAUDE.md + arquivos alvo no início de cada sessão |
| Escopo | Implementar apenas o gap planejado — sem "enquanto estamos aqui" |
| Limite de arquivos | Se o PR tocar > 5 arquivos de camadas distintas: parar e dividir |
| Encerramento | Fechar a sessão após o PR estar pronto para revisão |
| Sinal de esgotamento | LLM cria helpers desnecessários, esquece padrões ou sugere refatorações off-topic |

---

## 7. Política de Branches

| Convenção | Padrão |
|---|---|
| Branch de gap | `feat/gap-X-slug` (ex.: `feat/gap-j-pagination`) |
| Branch de ADR | `feat/adr-NNNN-slug` |
| Branch de fix | `fix/descricao-curta` |
| Branch de docs | `docs/descricao-curta` |
| Base | Sempre criada a partir de `main` atualizado (`git pull origin main`) |
| Lifetime | Deletar imediatamente após merge |
| Push direto em main | **NUNCA** — todo trabalho vai via PR |
| Force push em main | **NUNCA** |

---

## 8. Políticas de Arquitetura

| Política | Regra |
|---|---|
| Dependency Rule | `domain ← usecases ← adapters` — imports só apontam para dentro |
| Domain purity | Zero imports de framework em `domain/` |
| Ports location | Interfaces de repositório em `usecases/repositories/` — nunca em `domain/` |
| ForbiddenImport | `Jdbc*Repository` não pode ser importado fora de `AppModule` (Detekt rule) |
| CQS | Cada use case aceita exatamente um `Command` ou `Query` — nunca primitivos avulsos |
| Either para erros | Erros modelados como `Either<DomainError, T>` — sem exceções para controle de fluxo |
| Aggregate Root | Use cases não enforçam invariantes diretamente — delegam ao Aggregate Root |
| Board hydration | `JdbcBoardRepository.findById()` retorna Board com `steps = emptyList()`. Use cases devem hidratar antes de chamar `board.addStep()` / `board.addCard()` |

---

## 9. Políticas de Comunicação

| Situação | Política |
|---|---|
| Mudança de arquitetura | Abrir ADR antes de implementar — nunca surpresa no PR |
| Regressão descoberta | Criar card no board imediatamente — nunca ignorar |
| Bloqueio técnico | Documentar no card e mover de volta para Todo — nunca deixar em Doing parado |
| Divergência de política | Discutir via PR neste arquivo — a política muda via consenso, não silenciosamente |

---

## Referências

- Skill: [xp-kanban](../.claude/skills/xp-kanban/SKILL.md)
- Skill: [evolutionary-change](../.claude/skills/evolutionary-change/SKILL.md)
- Skill: [definition-of-done](../.claude/skills/definition-of-done/SKILL.md)
- GitHub Project #6: https://github.com/users/agnaldo4j/projects/6
- ADR-0004: [`adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md`](../adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md)
- CLAUDE.md — seção "Kanban Board Protocol"
