---
status: accepted
date: 2026-07-06
decision-makers: "@agnaldo4j"
---

# ADR-0029 — Gate JaCoCo default de 0.97 para 0.98

## Context and Problem Statement

O convention plugin (`buildSrc/src/main/kotlin/kanban.kotlin-common.gradle.kts`) fixa o gate de
cobertura de instrução em **0.97** para todos os módulos. A medição atual mostra que o projeto
já opera acima disso em metade dos módulos e que o gap dos demais é composto por código que um
gate mais alto forçaria a cobrir — não por código difícil de testar:

| Módulo | Cobertura | Composição do gap até 0.98 |
|---|---|---|
| usecases | 100% | — |
| sql_persistence | 99.29% | — |
| domain | 97.86% | `Audit.now()`/`touch()` **sem nenhum teste**; bridges de default args de construtores/factories |
| http_api | 97.33% | `day` não-inteiro e `size` inválido sem teste; ~73 instruções de guards defensivos inalcançáveis (path param ausente — o Ktor responde 404 antes do handler) |

Cobrindo apenas o alcançável, domain chega a ~98.7% e http_api a ~98.4% — **0.98 fecha sem
nenhuma exclusão JaCoCo nova e sem tocar código de produção**. `buildSrc/**` é protegido por
política (ADR-0023): a mudança do threshold exige decisão registrada antes da edição.

**Pergunta a decidir**: subir o default para 0.98, criar thresholds por módulo, ou manter 0.97?

## Decision Drivers

- Gate deve refletir o nível real do projeto — folga excessiva permite regressão silenciosa.
- Um número único é mais barato de manter e comunicar do que N thresholds.
- Código defensivo genuinamente inalcançável não deve forçar exclusões novas (que escondem
  regressões futuras no mesmo arquivo).
- `buildSrc` é protegido: mudanças raras, deliberadas e documentadas.

## Considered Options

1. Default 0.98 no convention plugin (todos os módulos).
2. Thresholds por módulo (ex.: usecases 1.00, sql_persistence 0.99, domain/http_api 0.98).
3. Manter 0.97.

## Decision Outcome

**Escolhida: Opção 1 (default 0.98)**, porque um número único preserva a política simples que o
projeto sempre teve, os quatro módulos comportam 0.98 depois de cobrir o alcançável, e a subida
captura o ganho de cobertura já disponível em vez de deixá-lo evaporar em regressões dentro da
folga.

**Pré-requisito de implementação (mesmo PR do bump)**: cobrir o alcançável de domain e http_api
ANTES da subida — `Audit.now()`/`touch()`, defaults de construtores/factories, `day` não-inteiro,
`size` inválido. Guards defensivos inalcançáveis ficam descobertos e cabem no orçamento de 2% —
**nenhuma exclusão JaCoCo nova**. Atenção: módulos que sobrescrevem a rule localmente (http_api,
sql_persistence) precisam do mesmo bump em seus `build.gradle.kts`, senão o override em 0.97
vira o gate efetivo.

### Confirmation

O próprio gate `jacocoTestCoverageVerification` em `testAll` e no job `quality` do CI, com os
quatro módulos ≥ 0.98 no PR de implementação. O report madrapps do PR passa a exibir 98/98.
Validação negativa registrada no PR: desabilitar temporariamente um teste novo →
`jacocoTestCoverageVerification` FALHA → restaurar.

## Consequences

- Bom: o gate volta a exercer pressão real — regressões de cobertura em domain/http_api falham
  o build em vez de consumir folga silenciosamente.
- Bom: APIs hoje sem teste (Audit) ganham testes comportamentais como pré-requisito.
- Ruim: PRs futuros em domain/http_api têm menos folga (~0.5-0.7pp) — fricção intencional; código
  novo nasce coberto ou o build quebra.
- Nota: as ~73 instruções defensivas inalcançáveis do http_api consomem parte do orçamento de 2%
  permanentemente; se um dia forem removidas/refatoradas, a folga volta.

## Pros and Cons of the Options

### Opção 1 — Default 0.98

- Bom: uma política, um número; sem negociação por módulo.
- Bom: viável hoje nos 4 módulos sem exclusões novas (medição na tabela acima).
- Ruim: o teto é ditado pelo módulo mais apertado — módulos folgados ficam sub-exigidos
  (mitigável em ADR futura se a diferença crescer).

### Opção 2 — Thresholds por módulo

- Bom: máxima pressão onde há máxima cobertura (usecases 1.00).
- Ruim: N números para manter; cada PR apertado vira negociação do threshold local — o
  anti-padrão que a política de configs imutáveis existe para evitar.

### Opção 3 — Manter 0.97

- Bom: zero esforço.
- Ruim: desperdiça o ganho já conquistado; Audit segue sem teste até alguém notar por acaso.

## More Information

- Branch: `feat/adr-0029-jacoco-098` · PR: (preencher após abrir)
- Item no board #6: [GAP-AW](https://github.com/users/agnaldo4j/projects/6) — o plano de implementação vive lá, não aqui
- Referências: ADR-0023 (imutabilidade de configs), ADR-0028 (fluxo 2-PRs para arquivo protegido),
  medição em `docs/quality/` e relatórios JaCoCo do CI
