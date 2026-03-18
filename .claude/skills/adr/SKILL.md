argument-hint: "[ADR topic or gap description, e.g.: GAP-J pagination]"
allowed-tools: Read, Grep, Glob, Write, Edit, Bash
---
name: adr
description: >
  Crie, revise e mantenha Architecture Decision Records (ADRs) neste projeto.
  Use este skill ao propor qualquer mudança significativa de arquitetura, tecnologia,
  padrão ou design. Toda mudança relevante deve ter uma ADR aprovada antes do código
  ser escrito. A ADR garante explicitamente DOD, qualidade de código, manutenibilidade
  e aderência à arquitetura hexagonal do projeto.
---

# Architecture Decision Records (ADR)

> **Princípio central**: Decisões arquiteturais são irreversíveis no curto prazo.
> Documentar antes de implementar é mais barato do que refatorar depois de descobrir
> que a decisão estava errada. Uma ADR bem escrita é o melhor artefato de onboarding
> que um time pode ter.

---

## 1. Quando Escrever uma ADR

### Obrigatório — sem ADR, sem PR

| Categoria | Exemplos concretos |
|---|---|
| Nova tecnologia ou biblioteca | Adicionar Arrow-kt, Exposed, Redis, Kafka |
| Novo padrão arquitetural | Introduzir CQRS com event sourcing, saga pattern |
| Mudança de módulo Gradle | Criar novo subprojeto, remover módulo existente |
| Quebra de API pública | Renomear endpoint, mudar payload, remover campo |
| Decisão de persistência significativa | Novo esquema de tabelas, migração de banco, estratégia de serialização |
| Novo bounded context | Adicionar `analytics/`, `notifications/`, `billing/` |
| Escolha entre alternativas com trade-offs reais | JDBC vs. Exposed, Koin vs. Dagger, REST vs. gRPC |
| Mudança na estrutura de módulos ou camadas | Mover ports, reorganizar pacotes |

### Recomendado

| Categoria | Exemplos concretos |
|---|---|
| Refatorações internas de impacto médio | Extrair serviço de domínio, consolidar value objects |
| Novas rotas em domínio já existente | `ScenarioAnalyticsRoutes`, `TenantMetricsRoutes` |
| Melhorias de observabilidade | Novo middleware de MDC, estratégia de logging estruturado |

### Não necessário

| Categoria | Exemplos concretos |
|---|---|
| Bug fix sem mudança de comportamento | Corrigir validação, fix de NPE |
| Atualização de versão patch | `ktor 3.1.2 → 3.1.3` |
| Documentação pura | Atualizar README, corrigir docstring |
| Refatoração trivial | Renomear variável local, extrair constante |

---

## 2. Convenção de Nomenclatura

```
adr/ADR-NNNN-slug-descritivo.md
```

- `NNNN` — número sequencial com zero-padding (0001, 0002, …)
- `slug-descritivo` — kebab-case, 3–6 palavras que identificam a decisão
- Nunca reutilize um número, mesmo se a ADR for rejeitada

### Rastreabilidade ADR ↔ Branch ↔ Commit ↔ PR

| Artefato | Convenção | Exemplo |
|---|---|---|
| Arquivo ADR | `adr/ADR-NNNN-slug.md` | `adr/ADR-0002-scenario-analytics-routes.md` |
| Branch | `feat/adr-NNNN-slug` | `feat/adr-0002-scenario-analytics-routes` |
| Commit inicial | `docs(adr): ADR-NNNN — título curto` | `docs(adr): ADR-0002 — adicionar rotas de analytics de cenário` |
| PR title | `feat(adr-NNNN): título curto` | `feat(adr-0002): scenario analytics routes` |
| Campo PR na ADR | URL do PR no GitHub | `https://github.com/org/repo/pull/52` |

---

## 3. Template ADR Completo

Copie este template ao criar uma nova ADR. Todos os campos marcados com `*` são obrigatórios.

```markdown
# ADR-NNNN — Título Descritivo da Decisão *

## Cabeçalho

| Campo        | Valor                                      |
|--------------|--------------------------------------------|
| Status *     | Proposta / Aceita / Supersedida / Rejeitada / Descontinuada |
| Data *       | YYYY-MM-DD                                 |
| Autores *    | @handle                                    |
| Branch *     | feat/adr-NNNN-slug                         |
| PR           | (preencher após abrir o PR)                |
| Supersede    | (ADR-XXXX se substituir outra ADR)         |

---

## Contexto e Motivação *

> Descreva o problema ou necessidade que levou a esta decisão.
> Inclua: qual parte do sistema é afetada, quais requisitos ou restrições existem,
> e por que uma decisão explícita é necessária agora.

---

## Forças (Decision Drivers) *

- [ ] Força 1 — ex: manter o domínio livre de dependências de framework
- [ ] Força 2 — ex: garantir cobertura de testes ≥ 90%
- [ ] Força 3 — ex: evitar quebra de API para clientes existentes

---

## Opções Consideradas *

- **Opção A**: Nome curto — descrição em uma frase
- **Opção B**: Nome curto — descrição em uma frase
- **Opção C**: Nome curto — descrição em uma frase (se aplicável)

---

## Decisão *

> **Escolhemos [Opção X]** porque [justificativa clara e objetiva em 2–4 frases].
> Esta opção satisfaz as forças [lista as forças atendidas] sem comprometer [lista o que não é comprometido].

---

## Análise das Opções

### Opção A — Nome

**Prós:**
- Pro 1
- Pro 2

**Contras:**
- Contra 1
- Contra 2

### Opção B — Nome

**Prós:**
- Pro 1
- Pro 2

**Contras:**
- Contra 1
- Contra 2

### Opção C — Nome (se aplicável)

**Prós:**
- Pro 1

**Contras:**
- Contra 1

---

## Consequências *

**Positivas:**
- Consequência positiva 1
- Consequência positiva 2

**Negativas / Trade-offs:**
- Trade-off 1 (e como será mitigado)
- Trade-off 2

**Neutras:**
- Impacto neutro 1

---

## Plano de Implementação *

> Tarefas verificáveis. Cada item deve ser entregável de forma independente ou em sequência clara.

- [ ] Tarefa 1 — ex: criar interface `ScenarioAnalyticsRepository` em `usecases/repositories/`
- [ ] Tarefa 2 — ex: implementar `JdbcScenarioAnalyticsRepository` em `sql_persistence/`
- [ ] Tarefa 3 — ex: criar `GetScenarioAnalyticsUseCase` com `GetScenarioAnalyticsQuery`
- [ ] Tarefa 4 — ex: criar `ScenarioAnalyticsRoutes` com spec OpenAPI completa
- [ ] Tarefa 5 — ex: escrever testes unitários e de integração (cobertura ≥ 90%)
- [ ] Tarefa 6 — ex: executar `./gradlew testAll` — build verde
- [ ] Tarefa 7 — ex: atualizar diagramas C4 no README (skill c4-model)

---

## Garantias de Qualidade *

### DOD — Definition of Done

Antes de marcar o PR como pronto, confirmar cada item do skill `definition-of-done`:

- [ ] **1. Contrato e Rastreabilidade**: ticket ↔ branch ↔ PR ↔ build rastreáveis
- [ ] **2. Testes Técnicos**: unit tests (given–when–then, sucesso e erro), integration tests para cada boundary
- [ ] **3. Versionamento e Compatibilidade**: mudanças de API documentadas, OpenAPI atualizado
- [ ] **4. Segurança e Compliance**: sem secrets no código, PII protegido
- [ ] **5. CI/CD**: `./gradlew testAll` verde no CI, sem testes flaky
- [ ] **6. Observabilidade**: logs estruturados com MDC, métricas nos golden signals
- [ ] **7. Performance e Confiabilidade**: limites funcionais definidos, circuit breakers se aplicável
- [ ] **8. Deploy Seguro**: rollback documentado, health checks válidos
- [ ] **9. Documentação**: README / runbook atualizado, comunicação publicada

### Qualidade de Código

Toda entrega deve passar no pipeline completo:

| Ferramenta | Requisito | Ação se falhar |
|---|---|---|
| Detekt | zero violações (`warningsAsErrors = true`) | Refatorar — nunca suprimir sem justificativa |
| KtLint | zero erros de formatação | `./gradlew ktlintFormat` antes do commit |
| JaCoCo | ≥ 90% de cobertura de instruções por módulo | Escrever o teste faltante — nunca baixar o threshold |
| `@Suppress` | somente com comentário justificando | Sem justificativa = PR rejeitado |

> ⛔ **REGRA ABSOLUTA**: nenhum arquivo de configuração de qualidade será editado
> (`detekt.yml`, `.editorconfig`, `build.gradle.kts`, `gradle.properties`, convention plugin).
> Se o build falha, corrija o código. Veja skill `kotlin-quality-pipeline`.

### Manutenibilidade SOLID

- [ ] **SRP** — cada classe tem uma única razão para mudar
- [ ] **OCP** — extensível via interfaces/abstrações, não via modificação de código existente
- [ ] **LSP** — implementações são substituíveis por suas interfaces sem quebrar o sistema
- [ ] **ISP** — interfaces são coesas e focadas; nenhum cliente implementa métodos que não usa
- [ ] **DIP** — módulos de alto nível dependem de abstrações, não de implementações concretas

### Aderência à Arquitetura

- [ ] **Dependency Rule**: dependências de código-fonte apontam apenas para dentro (`domain ← usecases ← adapters`)
- [ ] **Ports-and-Adapters**: interfaces (ports) definidas em `usecases/repositories/`, implementações em `sql_persistence/`
- [ ] **CQS**: cada caso de uso aceita exatamente um `Command` (modifica) ou `Query` (lê), nunca primitivos avulsos
- [ ] **Either para erros**: erros de domínio modelados como `Either<DomainError, T>` via Arrow-kt
- [ ] **Domain puro**: zero imports de framework em `domain/` após a entrega
- [ ] **DTOs nas boundaries**: nenhum objeto de framework (`ApplicationCall`, `ResultSet`) cruza para dentro do domínio

---

## Referências

- Skill: [definition-of-done](..definition-of-done/SKILL.md)
- Skill: [clean-architecture](../clean-architecture/SKILL.md)
- Skill: [kotlin-quality-pipeline](../kotlin-quality-pipeline/SKILL.md)
- Skill: [solid-principles](../solid-principles/SKILL.md)
- ADR anterior relacionada: ADR-XXXX (se aplicável)
- Issue / ticket: (link)
```

---

## 4. Workflow ADR-First

### Fluxo em 8 passos

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  1. Proposta │────▶│  2. Escrita  │────▶│  3. Revisão  │────▶│  4. Aprovação│
│  (issue ou  │     │  (branch     │     │  (PR draft, │     │  (Status:   │
│   discussão)│     │   feat/adr-) │     │   feedback) │     │   Aceita)   │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                                                                      │
                                                                      ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  8. Merge   │◀────│ 7. Verif.   │◀────│  6. PR      │◀────│ 5. Impl.    │
│  (main)     │     │    DOD      │     │  (review,   │     │  (código na │
│             │     │             │     │   CI verde) │     │   branch)   │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
```

### Ciclo de vida do Status

| Status | Significado | Quem define |
|---|---|---|
| **Proposta** | ADR escrita, aguardando revisão do time | Autor |
| **Aceita** | Decisão aprovada, implementação autorizada | Revisor após aprovação |
| **Supersedida** | Substituída por ADR mais recente (referenciar nova ADR) | Autor da nova ADR |
| **Rejeitada** | Avaliada e descartada (registrar motivo) | Revisor |
| **Descontinuada** | Era válida, mas o contexto mudou (sem substituto imediato) | Time |

**Regra**: o status da ADR deve ser atualizado no mesmo PR que concluir a implementação.
Uma ADR com status `Proposta` após o merge indica processo incompleto.

---

## 5. Como Ligar ADR ao PR

### Commit inicial da ADR

```
docs(adr): ADR-NNNN — [título curto da decisão]

Documenta a decisão de [frase resumo].
Contexto: [1-2 frases sobre o problema].
Opções avaliadas: A (escolhida), B, C.
```

### Corpo do PR

```markdown
## ADR

Decisão documentada em: `adr/ADR-NNNN-slug.md`

## Contexto

[1-3 frases sobre o problema que motivou esta ADR]

## O que muda

- [Mudança 1]
- [Mudança 2]

## Checklist ADR

- [ ] ADR com status `Aceita` antes do primeiro commit de código
- [ ] Plano de implementação completo na ADR
- [ ] Garantias de Qualidade preenchidas na ADR
- [ ] `./gradlew testAll` verde
- [ ] Campo `PR` na ADR preenchido com a URL deste PR

## Test plan

- [ ] Testes unitários: [descreva os cenários]
- [ ] Testes de integração: [descreva os boundaries testados]
- [ ] CI pipeline verde

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

### Instrução final

Após abrir o PR, volte ao arquivo `adr/ADR-NNNN-slug.md` e preencha o campo `PR`
na tabela de cabeçalho com a URL completa do PR. Isso fecha o ciclo de rastreabilidade.

---

## 6. Exemplos: ADR Boa vs. Ruim

O exemplo usa `ScenarioAnalyticsRoutes` — um arquivo real deste projeto.

### Contexto ruim

```markdown
## Contexto

Precisamos adicionar analytics para cenários.
```

**Por que é ruim**: não explica o problema, não menciona restrições, não justifica
por que uma decisão explícita é necessária agora.

### Contexto bom

```markdown
## Contexto e Motivação

O módulo `http_api` expõe rotas de CRUD para cenários (`ScenarioRoutes`), mas não
há endpoints que retornem métricas agregadas de simulação — throughput diário,
distribuição de lead time, WIP médio por coluna.

Clientes da API precisam dessas métricas para construir dashboards. A lógica de
agregação envolve múltiplos dias de `DailySnapshot` e potencialmente queries pesadas.
Precisamos decidir se a agregação ocorre na camada de aplicação (usecase) ou diretamente
em SQL, pois essa escolha impacta testabilidade, performance e aderência à Dependency Rule.

Esta ADR é necessária agora porque existe PR em progresso que adiciona SQL de agregação
diretamente na rota, violando a separação de responsabilidades.
```

**Por que é boa**: descreve o sistema atual, o problema real, o impacto da decisão
e por que ela é urgente.

---

### Análise de opções ruim

```markdown
## Opções

- Opção A: usar usecase
- Opção B: usar SQL direto

Escolhemos A porque é melhor.
```

**Por que é ruim**: sem prós/contras, sem critérios, justificativa vazia.

### Análise de opções boa

```markdown
## Opções Consideradas

- **Opção A**: Agregação no `GetScenarioAnalyticsUseCase` — lógica em Kotlin sobre objetos de domínio
- **Opção B**: Agregação em SQL — query complexa em `JdbcScenarioAnalyticsRepository`
- **Opção C**: View materializada no PostgreSQL — pré-computada por trigger

### Opção A — Agregação no UseCase

**Prós:**
- Lógica testável com MockK puro, sem banco real
- Mantém `domain/` e `usecases/` sem SQL
- Fácil de entender e modificar

**Contras:**
- Carrega todos os `DailySnapshot` em memória para processar
- Performance degradada para cenários com muitos dias (> 365)

### Opção B — Agregação em SQL

**Prós:**
- Performance superior para grandes volumes de dados
- Banco faz o que faz bem (agregações)

**Contras:**
- Lógica de negócio em SQL → difícil de testar sem banco real
- Viola a intenção da Dependency Rule: usecase passa a depender de SQL implicitamente
- Mudança de banco exige reescrever a lógica de agregação

### Opção C — View Materializada

**Prós:**
- Consulta O(1) após atualização

**Contras:**
- Complexidade operacional: trigger de atualização, invalidação de cache
- Over-engineering para o volume atual de dados
- Não justificado pelas métricas de uso presentes

## Decisão

**Escolhemos Opção A** (Agregação no UseCase) porque mantém a Dependency Rule intacta,
garante testabilidade sem banco e o volume atual de dados (simulações de até 365 dias)
não justifica a complexidade das alternativas. Se o volume crescer, uma ADR de migração
para Opção B será emitida com benchmark comparativo.
```

---

## 7. Antipadrões

| Antipadrão | Problema | Correção |
|---|---|---|
| **ADR post-fato** | Código já está em produção quando a ADR é escrita | ADR-first: nenhum código antes da ADR aprovada |
| **ADR como changelog** | Descreve o que foi feito, não por que foi decidido | Foco no problema e nas alternativas rejeitadas |
| **Uma opção avaliada** | Sem alternativas, não é uma decisão — é uma especificação | Mínimo 2 opções com prós/contras reais |
| **Justificativa vaga** | "Escolhemos X porque é melhor/mais simples/recomendado" | Justificativa baseada em forças concretas e trade-offs |
| **Plano sem tarefas** | Plano de implementação com itens não verificáveis | Cada item deve ser um entregável concreto com `- [ ]` |
| **Garantias ignoradas** | Seção de qualidade em branco ou copiada sem reflexão | Cada item confirmado explicitamente ou marcado N/A com justificativa |
| **Status nunca atualizado** | ADR permanece como `Proposta` após o merge | Atualizar status para `Aceita` no PR de implementação |
| **Sem número sequencial** | `adr/analytics.md`, `adr/nova-rota.md` | Sempre `ADR-NNNN-slug.md` — número é imutável |
| **ADR supersedida sem referência** | ADR antiga não menciona a nova | Adicionar `Supersede: ADR-NNNN` na ADR antiga e nova |
| **Múltiplas decisões em uma ADR** | "ADR sobre analytics E autenticação E cache" | Uma decisão por ADR — se estão acopladas, documente explicitamente |

---

## 8. Checklist Rápido Pré-PR

Use antes de marcar o PR como pronto para revisão.

### ADR

- [ ] Arquivo nomeado corretamente: `adr/ADR-NNNN-slug-descritivo.md`
- [ ] Status atualizado para `Aceita`
- [ ] Mínimo 2 opções com prós/contras completos
- [ ] Justificativa da decisão baseada nas forças declaradas
- [ ] Plano de implementação com todas as tarefas marcadas como concluídas

### Rastreabilidade

- [ ] Campo `Branch` preenchido na ADR
- [ ] Campo `PR` preenchido com URL após abertura do PR
- [ ] Commit inicial da ADR segue a convenção `docs(adr): ADR-NNNN — título`
- [ ] Se supersede outra ADR, ambas referenciam-se mutuamente

### Garantias

- [ ] `./gradlew testAll` verde (Detekt + KtLint + testes + JaCoCo ≥ 90%)
- [ ] Todos os itens do DOD confirmados ou marcados N/A com justificativa
- [ ] Dependency Rule verificada: nenhum import de framework em `domain/` ou `usecases/`
- [ ] Diagramas C4 atualizados se um novo módulo, rota ou caso de uso foi adicionado