---
name: microservices-modular-monolith
description: >
  Avalie, projete e evolua a arquitetura deste projeto entre os espectros de monólito
  modular e microserviços. Use este skill ao decidir como organizar módulos, definir
  boundaries entre domínios, planejar uma migração incremental ou justificar a escolha
  de permanecer como monólito. Complementa clean-architecture, ddd e screaming-architecture.
argument-hint: "[module or boundary to evaluate (optional)]"
allowed-tools: Read, Grep, Glob
---

# Microservices Patterns & Monólito Modular

> *"Almost all the successful microservice stories have started with a monolith that got
> too big and was broken up. Almost all the cases where I've heard of a system being built
> as a microservice system from scratch, it has ended up in serious trouble."*
> — Martin Fowler, *MonolithFirst*, 2015
> Fonte: https://martinfowler.com/bliki/MonolithFirst.html

> *"The microservice architecture structures an application as a set of loosely coupled,
> deployable/executable components organized around business capabilities."*
> — Chris Richardson, *microservices.io*
> Fonte: https://microservices.io/patterns/microservices.html

---

## O Espectro Arquitetural

Existem três pontos fundamentais no espectro entre simplicidade e distribuição:

```
┌──────────────────┐     ┌──────────────────────┐     ┌────────────────────┐
│  Monólito        │     │  Monólito Modular     │     │  Microserviços     │
│  Tradicional     │────►│  (Domain-Oriented)    │────►│                   │
│                  │     │                       │     │                   │
│ • Deploy único   │     │ • Deploy único        │     │ • Deploy separado  │
│ • Sem boundaries │     │ • Módulos por domínio │     │ • Por serviço      │
│ • Alta coesão    │     │ • APIs públicas       │     │ • Rede entre svcs  │
│ • Alto acoplam.  │     │ • Baixo acoplamento   │     │ • DB por serviço   │
└──────────────────┘     └──────────────────────┘     └────────────────────┘
    Complexidade ─────────────────────────────────────────────────────────►
    Autonomia    ─────────────────────────────────────────────────────────►
    Overhead ops ─────────────────────────────────────────────────────────►
```

**Este projeto é atualmente um Monólito Modular.** Quatro módulos Gradle
(`domain`, `usecases`, `sql_persistence`, `http_api`) com boundaries explícitas,
deploy único, banco compartilhado. É o ponto certo para o estágio atual.

---

## I. MonolithFirst — O Princípio Fundamental

### O Argumento de Fowler

Fowler observa um padrão consistente na indústria:

1. **Sistemas bem-sucedidos com microserviços começaram como monólitos** que cresceram
   e foram desmembrados quando as boundaries se tornaram claras.
2. **Sistemas construídos como microserviços desde o início** frequentemente enfrentam
   problemas sérios — boundaries erradas são muito mais caras de corrigir em serviços
   distribuídos do que em um monólito.

### Por que Começar como Monólito?

| Razão | Explicação |
|---|---|
| **Boundaries são difíceis de descobrir antecipadamente** | Você só entende as fronteiras corretas depois de operar o sistema em produção |
| **O Microservice Premium** | Microserviços adicionam overhead operacional significativo que só se paga em sistemas complexos |
| **Princípio YAGNI** | No início, velocidade de experimentação > autonomia de deployment |
| **Refatoração de boundaries é mais fácil em monólito** | Mover código entre módulos é trivial; mover entre serviços requer APIs, migrações de dados, contratos |

### Três Caminhos após o Monólito

```
Monólito Modular
      │
      ├─── 1. Peeling Gradual ────► extrair serviço por serviço, mantendo o núcleo
      │
      ├─── 2. Sacrificial Architecture ────► construir para aprender, depois reescrever
      │
      └─── 3. Coarse-Grained Split ────► dividir em 2-3 serviços grandes, subdividir depois
```

---

## II. Monólito Modular

### Definição

> *"A modular monolith is an architectural pattern that structures the application into
> independent modules or components with well-defined boundaries."*
> Fonte: https://www.milanjovanovic.tech/blog/what-is-a-modular-monolith

Um Monólito Modular combina:
- **Deploy unificado** de um monólito tradicional (simplicidade operacional)
- **Modularidade orientada a domínio** de microserviços (clareza de boundaries)

É o melhor dos dois mundos para sistemas de complexidade média que ainda não justificam
a sobrecarga operacional de serviços distribuídos.

### Exemplos Reais

| Empresa | Sistema | Estratégia |
|---|---|---|
| **Shopify** | E-commerce platform | Monólito Rails modular com ~20 domínios internos |
| **GitHub** | Repositórios e pull requests | Monólito Ruby modular, extraiu serviços cirurgicamente |
| **Stack Overflow** | Q&A platform | Monólito .NET de alto desempenho, sem microserviços |

### Estrutura do Monólito Modular

```
┌─────────────────────────────────────────────────────────────┐
│  Single Deployment Unit                                       │
│                                                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Módulo A   │  │  Módulo B   │  │  Módulo C           │  │
│  │  Kanban     │  │  Analytics  │  │  Tenant/Billing     │  │
│  │             │  │             │  │                     │  │
│  │  ┌────────┐ │  │  ┌────────┐ │  │  ┌────────────────┐ │  │
│  │  │ API    │ │  │  │ API    │ │  │  │ API            │ │  │
│  │  │(pública│ │  │  │(pública│ │  │  │(pública)       │ │  │
│  │  └────────┘ │  │  └────────┘ │  │  └────────────────┘ │  │
│  │  ┌────────┐ │  │  ┌────────┐ │  │  ┌────────────────┐ │  │
│  │  │ Impl   │ │  │  │ Impl   │ │  │  │ Impl           │ │  │
│  │  │(privada│ │  │  │(privada│ │  │  │(privada)       │ │  │
│  │  └────────┘ │  │  └────────┘ │  │  └────────────────┘ │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐   │
│  │  Shared Infrastructure (DB, Cache, Message Broker)    │   │
│  └───────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Princípios de Design do Módulo

1. **Alta coesão dentro do módulo** — tudo que muda junto fica junto
2. **Baixo acoplamento entre módulos** — módulos comunicam apenas via API pública
3. **Encapsulamento da implementação** — a implementação interna é privada; só a API é pública
4. **Organização por domínio, não por camada técnica** — `kanban/`, `analytics/`, não `controllers/`, `services/`
5. **Unidirecional** — dependências entre módulos seguem direção definida; sem ciclos

---

## III. Os Três Padrões do Monólito Modular para Fast Flow

Fonte: Chris Richardson — https://microservices.io/post/architecture/2024/09/09/modular-monolith-patterns-for-fast-flow.html

### Padrão 1: Modular Monolith (Domain-Oriented Monolith)

> *"Organize a monolith as a collection of loosely coupled, domain modules that are based
> on DDD subdomains/bounded contexts rather than technical layers."*

**O que é:** Estruturar o código ao redor de subdomínios/bounded contexts de DDD, não de
camadas técnicas (controllers, services, repositories como pastas de primeiro nível).

**Benefícios:**
- Gerencia complexidade em codebases grandes
- Melhora autonomia de time mesmo em codebase único
- Alinhameno natural com bounded contexts do DDD

**Aplicação neste projeto:**

```
// ❌ Organização por camada técnica (evitar)
src/
  controllers/
    BoardController.kt
    ScenarioController.kt
  services/
    BoardService.kt
  repositories/
    BoardRepository.kt

// ✅ Organização por domínio (padrão atual do projeto)
domain/           ← regras de negócio puras
usecases/         ← casos de uso por domínio (board/, card/, scenario/)
sql_persistence/  ← adaptadores de persistência
http_api/routes/  ← adaptadores HTTP por domínio
```

**Evolução futura:** quando novos bounded contexts surgirem (Analytics, Tenant Management),
eles ganham módulos Gradle dedicados — não pastas dentro dos módulos existentes.

---

### Padrão 2: Domain Module API

> *"Encapsulate each domain module's implementation details, which includes its database
> schema, behind a stable, facade-style API."*

**O que é:** Cada módulo expõe uma API pública estável (interfaces/facades) e esconde sua
implementação. Outros módulos dependem apenas da API, nunca dos internos.

**Benefícios:**
- Reduz acoplamento em tempo de design entre módulos
- Melhora testabilidade (mock da API, não dos internos)
- Esconde detalhes de implementação que podem mudar

**Aplicação neste projeto:**

```kotlin
// ✅ API pública do módulo usecases — o que outros módulos podem ver
interface BoardRepository    // em usecases/repositories/
interface ScenarioRepository // em usecases/repositories/
class CreateBoardUseCase     // em usecases/board/
class RunDayUseCase          // em usecases/scenario/

// ❌ Privado ao módulo sql_persistence — ninguém de fora deveria depender diretamente
class JdbcBoardRepository    // implementação interna
class DatabaseFactory        // infraestrutura interna
class SimulationStateSerializer // detalhe de serialização
```

**Regra:** `http_api` nunca importa `JdbcBoardRepository` diretamente. Sempre injeta
`BoardRepository` (a interface pública). O `AppModule` do Koin é o único ponto de
wiring — e é proposital.

**Evolução:** ao extrair `sql_persistence` como microserviço separado, a `BoardRepository`
se torna um cliente HTTP — o `http_api` e `usecases` não precisam mudar uma linha.

---

### Padrão 3: Domain API Build Module

> *"Define a separate build module (e.g. Gradle subproject) for the domain module's API,
> which the domain module's clients depend upon."*

**O que é:** Separar o contrato (interfaces, DTOs de entrada/saída) da implementação
em módulos Gradle diferentes. Clientes dependem apenas do módulo de contrato.

**Benefícios:**
- Reduz acoplamento em tempo de build — compilar apenas o contrato, não a implementação
- Acelera o pipeline de CI/CD (mudanças de implementação não recompilam os clientes)
- Torna explícita a separação API/implementação

**Estrutura alvo (quando o projeto crescer):**

```
// Estrutura atual
usecases/        ← API (interfaces de repositório + use cases) + implementação misturados

// Estrutura evoluída com Domain API Build Module
usecases-api/    ← apenas interfaces + commands + queries + domain types
usecases-impl/   ← implementações dos use cases, depende de usecases-api
http_api/        ← depende de usecases-api (não de usecases-impl!)
```

```kotlin
// usecases-api/build.gradle.kts
dependencies {
    api(project(":domain"))
    // APENAS interfaces — sem implementações
}

// usecases-impl/build.gradle.kts
dependencies {
    implementation(project(":usecases-api"))
    // implementações dos use cases aqui
}

// http_api/build.gradle.kts
dependencies {
    implementation(project(":usecases-api"))  // ← só a API!
    runtimeOnly(project(":usecases-impl"))    // ← implementação só em runtime
}
```

**Nota:** O projeto atual está na estrutura simples (4 módulos). A separação API/impl
se justifica quando o build time do módulo de implementação passa a impactar o pipeline.

---

## IV. Microserviços

### Definição

> *"The microservice architecture structures an application as a set of loosely coupled,
> deployable/executable components organized around business capabilities."*

Cada serviço:
- Tem seu **próprio processo** de deployment
- Tem seu **próprio banco de dados** (Database per Service pattern)
- Comunica via **rede** (HTTP/REST, gRPC, mensageria)
- Pode usar **stacks tecnológicas diferentes**
- É deployado **independentemente**

### As Duas Forças em Tensão (Chris Richardson)

Chris Richardson usa a metáfora de **Dark Energy** (forças centrífugas) e **Dark Matter**
(forças centrípetas) para explicar por que nem tudo deve ser microserviço.

**Dark Energy — forças que favorecem a separação:**

| Força | Descrição |
|---|---|
| Simple components | Componentes menores são mais fáceis de entender e manter |
| Team autonomy | Times desenvolvem e deployam independentemente |
| Fast deployment pipeline | Deploy de um serviço sem afetar outros |
| Technology diversity | Escolher a stack certa para cada problema |
| Segregation by characteristics | Escalar apenas o que precisa escalar |

**Dark Matter — forças que favorecem a integração:**

| Força | Descrição |
|---|---|
| Simple interactions | Operações distribuídas são mais complexas que chamadas locais |
| Efficient interactions | Latência de rede vs. chamada em memória |
| Prefer ACID transactions | Transações distribuídas (sagas) são muito mais complexas |
| Minimize runtime coupling | Serviços indisponíveis afetam outros serviços |
| Minimize design-time coupling | Mudanças em um serviço não devem exigir mudanças lockstep em outros |

**Quando as forças Dark Energy dominam Dark Matter, extraia para microserviço.**

### Padrões de Microserviços

#### Comunicação

| Padrão | Quando usar |
|---|---|
| **API Gateway** | Ponto de entrada único para clientes externos; roteamento, auth, rate limiting |
| **Backends for Frontends (BFF)** | API Gateway especializado por tipo de cliente (web, mobile) |
| **Service Mesh** | Comunicação serviço-a-serviço com observabilidade, retry, circuit breaking |

#### Dados

| Padrão | Quando usar |
|---|---|
| **Database per Service** | Cada serviço possui seu schema — autonomia total de dados |
| **Shared Database** | Anti-padrão na maioria dos casos; aceitar apenas em transição |
| **CQRS** | Separar modelos de leitura e escrita em serviços de alta carga |
| **Event Sourcing** | Persistir eventos em vez de estado atual; auditoria completa |

#### Transações Distribuídas

| Padrão | Quando usar |
|---|---|
| **Saga** | Operações de longa duração que cruzam múltiplos serviços; compensações em falha |
| **Transaction Outbox** | Garantir consistência entre mudança de estado local e publicação de evento |
| **Command-side Replica** | Replicar dados de outro serviço para evitar chamadas síncronas |

#### Resiliência

| Padrão | Quando usar |
|---|---|
| **Circuit Breaker** | Evitar cascade failure quando serviço downstream está indisponível |
| **Bulkhead** | Isolar recursos para evitar que falha de um componente esgote todos |
| **Retry with Backoff** | Lidar com falhas temporárias sem sobrecarga do serviço destino |

#### Observabilidade

| Padrão | Quando usar |
|---|---|
| **Distributed Tracing** | Correlacionar requisições que atravessam múltiplos serviços (OpenTelemetry) |
| **Health Check API** | Endpoint `/health` para readiness e liveness probes |
| **Log Aggregation** | Centralizar logs de todos os serviços (ELK, Loki) |
| **Metrics** | Métricas por serviço (Prometheus + Grafana) |

---

## V. Framework de Decisão: Monólito Modular vs. Microserviços

### Quando Permanecer como Monólito Modular

Fique no monólito modular enquanto:

- [ ] **As boundaries de domínio ainda estão sendo descobertas** — extrair antes de
  entender as fronteiras gera serviços mal definidos
- [ ] **O time é pequeno (< 10 engenheiros)** — a sobrecarga de microserviços supera
  o ganho de autonomia
- [ ] **O produto ainda não encontrou product-market fit** — velocidade de iteração
  importa mais que autonomia de deploy
- [ ] **A carga não exige scaling independente por domínio** — um monólito com bom
  design escala horizontalmente via load balancer
- [ ] **Não há times diferentes com ownership de domínios separados** — sem times
  separados, a autonomia de microserviços não se materializa
- [ ] **Operações ACID são frequentes entre domínios** — sagas e compensações adicionam
  complexidade real

### Quando Extrair para Microserviço

Considere a extração quando **pelo menos dois** dos seguintes forem verdade:

- [ ] Um domínio específico precisa de **scaling independente** (ex: `SimulationEngine`
  com dezenas de simulações concorrentes vs. `BoardManagement` com carga baixa)
- [ ] **Times diferentes** terão ownership de domínios diferentes e precisam de autonomia
  de deployment
- [ ] Um domínio tem **requisitos de segurança ou compliance diferentes** dos demais
- [ ] Um domínio precisa de uma **stack tecnológica diferente** (ex: Python para ML)
- [ ] A boundary do domínio está **estável há pelo menos 6 meses** em produção
- [ ] O **build time** do módulo está impactando a produtividade (> 5 min para o módulo)

### Modelo de Decisão

```
                     ┌─────────────────────────────────────┐
                     │ Boundaries de domínio bem entendidas?│
                     └──────────┬──────────────────────────┘
                                │
                   ┌────────────┴────────────┐
                  NÃO                       SIM
                   │                         │
                   ▼                         ▼
          ┌────────────────┐      ┌────────────────────────┐
          │ MONÓLITO       │      │ Times separados com    │
          │ MODULAR        │      │ ownership de domínios? │
          │ (aprenda os    │      └────────────┬───────────┘
          │  boundaries)   │                  │
          └────────────────┘     ┌────────────┴────────────┐
                                NÃO                       SIM
                                 │                         │
                                 ▼                         ▼
                        ┌────────────────┐      ┌──────────────────┐
                        │ MONÓLITO       │      │ MICROSERVIÇOS    │
                        │ MODULAR        │      │ (extraia gradual)│
                        │ (bom o sufic.) │      └──────────────────┘
                        └────────────────┘
```

---

## VI. Estratégias de Migração para Microserviços

Quando a decisão de extrair for tomada, use uma estratégia incremental — nunca reescreva tudo de uma vez.

### Strangler Fig Pattern

Extraia funcionalidades do monólito gradualmente, enquanto o monólito continua operando.
Novos clientes usam o microserviço; clientes existentes continuam no monólito.

```
                        ┌───────────────────────────┐
Cliente ──► API Gateway │  Routing                  │
                        │  /api/v1/scenarios ──────►│──► Novo Serviço (Python/ML)
                        │  /api/v1/boards ──────────│──► Monólito (mantido)
                        │  /api/v1/analytics ───────│──► Novo Serviço (Analytics)
                        └───────────────────────────┘
```

**Etapas:**
1. Introduza um API Gateway na frente do monólito (já existe como `http_api`)
2. Crie o novo microserviço para o domínio a extrair
3. Roteie o tráfego novo para o microserviço
4. Migre o tráfego existente gradualmente
5. Remova o código correspondente do monólito

### Branch by Abstraction

Use os módulos Gradle existentes como pontos de extração:

```kotlin
// 1. ATUAL: módulo como implementação interna
// http_api → usecases → domain

// 2. PASSO INTERMEDIÁRIO: isolar atrás de interface de serviço
interface SimulationService {
    suspend fun runDay(command: RunDayCommand): Either<DomainError, DailySnapshot>
}

class LocalSimulationService(
    private val useCase: RunDayUseCase
) : SimulationService {
    override suspend fun runDay(command: RunDayCommand) = useCase.execute(command)
}

// 3. EXTRAÇÃO: trocar LocalSimulationService por HttpSimulationService
class HttpSimulationService(
    private val client: HttpClient,
    private val baseUrl: String
) : SimulationService {
    override suspend fun runDay(command: RunDayCommand): Either<DomainError, DailySnapshot> =
        client.post("$baseUrl/run") { setBody(command) }
            .body<DailySnapshotResponse>()
            .toDomain()
            .right()
}
```

O `AppModule` do Koin é o único lugar a mudar:
```kotlin
// Antes
single<SimulationService> { LocalSimulationService(get()) }

// Depois da extração
single<SimulationService> { HttpSimulationService(get(), "http://simulation-svc") }
```

---

## VII. Como Este Projeto se Enquadra

### Estado Atual: Monólito Modular Bem Estruturado

```
domain/           ← Bounded Context: Kanban Simulation (puro, framework-free)
usecases/         ← Application Services + Repository Ports
sql_persistence/  ← Adapter: PostgreSQL
http_api/         ← Adapter: HTTP/REST + DI Wiring
```

**O que já está correto para suportar extração futura:**
- Módulos Gradle com boundaries explícitas ✅
- Ports-and-adapters (interfaces de repositório em `usecases/`) ✅
- CQS (Commands/Queries separados) ✅
- Zero dependências de framework no `domain/` ✅
- `Either<DomainError, T>` retornável tanto de chamada local quanto HTTP ✅

### Bounded Contexts que Poderiam Emergir como Microserviços

| Contexto Candidato | Gatilho para Extração |
|---|---|
| `Simulation Engine` | Demanda por execução paralela de N cenários; necessidade de escalar independentemente; possível migração para Python/ML |
| `Analytics / Relatórios` | Times de dados com stack separada (dbt, pandas); leitura intensa sem impactar escritas do simulador |
| `Tenant Management / Billing` | Requisitos de segurança/compliance diferentes; time de produto separado |
| `Integração Externa` (Jira/Trello) | Stack e ritmo de mudança completamente diferentes |

### Mapeamento dos Três Padrões no Projeto Atual

| Padrão | Status Atual | Próximo Passo |
|---|---|---|
| **Domain-Oriented Monolith** | ✅ Implementado — módulos Gradle por camada com DDD interno | Crescer com novos módulos Gradle por bounded context |
| **Domain Module API** | ✅ Implementado — `usecases/repositories/` são as APIs públicas | Reforçar: proibir imports diretos de `JdbcRepository` fora de `AppModule` |
| **Domain API Build Module** | ⚠️ Parcial — API e impl no mesmo módulo `usecases/` | Considerar separar `usecases-api/` quando o build time > 2min |

---

## VIII. Anti-Padrões a Evitar

### No Monólito Modular

| Anti-padrão | Sintoma | Correção |
|---|---|---|
| **Big Ball of Mud** | Qualquer módulo importa qualquer classe de qualquer outro | Definir regras de dependência no Gradle (implementação vs api) |
| **Shared Kernel sem governança** | Dois módulos modificam o mesmo código livremente | Extrair para módulo `-api` imutável com revisão obrigatória |
| **God Module** | Um módulo concentra 80% do código | Dividir em sub-módulos por bounded context |
| **Camadas técnicas como módulos** | `controllers/`, `services/`, `repositories/` como módulos Gradle | Reorganizar por domínio |
| **Banco compartilhado sem boundaries** | Módulo A faz JOIN direto na tabela do Módulo B | Módulo B expõe consulta via API; Módulo A não acessa o schema do B |

### Ao Migrar para Microserviços

| Anti-padrão | Sintoma | Correção |
|---|---|---|
| **Nanoserviços** | Serviços de 50 linhas com uma única função | Agrupar por bounded context, não por função técnica |
| **Distributed Monolith** | Deploys sempre em conjunto; mudança em um serviço quebra outros | Revisar boundaries; provavelmente são um serviço só |
| **Shared Database entre serviços** | Dois serviços fazem queries no mesmo schema | Database per Service; comunicação via API/eventos |
| **Sincronous Chain** | A → B → C → D em cadeia síncrona de N chamadas | Introduzir mensageria/eventos para desacoplar |
| **Big Bang Rewrite** | Reescrever tudo como microserviços de uma vez | Strangler Fig + Branch by Abstraction incrementalmente |

---

## IX. Checklist

### Ao Criar um Novo Módulo

- [ ] O módulo está organizado ao redor de um **bounded context ou subdomínio** (não camada técnica)?
- [ ] O módulo tem uma **API pública explícita** (interfaces, facades) separada de sua implementação?
- [ ] As **dependências entre módulos são unidirecionais** (sem ciclos)?
- [ ] O módulo de domínio (`domain/`) permanece **sem dependências de framework**?
- [ ] A **database schema** do módulo é privada — outros módulos não fazem JOIN direto nela?

### Ao Avaliar se Deve Extrair para Microserviço

- [ ] A **boundary do domínio está estável** há pelo menos 6 meses em produção?
- [ ] Há um **time com ownership exclusivo** deste domínio?
- [ ] O domínio tem **necessidades de scaling significativamente diferentes** dos demais?
- [ ] As operações dentro do domínio são **majoritariamente independentes** (poucas transações cross-domain)?
- [ ] O time está preparado para o **overhead operacional** (CI/CD separado, Kubernetes, observabilidade distribuída)?

### Ao Planejar a Migração

- [ ] Você identificou os **pontos de extração** (interfaces existentes a serem implementadas remotamente)?
- [ ] O **API Gateway** está em lugar para rotear tráfego sem afetar clientes?
- [ ] A **estratégia de dados** foi definida (Database per Service, migração incremental)?
- [ ] O **Strangler Fig** está configurado (novo serviço recebe tráfego novo, monólito recebe o antigo)?
- [ ] Há **contract tests** entre o monólito e o novo serviço?

---

## X. Relação com Outras Skills

| Esta skill | Complementa |
|---|---|
| Bounded contexts, módulos por domínio | `ddd` — contextos delimitados definem os módulos |
| Dependency Rule entre módulos | `clean-architecture` — regra de dependência se aplica entre módulos também |
| Estrutura de pacotes que grita o domínio | `screaming-architecture` — a estrutura de módulos comunica intenção |
| Ports para extração de serviços | `clean-architecture` — ports tornam a extração cirúrgica |
| Diagrama C4 para contextos e serviços | `c4-model` — visualizar containers e contextos |
| ADR para decisão de extração | `adr` — toda decisão de extração deve ter ADR aprovada |

---

## Referências

- Richardson, Chris. *Microservice Architecture Pattern*. https://microservices.io/patterns/microservices.html
- Richardson, Chris. *Modular Monolith Patterns for Fast Flow*. https://microservices.io/post/architecture/2024/09/09/modular-monolith-patterns-for-fast-flow.html
- Fowler, Martin. *MonolithFirst*. https://martinfowler.com/bliki/MonolithFirst.html
- Jovanović, Milan. *What is a Modular Monolith?*. https://www.milanjovanovic.tech/blog/what-is-a-modular-monolith
- ThoughtWorks. *Modular Monolith: A Better Way to Build Software*. https://www.thoughtworks.com/en-br/insights/blog/microservices/modular-monolith-better-way-build-software
- Naveen S. *Behold the Modular Monolith*. https://dev.to/naveens16/behold-the-modular-monolith-the-architecture-balancing-simplicity-and-scalability-2d4
- Fowler, Martin. *StranglerFigApplication*. https://martinfowler.com/bliki/StranglerFigApplication.html
- Richardson, Chris. *Pattern: Database per Service*. https://microservices.io/patterns/data/database-per-service.html
- Richardson, Chris. *Pattern: Saga*. https://microservices.io/patterns/data/saga.html