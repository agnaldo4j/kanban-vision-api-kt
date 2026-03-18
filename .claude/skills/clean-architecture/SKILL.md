argument-hint: "[class, module or dependency to evaluate (optional)]"
allowed-tools: Read, Grep, Glob
---
name: clean-architecture
description: >
  Aplique os princípios de Clean Architecture (Uncle Bob, 2012) ao projetar,
  avaliar e evoluir qualquer parte deste projeto. Use este skill ao criar módulos,
  camadas, casos de uso, adaptadores, boundaries ou ao decidir onde uma classe
  ou dependência deve residir. Complementa o skill screaming-architecture.
---

# Clean Architecture

> "Conformar-se a estas regras simples não é difícil e economizará dores de cabeça
> significativas. Quando qualquer uma das partes externas ficar obsoleta — o banco
> de dados, o framework web — você poderá substituí-la com o mínimo de esforço."
> — Robert C. Martin (Uncle Bob), 2012
> Fonte: https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html

---

## Contexto: Uma Síntese de Boas Arquiteturas

Clean Architecture não é uma ideia isolada. Uncle Bob a descreve como uma síntese
de abordagens que já alcançaram os mesmos objetivos por caminhos diferentes:

- **Hexagonal Architecture** (Ports & Adapters) — Alistair Cockburn
- **Onion Architecture** — Jeffrey Palermo
- **Screaming Architecture** — o próprio Uncle Bob
- **DCI** (Data, Context, Interaction) — Trygve Reenskaug
- **BCE** (Boundary, Control, Entity) — Ivar Jacobson

Todas compartilham uma característica: **separação de responsabilidades por camadas**,
com as regras de negócio no centro e os detalhes técnicos na periferia.

---

## Os Cinco Objetivos de Uma Arquitetura Limpa

| Objetivo | Significado prático |
|---|---|
| **Independência de frameworks** | Frameworks são ferramentas plugáveis, não a fundação |
| **Testabilidade** | Regras de negócio testáveis sem UI, banco ou servidor |
| **Independência de UI** | Trocar web por CLI sem tocar no domínio |
| **Independência de banco de dados** | Trocar PostgreSQL por MongoDB sem tocar nos casos de uso |
| **Independência de agências externas** | O núcleo não sabe que o mundo externo existe |

Se qualquer um desses cinco objetivos não puder ser alcançado, a arquitetura tem
um problema de dependência a ser corrigido.

---

## A Regra da Dependência — O Princípio Fundamental

> **Dependências de código-fonte só podem apontar para dentro.**

Esta é a única regra que, se respeitada rigorosamente, garante todos os cinco
objetivos. Tudo o mais é consequência dela.

```
╔══════════════════════════════════════════════╗
║  Frameworks & Drivers         (círculo mais externo)
║  ┌──────────────────────────────────────┐    ║
║  │  Interface Adapters                  │    ║
║  │  ┌────────────────────────────────┐  │    ║
║  │  │  Use Cases                     │  │    ║
║  │  │  ┌──────────────────────────┐  │  │    ║
║  │  │  │  Entities                │  │  │    ║
║  │  │  │  (núcleo, mais interno)  │  │  │    ║
║  │  │  └──────────────────────────┘  │  │    ║
║  │  └────────────────────────────────┘  │    ║
║  └──────────────────────────────────────┘    ║
╚══════════════════════════════════════════════╝
              ↑ dependências só apontam para dentro ↑
```

**O que a regra proíbe:**
- Nenhum nome declarado em um círculo externo pode aparecer no código de um círculo interno
- Isso inclui: nomes de classes, funções, variáveis, anotações, formatos de dados
- Especialmente: estruturas de dados geradas por frameworks (`RowStructure`, `HttpRequest`,
  `ApplicationCall`) jamais devem cruzar para dentro de uma boundary

---

## As Quatro Camadas

### Camada 1 — Entities (Domínio)

**O que são:** os objetos de negócio da organização/aplicação. Encapsulam as regras
mais fundamentais — aquelas que existiriam mesmo sem software.

**Características:**
- Plain Objects sem nenhuma dependência de framework, protocolo ou banco
- Mais estáveis de todo o sistema: mudam apenas se as regras de negócio fundamentais mudarem
- Reutilizáveis por múltiplas aplicações da organização
- Não são afetadas por mudanças de UI, banco, framework ou operações de aplicação

**Neste projeto:**
```kotlin
// domain/model/Board.kt
data class Board(val id: BoardId, val name: String)

// domain/model/Card.kt
data class Card(
    val id: CardId,
    val columnId: ColumnId,
    val title: String,
    val description: String,
    val position: Int,
)
```

Zero imports de framework. Zero anotações de persistência. Zero conhecimento de HTTP.
Se amanhã o Ktor for substituído por Quarkus, essas classes não mudam uma vírgula.

---

### Camada 2 — Use Cases (Casos de Uso)

**O que são:** as regras de negócio específicas da aplicação. Orquestram o fluxo de
dados entre entidades para atingir um objetivo de negócio concreto.

**Características:**
- Conhecem apenas as Entities (círculo mais interno)
- Não conhecem UI, banco de dados, frameworks ou protocolos
- Mudam apenas quando os requisitos da aplicação mudam
- Nomeados pelo que fazem no domínio, não por padrão técnico

**Neste projeto:**
```kotlin
// usecases/board/CreateBoardUseCase.kt
class CreateBoardUseCase(private val repository: BoardRepository) {
    fun execute(command: CreateBoardCommand): BoardId {
        command.validate()
        val board = Board(id = BoardId.generate(), name = command.name)
        repository.save(board)
        return board.id
    }
}
```

`BoardRepository` é uma **interface** definida dentro de `usecases/` — o caso de uso
depende de uma abstração que ele mesmo define, não de uma implementação externa.
Isso é a Regra da Dependência em ação.

**Padrão CQS nesta camada:**

| Tipo | Interface | Exemplo |
|---|---|---|
| Modifica estado | `Command` com `validate()` | `CreateBoardCommand`, `MoveCardCommand` |
| Lê estado | `Query` | `GetBoardQuery`, `GetCardQuery` |

Cada caso de uso aceita exatamente um `Command` ou `Query`. Sem sobrecarga, sem
múltiplos parâmetros primitivos espalhados.

---

### Camada 3 — Interface Adapters (Adaptadores)

**O que são:** conversores entre o formato conveniente para os casos de uso e o
formato conveniente para os agentes externos (UI, banco, filas).

**O que pertence aqui:**
- Controllers HTTP (recebem request, constroem Command, chamam UseCase)
- Presenters (transformam resultado do UseCase em formato de resposta)
- Repositórios JDBC/JPA (implementam as interfaces definidas nos UseCases)
- Todo SQL fica **restrito a esta camada** — nenhum código interno conhece SQL

**Neste projeto:**
```kotlin
// http_api/routes/BoardRoutes.kt — controller/presenter embutido na rota
post("/boards") {
    // 1. recebe dado externo
    val request = call.receive<CreateBoardRequest>()

    // 2. converte para o formato interno (Command)
    val boardId = createBoard.execute(CreateBoardCommand(name = request.name))

    // 3. busca resultado e converte para formato externo (Response DTO)
    val board = getBoard.execute(GetBoardQuery(id = boardId.value))
    call.respond(HttpStatusCode.Created, BoardResponse(board.id.value, board.name))
}

// sql_persistence/repositories/JdbcBoardRepository.kt — adaptador de persistência
class JdbcBoardRepository : BoardRepository {
    override fun save(board: Board) { /* SQL aqui, nunca no domínio */ }
    override fun findById(id: BoardId): Board? { /* SQL aqui */ }
}
```

A rota HTTP não contém lógica de negócio. O repositório JDBC não contém lógica
de negócio. Ambos apenas adaptam formatos.

---

### Camada 4 — Frameworks & Drivers

**O que são:** o anel mais externo. Frameworks, bancos, servidores, ORMs, bibliotecas
de terceiros. Pouco código original — principalmente integração e configuração.

**Características:**
- Contém todos os detalhes técnicos
- É a camada que mais muda ao longo do tempo
- Seu impacto deve ser zero nos círculos internos
- A web é um detalhe aqui. O banco de dados é um detalhe aqui.

**Neste projeto:**
```
http_api/plugins/    ← Ktor: configuração de plugins (Serialization, Routing, etc.)
http_api/di/         ← Koin: wiring de dependências
sql_persistence/     ← HikariCP + JDBC + PostgreSQL
```

O `AppModule` do Koin é o único lugar onde `JdbcBoardRepository` é vinculado à
interface `BoardRepository`. É o único ponto de contato entre o adaptador e o domínio.

---

## Cruzando Boundaries: Fluxo de Controle vs. Dependência

Este é o ponto mais sutil da Clean Architecture. O fluxo de controle e as
dependências de código **podem apontar em direções opostas** — e isso é intencional.

### O Problema

```
Controller → UseCase → Presenter
```

O fluxo de controle vai da esquerda para a direita. Mas `Presenter` está em um
círculo externo. Se o UseCase chamar o Presenter diretamente, ele estaria
dependendo de um círculo externo — violando a Dependency Rule.

### A Solução: Dependency Inversion Principle (DIP)

```
Controller → UseCase → <OutputPort interface>
                              ↑
                         Presenter (implementa OutputPort)
```

O UseCase define uma interface (`OutputPort`) no seu próprio círculo. O Presenter,
no círculo externo, **implementa** essa interface. O UseCase nunca importa o
Presenter concreto — só conhece a interface que ele mesmo define.

**Neste projeto, o mesmo padrão nos repositórios:**

```kotlin
// usecases/repositories/BoardRepository.kt — interface definida DENTRO do UseCases
interface BoardRepository {
    fun save(board: Board)
    fun findById(id: BoardId): Board?
}

// sql_persistence/repositories/JdbcBoardRepository.kt — implementação FORA
class JdbcBoardRepository : BoardRepository { ... }
```

`usecases/` define a interface. `sql_persistence/` implementa. A dependência aponta
para dentro (sql_persistence depende de usecases), mesmo que o fluxo de controle
vá para fora (UseCase chama o repositório que é JDBC).

---

## Dados Cruzando Boundaries

**Regra:** dados que cruzam uma boundary devem ser **estruturas simples e isoladas**,
na forma mais conveniente para o círculo **interno**, nunca para o externo.

| Permitido | Proibido |
|---|---|
| Data class simples (DTO) | Entity de banco de dados (`RowStructure`) |
| Argumentos primitivos | `HttpRequest` / `ApplicationCall` passado para dentro |
| Command/Query objects | Objetos gerados por framework |
| Hashmaps de dados primitivos | Qualquer objeto com dependência de framework |

### Por que isso importa

Frameworks de banco frequentemente retornam um `ResultSet` ou `Row` como resposta
a uma query. Se você passar esse objeto diretamente para dentro do UseCase, o
círculo interno passa a depender do framework de banco. Amanhã, se você trocar
o banco, o UseCase quebra — exatamente o que a Dependency Rule deveria evitar.

**Neste projeto:**
```kotlin
// ✅ CORRETO — JdbcBoardRepository converte o ResultSet antes de retornar
override fun findById(id: BoardId): Board? {
    // ResultSet nunca sai desta função
    return resultSet?.let {
        Board(
            id = BoardId(it.getString("id")),
            name = it.getString("name"),
        )
    }
}

// ❌ ERRADO — jamais faça isso
override fun findById(id: BoardId): ResultSet  // ResultSet cruzando a boundary
```

---

## Como Este Projeto Mapeia as Camadas

```
┌─────────────────────────────────────────────────────────────┐
│  FRAMEWORKS & DRIVERS                                        │
│  http_api/plugins/   http_api/di/   sql_persistence/        │
├─────────────────────────────────────────────────────────────┤
│  INTERFACE ADAPTERS                                          │
│  http_api/routes/   sql_persistence/repositories/           │
├─────────────────────────────────────────────────────────────┤
│  USE CASES                                                   │
│  usecases/board/   usecases/card/   usecases/repositories/  │
├─────────────────────────────────────────────────────────────┤
│  ENTITIES                                                    │
│  domain/model/   domain/model/valueobjects/                 │
└─────────────────────────────────────────────────────────────┘
```

**Regra de dependência verificada:**
- `domain` → não depende de nenhum módulo interno
- `usecases` → depende apenas de `domain`
- `sql_persistence` → depende de `domain` e `usecases` (implementa interfaces)
- `http_api` → depende de `usecases`, `sql_persistence` (só para wiring no DI)

---

## Checklist ao Adicionar Código Novo

### Ao criar uma nova Entidade

- [ ] Está em `domain/model/`?
- [ ] É um Plain Object (data class) sem imports de framework?
- [ ] Tem apenas lógica de negócio pura (sem IO, sem HTTP, sem SQL)?
- [ ] Testável com JUnit puro, sem nenhum mock de framework?

### Ao criar um novo Caso de Uso

- [ ] Está em `usecases/`?
- [ ] Aceita um `Command` ou `Query` como entrada (não primitivos avulsos)?
- [ ] Depende apenas de interfaces de repositório definidas em `usecases/repositories/`?
- [ ] Não importa nada de `http_api`, `sql_persistence`, Ktor ou JDBC?
- [ ] Testável com MockK apenas para os repositórios, sem servidor?

### Ao criar um Adaptador (rota, repositório, cliente externo)

- [ ] Está em `http_api/routes/` (adaptador HTTP) ou `sql_persistence/` (adaptador DB)?
- [ ] Toda lógica de conversão de formato fica aqui (nunca no UseCase)?
- [ ] Nenhum objeto de framework (ResultSet, ApplicationCall) cruza a boundary para dentro?
- [ ] O adaptador chama o UseCase — não contém lógica de negócio própria?

### Ao adicionar uma dependência nova

- [ ] A dependência de framework vai no módulo mais externo possível?
- [ ] `domain` continua com zero dependências externas após a adição?
- [ ] `usecases` continua sem dependências de framework após a adição?

---

## Sinais de Violação da Dependency Rule

| Violação | Sintoma no código | Correção |
|---|---|---|
| Entidade conhece framework | `import io.ktor.*` em `domain/` | Mova para adaptador |
| UseCase recebe objeto HTTP | `fun execute(call: ApplicationCall)` | Crie um Command object |
| UseCase retorna Row/ResultSet | `fun get(): ResultSet` | Converta no repositório |
| SQL em UseCase ou Entity | `"SELECT * FROM boards"` fora de `sql_persistence/` | Mova para JdbcRepository |
| Rota contém `if` de negócio | Validação de regra dentro da rota | Mova para UseCase/Entity |
| Repositório importado direto na rota | `val repo: JdbcBoardRepository by inject()` | Injete a interface `BoardRepository` |
| Teste de UseCase sobe servidor | `@SpringBootTest` ou `testApplication { }` num UseCase | Use MockK para o repositório |

---

## Relação com Screaming Architecture

Clean Architecture e Screaming Architecture são complementares:

| Screaming Architecture | Clean Architecture |
|---|---|
| Define **o que** a estrutura comunica | Define **como** as camadas se relacionam |
| Preocupa-se com nomes e organização visível | Preocupa-se com direção de dependências |
| "O projeto deve gritar Kanban, não Ktor" | "Ktor não pode influenciar o domínio Kanban" |
| Vista de fora para dentro (estrutura de pastas) | Vista de dentro para fora (fluxo de dependências) |

Use os dois juntos: Screaming Architecture garante que a estrutura comunica o domínio.
Clean Architecture garante que as dependências protegem o domínio.

---

## Princípios Resumidos

1. **Dependency Rule é inegociável** — dependências só apontam para dentro, sem exceção
2. **Entities são o núcleo imutável** — mudam apenas com as regras de negócio fundamentais
3. **Use Cases orquestram, não implementam detalhes** — nenhum SQL, nenhum HTTP
4. **Adaptadores convertem formatos** — toda tradução entre mundo externo e interno fica aqui
5. **Frameworks ficam na periferia** — trocar Ktor ou PostgreSQL não deve tocar no domínio
6. **DIP resolve conflitos de direção** — quando o fluxo vai para fora, inverta a dependência com interface
7. **DTOs simples cruzam boundaries** — nunca objetos de framework ou entidades de banco
8. **Testabilidade é o termômetro** — se não consegue testar o UseCase sem infraestrutura, há uma violação