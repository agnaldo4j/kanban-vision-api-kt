---
status: accepted
date: 2026-07-05
decision-makers: "@agnaldo4j"
---

# ADR-0026 — Fitness functions de arquitetura com Konsist em módulo test-only

## Context and Problem Statement

A arquitetura hexagonal do projeto é protegida hoje por convenção e por revisão: a Dependency
Rule (`http_api → usecases → domain`; `sql_persistence → domain/usecases`), a pureza do
`domain` (zero frameworks) e o placement dos ports em `usecases/repositories/` são verificados
à mão nas auditorias (`docs/quality/audit-2026-07.md`, dimensão flexibility) — o único gate
automatizado é o `ForbiddenImport` do Detekt para `Jdbc*Repository`. Uma violação estrutural
pode entrar num PR sem que nenhum gate falhe. Como transformar essas invariantes de
arquitetura em fitness functions executáveis no CI?

## Decision Drivers

- Auditoria manual não escala e não protege PRs entre auditorias.
- `config/detekt/detekt.yml` é imutável por política (ADR-0023/regras) — a solução não pode
  depender de editar regras Detekt.
- As regras devem viver como código de teste versionado, legível como documentação da
  arquitetura (fitness functions — Building Evolutionary Architectures).
- Custo de manutenção baixo: DSL idiomática Kotlin, integração JUnit 5 existente.

## Considered Options

1. Konsist (análise de fontes Kotlin, DSL Kotlin-first, guards como testes JUnit 5).
2. ArchUnit (análise de bytecode, ecossistema Java maduro).
3. Regras Detekt customizadas (plugin próprio de regras).

## Decision Outcome

**Escolhida: Opção 1 — Konsist 0.17.3** em um novo módulo Gradle test-only `architecture/`.
O Konsist analisa as fontes Kotlin por path (`scopeFromProject()`), então um módulo sem
código de produção enxerga o projeto inteiro; a DSL `assertArchitecture` expressa as camadas
hexagonais diretamente, e cada invariante vira um teste JUnit 5 nomeado. Regras iniciais:
direção das dependências entre camadas, pureza do `domain` (sem imports de framework) e
placement dos ports (`interface *Repository` só em `usecases.repositories`, implementações
em `persistence.repositories`). O módulo entra no `testAll` automaticamente via `check`.

### Confirmation

`:architecture:test` roda dentro de `./gradlew testAll` (job `quality` do CI) — qualquer
violação das fitness functions falha o gate obrigatório de PR. A cobertura das regras é
auditável lendo `architecture/src/test/kotlin/` como documentação executável da arquitetura.

**Correção de cache (requisito)**: o Konsist lê as fontes dos demais módulos em runtime,
fora dos inputs que o Gradle conhece para `:architecture:test` — com build cache ativo, um
PR que só muda `domain/` reutilizaria um resultado verde stale. O módulo DEVE declarar os
diretórios de fontes analisados (`<módulo>/src/main/kotlin`) como `inputs` da task `test`,
preservando o cache quando nada mudou e invalidando-o quando qualquer fonte de produção
mudar (preferível a `outputs.upToDateWhen { false }`, que desligaria o cache por completo).

## Consequences

- Bom: a Dependency Rule deixa de depender de revisão humana; violação estrutural quebra o CI.
- Bom: regras legíveis como documentação viva da arquitetura hexagonal.
- Ruim: dependência nova de test tooling (Konsist ainda pré-1.0); mitigação: isolada num
  módulo test-only, sem tocar código de produção — remoção barata se o projeto estagnar.
- Ruim: parser do Konsist acompanha versões de Kotlin com algum atraso; upgrades de Kotlin
  ganham um ponto extra de verificação (rodar `:architecture:test` no PR de upgrade).

## Pros and Cons of the Options

### Opção 1 — Konsist
- Bom: Kotlin-first (parseia fontes, entende declarações Kotlin); DSL de arquitetura pronta; JUnit 5; escopo por path permite módulo test-only.
- Ruim: pré-1.0; comunidade menor que ArchUnit.

### Opção 2 — ArchUnit
- Bom: maduro, amplamente adotado no mundo JVM.
- Ruim: opera sobre bytecode (classes Kotlin geram artefatos sintéticos que ruidam regras); DSL Java-first; exigiria classpath dos módulos analisados no módulo de teste.

### Opção 3 — Regras Detekt customizadas
- Bom: gate já existente, sem dependência nova de runtime de teste.
- Ruim: exigiria desenvolver e manter um plugin de regras próprio; ativação passa por editar `config/detekt/detekt.yml`, que é imutável por política; expressividade limitada para regras de camadas.

## More Information

- Branch: `feat/adr-0026-fitness-functions-konsist` · PR: https://github.com/agnaldo4j/kanban-vision-api-kt/pull/220
- Item no board #6: [GAP-AQ [M] — Fitness functions de arquitetura (Konsist)](https://github.com/users/agnaldo4j/projects/6) (ciclo P6) — plano de implementação vive lá e no PR 2/2, não aqui.
- Referências: `docs/quality/audit-2026-07.md` · `.claude/rules/architecture.md` (Dependency Rule) ·
  [Konsist](https://docs.konsist.lemonappdev.com/) · skill `/clean-architecture` ·
  ADR-0023 (política de ADRs) · ADR-0004 (histórico de gaps de qualidade).
