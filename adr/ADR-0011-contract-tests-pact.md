# ADR-0011 — Testes de Contrato com Pact JVM (GAP-K)

## Cabeçalho

| Campo     | Valor                                                              |
|-----------|--------------------------------------------------------------------|
| Status    | Aceita                                                             |
| Data      | 2026-06-06                                                         |
| Autores   | @agnaldo4j                                                         |
| Branch    | feat/gap-k-contract-tests-pact                                     |
| PR        | (preencher após abrir o PR)                                        |
| Supersede | —                                                                  |

---

## Contexto e Motivação

O módulo `http_api` expõe 7 endpoints de simulação e 3 de infraestrutura. A suíte atual
cobre rotas individualmente com `testApplication` (Ktor) e serialização de DTOs com
`RouteDtosContractTest` — porém essa abordagem valida apenas estrutura interna:

- `RouteDtosContractTest` verifica serialização/desserialização de DTOs via
  `kotlinx.serialization` e é puramente estática. Não detecta quebras de contrato
  HTTP: mudanças de código de status, headers obrigatórios, campos renomeados no payload,
  ou regras de autenticação alteradas.
- Não há consumidores externos declarados. Se um consumidor (dashboard, CLI, serviço
  downstream) depende de `GET /simulations/{id}/days`, nenhum teste detecta automaticamente
  que o campo `avgAgingDays` foi renomeado para `averageAgingDays` em uma refatoração.
- O roadmap (GAP-R e GAP-M) prevê extração de módulos para microserviços. Sem testes de
  contrato, a extração ocorre sem rede de segurança de compatibilidade: a primeira falha
  de contrato aparece em produção.

Esta ADR documenta a decisão de adotar **consumer-driven contract testing** com Pact JVM
como rede de segurança para a API de simulação antes que a arquitetura evolua.

---

## Forças (Decision Drivers)

- [x] **Detecção precoce de quebra de contrato**: nenhuma refatoração de DTO deve passar
  despercebida se quebrar o contrato HTTP de um consumidor declarado
- [x] **Dependency Rule preservada**: testes de contrato vivem em `http_api/test/` —
  nenhuma dependência de contrato entra em `domain/` ou `usecases/`
- [x] **Testabilidade sem infraestrutura externa**: pacts gerados como arquivos JSON locais;
  nenhum broker externo obrigatório para o ciclo de desenvolvimento local
- [x] **Alinhamento com evolução para microserviços**: formato Pact é portável — os pacts
  gerados aqui servirão de base quando GAP-R extrair serviços
- [x] **JaCoCo ≥ 96% por módulo**, zero violações Detekt/KtLint preservados
- [x] **GAP-G já entregue**: Docker disponível para executar Pact Broker em CI se necessário

---

## Opções Consideradas

- **Opção A**: Pact JVM — consumer-driven contract testing (CDC), pact files gerados pelos consumidores
- **Opção B**: Spring Cloud Contract — contract-first no provider, geração de stubs para consumers
- **Opção C**: Karate ou Dredd — testes de snapshot de API baseados em exemplos do OpenAPI

---

## Decisão

**Escolhemos Opção A** (Pact JVM) porque é a única abordagem consumer-driven — o consumidor
declara o que espera, não o provider impõe o contrato. Isso alinha diretamente com o cenário
de extração de microserviços do roadmap (GAP-R/GAP-M): quando um serviço downstream for
extraído, ele traz seu pact consigo. A Opção B (Spring Cloud Contract) exige que o provider
defina o contrato, invertendo o fluxo de dependência de conhecimento. A Opção C não é
consumer-driven — detecta regressões mas não representa expectativas declaradas por consumidores.

Como não há consumidores externos hoje, os testes Pact serão escritos como **self-consumer**:
o módulo `http_api` define o contrato que ele mesmo se compromete a honrar, do ponto de vista
de um cliente HTTP genérico. Isso estabelece a baseline antes que consumidores reais surjam.

**Risco resolvido (spike 2026-06-06)**: Pact JVM 4.6.17 é **compatível** com JUnit
Jupiter 6.0.3. A extensão `PactConsumerTestExt` carrega e executa corretamente. O pact
file V4 é gerado em `build/pacts/`. A API correta para V4 é `PactBuilder → V4Pact`
via `expectsToReceiveHttpInteraction()` — não `PactDslWithProvider → RequestResponsePact`
(API V3 legada). Ver commit `5327037` no branch `feat/gap-k-contract-tests-pact`.

---

## Análise das Opções

### Opção A — Pact JVM (consumer-driven)

**Prós:**
- Consumidor declara expectativas explícitas → provider é notificado de quebras antes do deploy
- Pact files são artefatos versionáveis e portáveis entre serviços
- Ecossistema maduro: suporte a Kotlin, JUnit Jupiter, Ktor (via cliente HTTP genérico)
- Pact Broker (Docker, open source) disponível para CI sem custo adicional
- Formato industria-padrão para CDC — times downstream já conhecem

**Contras:**
- Exige setup inicial maior que os outros: consumer test + provider verification + pact file
- Risco de compatibilidade com JUnit Jupiter 6.0.3 (requer spike antes da implementação)
- Self-consumer tests têm valor limitado vs. consumer real — mas estabelecem a baseline

### Opção B — Spring Cloud Contract

**Prós:**
- Provider-centric: o provider define o contrato e gera stubs para consumers
- Integração nativa com o ecossistema Spring Boot

**Contras:**
- Provider-driven inverte o fluxo CDC — consumidores recebem stubs, não definem expectativas
- Sem alinhamento com a stack Ktor/Koin do projeto (Spring não é usado)
- Não escala bem para extração de microserviços heterogêneos

### Opção C — Karate ou Dredd (snapshot de API)

**Prós:**
- Simples de configurar: aponta para o OpenAPI spec e executa requests reais
- Zero dependência de biblioteca adicional no projeto Kotlin

**Contras:**
- Não é consumer-driven: testa o provider contra sua própria spec, não as expectativas do consumidor
- Dredd não detecta que um campo renomeado quebrou um consumidor específico
- Karate seria outra linguagem (Gherkin/JavaScript) — diverge do estilo Kotlin do projeto
- Nenhum dos dois produz artefatos portáveis para o cenário de microserviços

---

## Consequências

**Positivas:**
- Toda refatoração de DTO ou rota vermelha se um consumidor declarado depende daquele campo
- Pact files em `build/pacts/` documentam o contrato de forma legível por máquina
- Baseline estabelecida para extração de microserviços (GAP-R/GAP-M) sem renegociação de contrato

**Negativas / Trade-offs:**
- Dois novos tipos de teste na suíte: consumer test (gera pact) + provider verification (lê pact)
- Spike de compatibilidade JUnit 6 + Pact pode indicar necessidade de versão específica
- Self-consumer tests têm valor de detecção menor que consumer real — mas são o ponto de partida correto

**Neutras:**
- Pact Broker não é obrigatório para o ciclo local; arquivos JSON em `build/pacts/` são suficientes
- JaCoCo coverage: testes Pact cobrem as rotas existentes — impacto neutro ou positivo no coverage

---

## Superfície Contratada

Os endpoints abaixo são os **contratos prioritários** para a primeira iteração, pois representam
os fluxos mais prováveis de serem consumidos por clientes externos ou serviços downstream:

| Endpoint | Método | Contrato consumer espera |
|---|---|---|
| `POST /api/v1/simulations` | POST | `201 Created`, body `{ simulationId: string }` |
| `GET /api/v1/simulations/{id}` | GET | `200 OK`, body `SimulationResponse` completo |
| `GET /api/v1/simulations/{id}/days` | GET | `200 OK`, body `SimulationDaysResponse` com série temporal |
| `GET /api/v1/simulations/{id}/cfd` | GET | `200 OK`, body `SimulationCfdResponse` com pontos CFD |
| `GET /api/v1/simulations` | GET | `200 OK`, body `SimulationListResponse` paginado |

Endpoints excluídos do escopo Pact inicial:
- `POST /api/v1/simulations/{id}/run` — ação de comando; contrato simples, coberto por route tests
- `GET /api/v1/simulations/{id}/days/{day}/snapshot` — derivado de `/days`, menor prioridade
- `/health`, `/metrics`, `/auth/token` — infraestrutura, não domínio de negócio

---

## Plano de Implementação

- [x] **1. Spike de compatibilidade** — `au.com.dius.pact.consumer:junit5:4.6.17` adicionado a
  `http_api/build.gradle.kts`; `@ExtendWith(PactConsumerTestExt::class)` compila e executa com
  JUnit Jupiter 6.0.3; pact file V4 gerado em `build/pacts/`; `testAll` verde
- [x] **2. Consumer test — POST /simulations** — `SimulationsCommandsConsumerPactTest`
  com interação `201 Created`, body com `stringType("simulationId")`, type-matched
- [x] **3. Consumer test — GET /simulations/{id}** — `SimulationsQueriesConsumerPactTest`,
  interação `200 OK` com todos os campos de `SimulationResponse` type-matched incluindo
  nested `state` object com `integerType`
- [x] **4. Consumer test — GET /simulations/{id}/days e /cfd** — interações com
  `eachLike("days")` + `decimalType("avgAgingDays")` e `eachLike("series")`
  + `integerType("throughputCumulative")`
- [x] **5. Consumer test — GET /simulations (lista paginada)** — interação com
  `eachLike("data")` + `integerType("page", "size", "total")`
- [ ] **6. Provider verification** — escrever `SimulationsPactProviderTest` usando
  `@Provider` + `@PactFolder("build/pacts")` e `@State` para cada interação; usar
  `testApplication` do Ktor como provider real (não mock)
- [ ] **7. Integração com `testAll`** — verificar que `./gradlew testAll` inclui
  os novos testes Pact; ajustar `test { }` task se necessário para que pact consumer
  e provider verification rodem no mesmo ciclo de CI
- [ ] **8. JaCoCo e Detekt** — confirmar que `./gradlew testAll` mantém cobertura ≥ 96%
  e zero violações Detekt/KtLint; ajustar exclusões de JaCoCo se necessário para
  classes geradas por Pact
- [ ] **9. CI pipeline** — verificar que `./gradlew testAll` no GitHub Actions inclui
  a verificação Pact e que os arquivos `build/pacts/*.json` são arquivados como artefatos
  de CI (14 dias, junto com JaCoCo e PITest)
- [ ] **10. ADR e documentação** — marcar GAP-K `[x]` em `ADR-0004`, atualizar
  `memory/project_adr_progress.md`, preencher campo `PR` nesta ADR, e fechar o item no
  board GitHub Project #6

---

## Garantias de Qualidade

### DOD — Definition of Done

- [ ] **1. Contrato e Rastreabilidade**: branch `feat/gap-k-contract-tests-pact` ↔ PR ↔ CI
- [ ] **2. Testes Técnicos**: consumer pact tests (5 interações) + provider verification; ambos passam em `./gradlew testAll`
- [ ] **3. Versionamento e Compatibilidade**: pact files em `build/pacts/` com versão do consumer declarada; nenhum contrato existente quebrado
- [ ] **4. Segurança e Compliance**: testes Pact não expõem secrets; JWT mockado nos testes consumer
- [ ] **5. CI/CD**: `./gradlew testAll` verde no CI com pact consumer + provider; pacts arquivados como artefatos
- [ ] **6. Observabilidade**: N/A — nenhuma nova métrica de negócio introduzida neste gap
- [ ] **7. Performance e Confiabilidade**: testes Pact são unit/integration level — sem degradação de tempo de build significativa
- [ ] **8. Deploy Seguro**: N/A — sem mudança de schema ou migração
- [ ] **9. Documentação**: campo `PR` preenchido nesta ADR; ADR-0004 atualizado com GAP-K `[x]`

### Qualidade de Código

| Ferramenta | Requisito | Ação se falhar |
|---|---|---|
| Detekt | zero violações | Refatorar — nunca suprimir sem justificativa |
| KtLint | zero erros | `./gradlew ktlintFormat` antes do commit |
| JaCoCo | ≥ 96% por módulo | Escrever o teste faltante |

### Aderência à Arquitetura

- [ ] **Dependency Rule**: testes Pact vivem em `http_api/test/` — zero imports de `Pact*` em `domain/` ou `usecases/`
- [ ] **DTOs nas boundaries**: provider verification usa os mesmos DTOs do módulo `http_api/routes/`
- [ ] **Domain puro**: nenhuma alteração em `domain/` — esta ADR é puramente de teste
- [ ] **CQS preservado**: contratos testados são os mesmos endpoints já existentes — sem novos use cases

---

## Referências

- ADR-0010 — Domain Events (GAP-H): eventos de domínio publicados que alimentam métricas observáveis pelos contratos
- ADR-0004 — Gap Prioritization: GAP-K `[E]` (Estrutural) — requer ADR antes de qualquer código
- GAP-R (futuro): Extração de módulo `analytics` para microserviço — os pacts aqui serão reutilizados
- Pact JVM: https://docs.pact.io/implementation_guides/jvm
- Pact consumer JUnit 5: `au.com.dius.pact.consumer:junit5`
- `RouteDtosContractTest` — testes de serialização existentes; complementares (não substituídos) por Pact
