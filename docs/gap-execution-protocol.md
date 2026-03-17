# Gap Execution Protocol

> **Princípio**: Uma sessão LLM por gap. Contexto fresco = código consistente.
> Sessões longas esgotam o contexto e produzem violações Detekt não percebidas,
> testes esquecidos e padrões `Either` ignorados. Este protocolo é a resposta
> estrutural ao J-Curve da mudança (PMI, 2014: 82% de falha em mudanças grandes).

---

## Classificação dos gaps

Antes de executar qualquer gap, verifique sua classificação:

| Tipo | Definição | Ação |
|------|-----------|------|
| `[N]` Normativo | Adiciona/melhora sem quebrar relações, status ou identidade do sistema | Execute diretamente. 1 gap por sessão. |
| `[M]` Médio impacto | Adiciona novo conceito ou novo artefato de infraestrutura | 1 sessão de design + 1 PR focado. |
| `[E]` Estrutural | Altera contratos, camadas ou identidades do sistema | **Requer ADR aprovada antes de qualquer código.** |

Ver classificação completa em `adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md`.

---

## J-Curve Tolerances (limites de Safety)

Estes limites nunca devem ser violados durante a execução de um gap:

| Medida | Limite | O que fazer se violar |
|--------|--------|-----------------------|
| JaCoCo coverage | ≥ 95% por módulo | Escrever o teste faltante — nunca baixar o threshold |
| Detekt violations | 0 (`warningsAsErrors = true`) | Refatorar — nunca suprimir sem comentário justificando |
| KtLint | 0 erros de formatação | `./gradlew ktlintFormat` antes do commit |
| Build | `./gradlew testAll` verde | Investigar root cause — nunca usar `--no-verify` |
| PR size | ≤ 400 linhas alteradas | Dividir em PRs menores se exceder |
| CI verde | Obrigatório antes do merge | Nunca fazer merge com CI vermelho |

**Patience**: PRs revisados em < 48h. Se um PR ficar aberto mais de 48h sem revisão,
reavalie o escopo — provavelmente está grande demais.

---

## Protocolo de execução por sessão LLM

### Pré-gap (início da sessão)

```
[ ] Verificar branch main: git pull origin main
[ ] Criar branch: feat/gap-X-slug-descritivo
[ ] Reler: CLAUDE.md
[ ] Reler: adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md (seção do gap)
[ ] Se gap [E]: confirmar que ADR dedicada existe com status Aceita
[ ] Identificar os 2-3 arquivos-alvo do gap e lê-los antes de escrever código
[ ] Verificar dependências: o gap anterior na ordem do ciclo está concluído?
```

### Durante a execução

```
[ ] Implementar somente o gap planejado — sem "aproveitando a sessão"
[ ] A cada 3-4 arquivos editados: rodar ./gradlew testAll para validar parcialmente
[ ] Se o PR tocar > 5 arquivos: parar e dividir o gap em dois PRs menores
```

### Pós-gap (antes de abrir PR)

```
[ ] ./gradlew testAll verde (Detekt + KtLint + testes + JaCoCo ≥ 95%)
[ ] Dependency Rule verificada: nenhum import de framework em domain/ ou usecases/
[ ] Se nova rota, caso de uso ou módulo: atualizar diagramas C4 (/c4-model)
[ ] Se novo endpoint: OpenAPI spec completa (/openapi-quality)
[ ] Marcar gap como [x] em ADR-0004 Plano de Implementação
[ ] Atualizar memory/project_adr_progress.md com PR e status
[ ] Abrir PR com título: feat(gap-X): descrição do gap
[ ] Encerrar sessão após PR pronto — não acumular gaps na mesma sessão
```

---

## Sinais de que o contexto da sessão está esgotado

Se qualquer um destes sinais aparecer: **encerre a sessão imediatamente**.

- O assistente criou helpers ou abstrações que não existiam antes e não foram pedidos
- O assistente usou `Either` ou Arrow-kt diferente do padrão do projeto
- O PR está tocando > 5 arquivos para um único gap
- O assistente sugeriu "enquanto estamos aqui, vamos também refatorar X"
- Violações Detekt estão aparecendo mas o assistente não as viu antes de commitar

**Ação**: abra nova sessão com contexto limpo. Releia CLAUDE.md e os arquivos alvo.

---

## Ordem dos ciclos de execução

```
Ciclo Hardening  (P1):  GAP-B → GAP-C → GAP-A                          ✅ concluído
Ciclo Operações  (P2):  GAP-F → GAP-D → GAP-E → GAP-G → GAP-V → GAP-U ✅ concluído
Ciclo Domínio    (P3):  GAP-W → GAP-O → GAP-P → GAP-Q → GAP-S → GAP-I → GAP-J → GAP-H → GAP-K
Ciclo Excelência (P4):  GAP-T → GAP-N → GAP-R → GAP-L → GAP-M
```

Não pule a ordem dentro de um ciclo sem verificar dependências em ADR-0004.

---

## Referências

- Skill: [`evolutionary-change`](.claude/skills/evolutionary-change/SKILL.md)
- Skill: [`definition-of-done`](.claude/skills/definition-of-done/SKILL.md)
- ADR-0004: [`adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md`](../adr/ADR-0004-avaliacao-qualidade-gaps-priorizados.md)
- Martin Fowler: [Strangler Fig Application](https://martinfowler.com/bliki/StranglerFigApplication.html)
- PMI (2014): Organizational Change Management — 82% failure rate for large changes
