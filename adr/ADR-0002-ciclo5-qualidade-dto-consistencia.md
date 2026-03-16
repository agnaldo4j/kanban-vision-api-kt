# ADR-0002 — Ciclo 5 de Qualidade: Consistência de DTOs de Erro, Extração de ScenarioDtos e Cobertura de HealthRoutes

## Cabeçalho

| Campo     | Valor                                                        |
|-----------|--------------------------------------------------------------|
| Status    | Aceita                                                       |
| Data      | 2026-03-16                                                   |
| Autores   | @agnaldo4j                                                   |
| Branch    | feat/adr-0002-ciclo5-qualidade-dto-consistencia              |
| PR        | (preencher após abrir o PR)                                  |
| Supersede | —                                                            |

---

## Contexto e Motivação

O Ciclo 4 de qualidade (PRs #50 e #51, 2026-03-16) estabilizou o projeto em 9.2/10:
66 testes passando, ~93% de cobertura, zero violações Detekt/KtLint, todas as 5 fases
da ADR do simulador entregues.

O Ciclo 5 de avaliação (2026-03-16) identificou três gaps que não falham o build atual,
mas comprometem consistência de contratos de API, organização de arquivos e cobertura
de branches em testes:

1. **BoardRoutes.kt:43** usa `mapOf("error" to "Missing board id")` — única resposta de
   erro no projeto que não usa `respondWithDomainError()` + DTOs tipados. Todos os outros
   routes (ScenarioRoutes, CardRoutes, ColumnRoutes) usam o padrão correto.

2. **ScenarioRoutes.kt (380 linhas)** contém 9 data classes de DTO + a função de mapeamento
   `DailySnapshot.toResponse()` embutidas no mesmo arquivo dos handlers e specs OpenAPI.
   O arquivo tem 13 funções de nível de arquivo (limite Detekt: 15) e crescerá naturalmente
   conforme novos campos forem adicionados. A mistura de concerns (transport DTOs +
   handlers + specs) dificulta a navegação e aumenta o risco de violar o limite.

3. **HealthRoutesTest.kt (1 teste)** cobre apenas o caminho feliz (`GET /health` → 200).
   O caminho de erro (exceção não tratada → StatusPages → 500) está descoberto, reduzindo
   a confiança na cobertura de branches do módulo `http_api`.

Esta ADR é necessária porque os três gaps envolvem decisões de organização de código com
impacto em múltiplos arquivos: onde os DTOs vivem, como os erros são formatados e qual
o padrão mínimo de cobertura para rotas de infraestrutura.

---

## Forças (Decision Drivers)

- [ ] **Consistência de contratos** — toda resposta de erro da API deve usar DTOs tipados
      (`DomainErrorResponse`, `ValidationErrorResponse`), não `mapOf()` avulso
- [ ] **Limite Detekt TooManyFunctions** — arquivos com >15 funções falham o build;
      ScenarioRoutes.kt está em 13/15 e crescerá com novos endpoints
- [ ] **SRP (Single Responsibility Principle)** — um arquivo de routes não deve acumular
      a definição dos seus próprios DTOs de transporte
- [ ] **Cobertura JaCoCo ≥ 90%** — branches em rotas de infraestrutura devem ser testados
- [ ] **Nenhum arquivo de configuração de qualidade é modificado** (regra imutável do projeto)

---

## Opções Consideradas

- **Opção A**: Três correções pontuais — fix `mapOf`, extrai `ScenarioDtos.kt`, adiciona teste `HealthRoutes`
- **Opção B**: Ignorar os gaps — aceitar o débito técnico e lidar quando Detekt falhar
- **Opção C**: Refatoração maior — dividir `ScenarioRoutes.kt` em 3 arquivos de rota separados
  (ScenarioCreationRoutes, ScenarioQueryRoutes, ScenarioRunDayRoutes) além de extrair os DTOs

---

## Decisão

**Escolhemos Opção A** (três correções pontuais) porque cada item é cirúrgico e não altera
comportamento observável da API. A Opção B acumula risco de falha futura no Detekt. A
Opção C introduz refatoração não solicitada de handlers que já estão estáveis e testados —
viola o princípio de over-engineering para o volume atual de código. A extração dos DTOs
para `ScenarioDtos.kt` (Tarefa 2) é suficiente para reduzir o risco de violar o limite de
funções sem mover lógica de routing.

Esta opção satisfaz as forças de consistência, SRP e cobertura sem comprometer a
estabilidade dos 66 testes existentes nem exigir mudanças em configurações de qualidade.

---

## Análise das Opções

### Opção A — Três correções pontuais

**Prós:**
- Cada tarefa é independente e pode ser revisada isoladamente
- Não altera comportamento observável da API (nenhuma quebra de contrato)
- `ScenarioRoutes.kt` reduz de 380 → ~300 linhas, afastando do limite Detekt
- Elimina a única inconsistência de DTO em `BoardRoutes.kt`
- Adiciona cobertura de branch ao caminho de erro de `HealthRoutes`

**Contras:**
- `ScenarioRoutes.kt` ainda ficará com ~300 linhas após a extração (acima do ideal)
- Requer atenção ao mover imports para `ScenarioDtos.kt`

### Opção B — Ignorar os gaps

**Prós:**
- Zero esforço imediato

**Contras:**
- `mapOf()` em `BoardRoutes.kt` propaga para novos devs como padrão aceitável
- `ScenarioRoutes.kt` atingirá o limite de 15 funções na próxima adição de endpoint,
  causando falha de build inesperada
- Branches de erro em `HealthRoutes` permanecem descobertos

### Opção C — Refatoração maior de ScenarioRoutes

**Prós:**
- Arquivos de routes menores e mais focados
- Padrão alinhado com os arquivos de teste já divididos (ScenarioCreationRoutesTest, etc.)

**Contras:**
- Mover lógica de handlers estáveis aumenta risco de regressão
- Koin `inject()` deve ser replicado em cada arquivo ou centralizado em um objeto — decisão
  adicional não justificada pelos gaps atuais
- Over-engineering: 3 arquivos de routes para 4 endpoints é excessivo

---

## Consequências

**Positivas:**
- Toda resposta de erro na API passa a usar DTOs tipados — contrato uniforme
- `ScenarioRoutes.kt` fica sob 320 linhas, com margem de ~3 funções antes do limite Detekt
- `HealthRoutesTest.kt` cobre caminho de erro, aumentando confiança na cobertura de http_api
- DTOs de `ScenarioRoutes` ficam em arquivo próprio, facilitando busca e navegação

**Negativas / Trade-offs:**
- `ScenarioDtos.kt` cria um novo arquivo a conhecer; mitigado pelo nome autoexplicativo
- Extração requer atualizar imports em `ScenarioRoutes.kt`

**Neutras:**
- Nenhum endpoint novo; nenhuma mudança de comportamento de API
- Testes de ScenarioRoutes existentes continuam válidos sem modificação

---

## Plano de Implementação

- [x] **Tarefa 1** — `BoardRoutes.kt:43`: substituir `mapOf("error" to "Missing board id")`
      por `respondWithDomainError(DomainError.ValidationError(listOf("Missing board id")))`.
      Verificar que o import de `DomainError` já está presente no arquivo.

- [x] **Tarefa 2** — Criar `http_api/src/main/kotlin/com/kanbanvision/httpapi/routes/ScenarioDtos.kt`
      com o mesmo package `com.kanbanvision.httpapi.routes`.
      Mover para o novo arquivo:
      - A função de extensão `internal fun DailySnapshot.toResponse(): DailySnapshotResponse`
      - As 9 data classes: `CreateScenarioRequest`, `DecisionRequest`, `RunDayRequest`,
        `ScenarioCreatedResponse`, `SimulationStateResponse`, `ScenarioResponse`,
        `FlowMetricsResponse`, `MovementResponse`, `DailySnapshotResponse`
      Removidas do `ScenarioRoutes.kt`. Imports obsoletos removidos.

- [x] **Tarefa 3** — `HealthRoutesTest.kt`: adicionado teste `unhandled exception in application
      returns 500 with requestId` seguindo padrão de `StatusPagesTest.kt`.

- [x] **Tarefa 4** — `./gradlew testAll` — BUILD SUCCESSFUL, zero violações Detekt/KtLint,
      cobertura ≥ 90%

- [ ] **Tarefa 5** — Preencher o campo `PR` com a URL do PR aberto

---

## Garantias de Qualidade

### DOD — Definition of Done

- [ ] **1. Contrato e Rastreabilidade**: branch `feat/adr-0002-ciclo5-qualidade-dto-consistencia` ↔ PR ↔ CI rastreáveis
- [ ] **2. Testes Técnicos**: `HealthRoutesTest` com ≥ 2 testes (happy path + erro 500); todos os testes existentes verdes
- [ ] **3. Versionamento e Compatibilidade**: nenhuma mudança de contrato de API (sem campos removidos, sem renomeação de endpoints)
- [ ] **4. Segurança e Compliance**: N/A para esta ADR
- [ ] **5. CI/CD**: `./gradlew testAll` verde no CI (Detekt + KtLint + JUnit + JaCoCo ≥ 90%)
- [ ] **6. Observabilidade**: N/A para esta ADR
- [ ] **7. Performance e Confiabilidade**: N/A para esta ADR
- [ ] **8. Deploy Seguro**: sem migração de banco, sem rollback especial — N/A
- [ ] **9. Documentação**: C4 não precisa ser atualizado (nenhum novo módulo ou rota)

### Qualidade de Código

| Ferramenta | Requisito                                | Ação se falhar                               |
|------------|------------------------------------------|----------------------------------------------|
| Detekt     | zero violações (`warningsAsErrors=true`) | Refatorar — nunca suprimir sem justificativa |
| KtLint     | zero erros de formatação                 | `./gradlew ktlintFormat` antes do commit     |
| JaCoCo     | ≥ 90% de cobertura de instruções         | Escrever o teste faltante — nunca baixar o threshold |

> ⛔ **REGRA ABSOLUTA**: nenhum arquivo de configuração de qualidade será editado
> (`detekt.yml`, `.editorconfig`, `build.gradle.kts`, `gradle.properties`, convention plugin).

### Manutenibilidade SOLID

- [x] **SRP** — `ScenarioDtos.kt` tem uma única razão para mudar: mudança nos DTOs de transporte
- [x] **OCP** — nenhuma interface é modificada
- [x] **LSP** — N/A
- [x] **ISP** — N/A
- [x] **DIP** — nenhuma dependência nova introduzida

### Aderência à Arquitetura

- [x] **Dependency Rule**: `ScenarioDtos.kt` fica em `http_api` — sem violação
- [x] **Domain puro**: zero imports de framework em `domain/` — não alterado
- [x] **DTOs nas boundaries**: os DTOs movidos são de transporte HTTP — corretos em `http_api`
- [x] **Either para erros**: `respondWithDomainError()` mantém o padrão Either

---

## Referências

- Skill: [adr](.claude/skills/adr/SKILL.md)
- Skill: [definition-of-done](.claude/skills/definition-of-done/SKILL.md)
- Skill: [kotlin-quality-pipeline](.claude/skills/kotlin-quality-pipeline/SKILL.md)
- Avaliação Ciclo 4 — PRs #50 e #51 (2026-03-16)
- `BoardRoutes.kt` linha 43 — inconsistência de DTO identificada no Ciclo 5
- `ScenarioRoutes.kt` — 380 linhas, 13 funções (limite Detekt: 15)
- `HealthRoutesTest.kt` — 1 teste, caminho de erro descoberto