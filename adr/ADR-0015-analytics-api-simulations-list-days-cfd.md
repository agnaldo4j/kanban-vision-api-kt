# ADR-0015 — Analytics API: listagem paginada de simulações, série temporal e CFD

## Cabeçalho

| Campo     | Valor                                                              |
|-----------|--------------------------------------------------------------------|
| Status    | Aceita                                                             |
| Data      | 2026-03-25                                                         |
| Autores   | @agnaldo4j                                                         |
| Branch    | feat/gap-j-analytics-api                                           |
| PR        | https://github.com/agnaldo4j/kanban-vision-api-kt/pull/103         |
| Supersede | —                                                                  |

---

## Contexto e Motivação

O módulo `http_api` expõe endpoints de ciclo de vida de simulações (`POST /simulations`,
`GET /simulations/{id}`, `POST /simulations/{id}/run`,
`GET /simulations/{id}/days/{day}/snapshot`), mas não oferece:

1. **Listagem paginada de simulações** — clientes precisam descobrir todas as
   simulações de uma organização sem carregar cada uma individualmente.
2. **Série temporal de métricas** — dashboards precisam de todos os `DailySnapshot`
   de uma simulação para plotar throughput, WIP e lead time ao longo do tempo.
3. **Dados CFD (Cumulative Flow Diagram)** — derivados de *The Principles of
   Product Development Flow* (Reinertsen), o CFD mostra a acumulação de itens
   por Step ao longo dos dias, revelando gargalos e lead time distribution.

Esses três endpoints compõem o GAP-J do roadmap (ADR-0004), classificado `[M]`
porque introduz novos casos de uso e ports sem quebrar contratos existentes.
A decisão central é: **onde ocorre a agregação CFD e como a paginação é implementada**
respeitando a Dependency Rule.

---

## Forças (Decision Drivers)

- [x] Manter a Dependency Rule: `domain` e `usecases` sem imports de framework
- [x] Testabilidade: lógica de negócio testável com MockK, sem banco real
- [x] CQS: cada novo caso de uso aceita exatamente um `Query` object
- [x] Cobertura JaCoCo ≥ 96% por módulo (threshold atual do projeto)
- [x] Zero violações Detekt/KtLint
- [x] Paginação sem carregar tudo em memória no servidor

---

## Opções Consideradas

- **Opção A**: Agregação CFD no `usecases/` — lógica Kotlin pura sobre `DailySnapshot`
- **Opção B**: Agregação CFD em SQL — query de agregação no repositório Exposed
- **Opção C**: Agregação CFD na rota HTTP — lógica na camada `http_api`

---

## Decisão

**Escolhemos Opção A** (agregação no `usecases/`) porque mantém a Dependency Rule
intacta, permite testar a lógica CFD com MockK puro (sem banco), e o volume atual
(simulações de até 365 dias) não justifica a complexidade de SQL de agregação.
Se no futuro o volume crescer e a performance degradar, uma nova ADR documentará
a migração para consulta SQL (Opção B) com benchmark comparativo.

---

## Análise das Opções

### Opção A — Agregação no UseCase (escolhida)

**Prós:**
- Lógica testável com MockK sem banco real
- `domain/` e `usecases/` permanecem sem SQL ou framework
- Fácil de entender e manter
- Alinhado com os padrões existentes (`GetSimulationUseCase`, `GetDailySnapshotUseCase`)

**Contras:**
- Carrega todos os snapshots em memória para calcular CFD
- Performance pode degradar para simulações com muitos dias (> 1000)

### Opção B — Agregação em SQL

**Prós:**
- Performance superior para volumes grandes
- Banco executa agregações nativamente

**Contras:**
- Lógica de negócio em SQL → difícil de testar sem banco real
- Viola a Dependency Rule: usecase dependeria de SQL implicitamente
- Mudança de banco exige reescrever a lógica de agregação

### Opção C — Agregação na rota HTTP

**Prós:**
- Implementação rápida para protótipo

**Contras:**
- Viola Clean Architecture: regra de negócio na camada de entrega
- Não testável via testes unitários
- Acoplamento máximo entre domínio e framework

---

## Especificação dos Endpoints

### `GET /simulations?page=1&size=20`

Retorna lista paginada de simulações para a organização do token JWT.

**Response 200:**
```json
{
  "data": [
    { "id": "uuid", "name": "Sprint 1", "status": "COMPLETED", "currentDay": 30 }
  ],
  "page": 1,
  "size": 20,
  "total": 42
}
```

**Query params:** `page` (default 1, min 1), `size` (default 20, min 1, max 100).

---

### `GET /simulations/{id}/days`

Retorna série temporal de métricas para todos os dias executados.

**Response 200:**
```json
{
  "simulationId": "uuid",
  "days": [
    {
      "day": 1,
      "throughput": 3,
      "wipCount": 5,
      "blockedCount": 1,
      "avgAgingDays": 2.4
    }
  ]
}
```

---

### `GET /simulations/{id}/cfd`

Retorna dados para CFD: acumulação de itens por Step ao longo dos dias.

**Response 200:**
```json
{
  "simulationId": "uuid",
  "steps": ["Backlog", "Dev", "Test", "Done"],
  "series": [
    { "day": 1, "counts": [10, 3, 1, 0] },
    { "day": 2, "counts": [9, 4, 2, 1] }
  ]
}
```

O campo `counts[i]` representa o número de cards na step `steps[i]` no final do dia.

---

## Consequências

**Positivas:**
- Clientes da API podem listar e filtrar simulações sem N+1 requests
- Dashboards CFD e gráficos de throughput ficam viáveis
- Lógica de negócio (paginação, agregação CFD) totalmente testável

**Negativas / Trade-offs:**
- `/days` carrega todos os snapshots em memória: limitado a simulações ≤ 365 dias
  (mitigação: size limit no `SnapshotRepository.findAllBySimulation`)
- `/cfd` deriva movimentos de cards por Step de cada `DailySnapshot.movements`
  (informação já presente); se os movimentos não capturarem o estado por Step,
  a lógica precisará de ajuste

**Neutras:**
- Três novos use cases, três novos ports em `usecases/repositories/`
- Três novas rotas em `SimulationRoutes.kt` (arquivo existente)

---

## Plano de Implementação

### 1 — Ports em `usecases/repositories/`

- [ ] Adicionar `findAll(organizationId, page, size)` em `SimulationRepository`
- [ ] Adicionar `findAllBySimulation(simulationId)` em `SnapshotRepository`

### 2 — Use Cases em `usecases/simulation/`

- [ ] `ListSimulationsUseCase` + `ListSimulationsQuery(organizationId, page, size)`
- [ ] `GetSimulationDaysUseCase` + `GetSimulationDaysQuery(simulationId)`
- [ ] `GetSimulationCfdUseCase` + `GetSimulationCfdQuery(simulationId)`

### 3 — Persistência em `sql_persistence/`

- [ ] Implementar `findAll` em `JdbcSimulationRepository` com `LIMIT/OFFSET` Exposed
- [ ] Implementar `findAllBySimulation` em `JdbcSnapshotRepository` com `ORDER BY day`

### 4 — DTOs em `http_api/`

- [ ] `SimulationSummaryResponse`, `SimulationListResponse(data, page, size, total)`
- [ ] `DayMetricsResponse`, `SimulationDaysResponse(simulationId, days)`
- [ ] `CfdSeriesEntry`, `SimulationCfdResponse(simulationId, steps, series)`

### 5 — Rotas em `SimulationRoutes.kt`

- [ ] `GET /simulations` com OpenAPI spec e validação de `page`/`size`
- [ ] `GET /simulations/{id}/days` com OpenAPI spec
- [ ] `GET /simulations/{id}/cfd` com OpenAPI spec

### 6 — DI em `AppModule.kt`

- [ ] Registrar `ListSimulationsUseCase`, `GetSimulationDaysUseCase`, `GetSimulationCfdUseCase`

### 7 — Testes

- [ ] Testes unitários para os 3 use cases (MockK, sem banco)
- [ ] Testes de integração para os 2 repositórios (Embedded PostgreSQL)
- [ ] Testes de rota em `http_api/` (testApplication + JWT)
- [ ] `./gradlew testAll` verde

### 8 — Diagramas

- [ ] Executar skill `/c4-model` para atualizar README com novas rotas

---

## Garantias de Qualidade

### DOD

- [ ] **1. Rastreabilidade**: branch `feat/gap-j-analytics-api`, PR com URL na ADR
- [ ] **2. Testes Técnicos**: unitários (given–when–then para casos feliz + erro) e integração para cada boundary
- [ ] **3. Versionamento**: OpenAPI atualizado com os 3 novos endpoints, schemas documentados
- [ ] **4. Segurança**: rotas dentro de `authenticate("jwt-auth")`, `tenantId` validado em cada use case
- [ ] **5. CI/CD**: `./gradlew testAll` verde, sem testes flaky
- [ ] **6. Observabilidade**: MDC `simulationId` propagado nos logs dos use cases
- [ ] **7. Performance**: `size` máximo 100 em `/simulations`, limite de snapshots em `/days` e `/cfd`
- [ ] **8. Deploy**: sem migrações de schema (endpoints somente leitura)
- [ ] **9. Documentação**: skill `/c4-model` atualizado no README

### Qualidade de Código

| Ferramenta | Requisito       |
|------------|-----------------|
| Detekt     | 0 violações     |
| KtLint     | 0 erros         |
| JaCoCo     | ≥ 96% por módulo |

### Aderência à Arquitetura

- [ ] `domain/` sem novos imports de framework
- [ ] Ports em `usecases/repositories/`, implementações em `sql_persistence/`
- [ ] Rotas chamam use cases; use cases chamam repositories; domain é puro
- [ ] `Either<DomainError, T>` em todos os métodos de repositório e use cases

---

## Referências

- ADR-0004 — Avaliação de qualidade, gaps e prioridades (GAP-J)
- ADR-0014 — Exposed DSL em sql_persistence (padrão de persistência adotado)
- *The Principles of Product Development Flow* — Reinertsen (CFD e Little's Law)
- *Kanban from the Inside* — Burrows (classes de serviço e fluxo)
