# Lições pendentes — fila de aplicação

Fila de espera das lições colhidas pelo `post-merge-harvester`, **ainda não aplicadas**. Existe para que
melhoria de processo pare de competir com entrega de produto.

## Por que uma fila (e não um PR por lição)

O harvester abria um PR de processo a **cada** implementação real mergeada. Com um ciclo de produto por
sessão, isso significava ~1 PR de doc para cada PR de código: o dobro de ciclos de revisão, de CI e de
atenção do mantenedor, para melhorias que quase nunca são urgentes. O efeito no fluxo é direto — cada PR de
processo é lead time que não virou produto.

**Regra (decisão do mantenedor, 2026-07-28):** as lições **acumulam aqui** e são aplicadas **em lote, a cada
10 PRs de código mergeados**. Nada se perde; só deixa de interromper.

## Como contar os 10

"PR de código" = commit na `main` que toca `*/src/main/**`. Os PRs de processo/doc não contam.

```bash
# marco do último lote. Bootstrap: antes do primeiro `lote`, vale qualquer `docs(process):` —
# o #386 é o último "aplicou tudo". Sem o fallback, o contador varre a história inteira (121 na
# medição de 2026-07-28) e o lote dispararia no primeiro merge.
LAST=$(git log origin/main --grep='^docs(process): lote' -1 --format=%H)
LAST=${LAST:-$(git log origin/main --grep='^docs(process):' -1 --format=%H)}
RANGE=${LAST:+$LAST..}origin/main

# quantos PRs de código desde então (commit que toca */src/main/**)
git log --format=%H $RANGE | while read c; do
  git show --name-only --format= "$c" | grep -q '/src/main/' && echo "$c"
done | wc -l
```

Chegou a **10 ou mais** → o harvester aplica a fila inteira num único PR `docs(process): lote N — <temas>`,
esvazia este arquivo e registra as linhas em `lessons-learned.md`. Abaixo de 10 → **só acrescenta aqui e
para**, sem abrir PR.

**Exceção — aplique fora do lote:** lição cuja ausência deixa um **defeito ativo** ou um **guard furado**
(algo que permite merge indevido, mascara falha de gate, ou perde dado). Nesse caso, diga no relato por que
não podia esperar. Preferência de estilo, clareza de comentário e enriquecimento de rubric **sempre esperam**.

---

## Fila atual

*(vazia — as lições dos ciclos #381→#385 foram aplicadas nos PRs #382, #384 e #386, antes desta regra existir)*

| Data | PR de origem | Lição durável | Onde aplicar (proposto) |
|---|---|---|---|
| 2026-07-28 | #385 | **O `sql_persistence` está no limite do orçamento de tempo do PITest.** Medido no #385: o módulo caiu de 75% para 72% (gate 65) **sem que o diff piorasse nada** — o per-arquivo dos arquivos tocados é idêntico à base, exceto um mutante novo, morto. Os 22 mutantes perdidos estão em `DatabaseFactory.kt` e `SimulationStateSerializer.kt`, não tocados: o teste `given filesystem migrations location … flyway migrates from the custom path` caiu de 20 kills para 2 porque, com mais uma classe de Embedded PostgreSQL no módulo, estoura o orçamento e vira `TIMED_OUT` (19→22 no módulo). Reproduzido em 2 rodadas. **A tendência é continuar caindo** a cada classe de teste com banco embarcado, até cruzar o gate de 65 — e aí o build quebra por um motivo que não é qualidade do código. As duas saídas óbvias estão barradas: o bloco `pitest` do `build.gradle.kts` é imutável por política, e fundir classes de teste é deixar a ferramenta ditar o design. **Não é lição de skill — é decisão de trade-off**, provavelmente ADR ou card `[M]`. | Decisão do mantenedor: ADR ou card. A ordem de diagnóstico já está em `.claude/rules/kotlin-quality.md`; o que falta é a decisão. |
