# Kanban Vision API

> Simulador de quadro Kanban via API REST — construído em Kotlin com foco em arquitetura limpa, qualidade de código e boas práticas de engenharia de software.

---

## Sobre o projeto

O **Kanban Vision** é um simulador de gestão de tarefas no estilo Kanban. Ele permite criar quadros, organizar colunas e mover cartões entre estágios de um fluxo de trabalho — expondo tudo via uma API REST.

O projeto foi concebido como uma **referência prática** de arquitetura hexagonal em Kotlin, demonstrando como separar domínio, casos de uso, persistência e entrega HTTP de forma clara e testável.

---

## Arquitetura

O projeto segue os princípios de **Clean Architecture** (Arquitetura Hexagonal) combinados com **Screaming Architecture** — os módulos e pacotes expressam a intenção do negócio, não os frameworks utilizados.

```
┌─────────────────────────────────────┐
│             http_api                │  ← Entrega HTTP (Ktor + Koin)
│  routes / plugins / di              │
└──────────────┬──────────────────────┘
               │ depende de
┌──────────────▼──────────────────────┐
│             usecases                │  ← Casos de uso (CQS)
│  board / card / cqs / repositories  │
└──────────────┬──────────────────────┘
               │ depende de
┌──────────────▼──────────────────────┐
│              domain                 │  ← Núcleo do negócio (puro Kotlin)
│  model / valueobjects               │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│          sql_persistence            │  ← Adaptador de banco (JDBC + HikariCP)
│  repositories / DatabaseFactory     │
└─────────────────────────────────────┘
```

### Fluxo de dependências

```
http_api → usecases → domain
sql_persistence → domain
http_api → sql_persistence   (somente na camada de DI via Koin)
```

O módulo `domain` não conhece nenhum framework. O módulo `usecases` não conhece banco de dados nem HTTP.

---

## Padrão CQS (Command Query Separation)

Cada caso de uso recebe um objeto tipado que implementa `Command` (modifica estado) ou `Query` (lê estado), com validação explícita antes da execução:

```
CreateBoardCommand → CreateBoardUseCase → BoardId
GetBoardQuery      → GetBoardUseCase    → Board

CreateCardCommand  → CreateCardUseCase  → CardId
MoveCardCommand    → MoveCardUseCase    → Unit
GetCardQuery       → GetCardUseCase     → Card
```

---

## Módulos

| Módulo | Responsabilidade |
|---|---|
| `domain` | Entidades, objetos de valor, regras de negócio puras |
| `usecases` | Casos de uso, interfaces de repositório (ports), CQS |
| `sql_persistence` | Implementações JDBC dos repositórios, schema SQL |
| `http_api` | Rotas HTTP, serialização, injeção de dependências, ponto de entrada |

---

## Stack

| Preocupação | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.1 |
| HTTP | Ktor 3 (Netty) |
| Serialização | kotlinx.serialization |
| Injeção de dependência | Koin 4 |
| Pool de conexões | HikariCP |
| Banco de produção | PostgreSQL |
| Banco de testes | H2 (in-memory) |
| Logging | SLF4J |
| Testes | JUnit 5 + MockK |
| Análise estática | Detekt |
| Estilo de código | Ktlint |
| Cobertura | JaCoCo (mínimo 90%) |
| Build | Gradle 8 (Kotlin DSL) |
| Java | Java 21 |

---

## Estrutura de pacotes

```
domain/
└── model/
    ├── Board.kt
    ├── Card.kt
    ├── Column.kt
    └── valueobjects/
        ├── BoardId.kt
        ├── CardId.kt
        └── ColumnId.kt

usecases/
├── cqs/
│   ├── Command.kt
│   └── Query.kt
├── board/
│   ├── commands/CreateBoardCommand.kt
│   ├── queries/GetBoardQuery.kt
│   ├── CreateBoardUseCase.kt
│   └── GetBoardUseCase.kt
├── card/
│   ├── commands/CreateCardCommand.kt
│   ├── commands/MoveCardCommand.kt
│   ├── queries/GetCardQuery.kt
│   ├── CreateCardUseCase.kt
│   ├── GetCardUseCase.kt
│   └── MoveCardUseCase.kt
└── repositories/
    ├── BoardRepository.kt
    └── CardRepository.kt

sql_persistence/
├── DatabaseFactory.kt
└── repositories/
    ├── JdbcBoardRepository.kt
    └── JdbcCardRepository.kt

http_api/
├── Main.kt
├── di/AppModule.kt
├── plugins/
│   ├── Routing.kt
│   ├── Serialization.kt
│   └── StatusPages.kt
└── routes/
    ├── BoardRoutes.kt
    └── CardRoutes.kt
```

---

## API REST

### Quadros (Boards)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/boards` | Cria um novo quadro |
| `GET` | `/boards/{id}` | Busca um quadro pelo ID |

**Criar quadro:**
```http
POST /boards
Content-Type: application/json

{ "name": "Meu Projeto" }
```

```json
HTTP 201 Created
{ "id": "uuid", "name": "Meu Projeto" }
```

### Cartões (Cards)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/cards` | Cria um cartão em uma coluna |
| `GET` | `/cards/{id}` | Busca um cartão pelo ID |
| `PATCH` | `/cards/{id}/move` | Move o cartão para outra coluna/posição |

**Criar cartão:**
```http
POST /cards
Content-Type: application/json

{ "columnId": "uuid", "title": "Implementar login", "description": "Opcional" }
```

**Mover cartão:**
```http
PATCH /cards/{id}/move
Content-Type: application/json

{ "columnId": "uuid-destino", "position": 2 }
```

---

## Como executar

### Pré-requisitos

- Java 21
- PostgreSQL (ou Docker)

### Configuração do banco

```bash
# Com Docker
docker run -d \
  --name kanban-db \
  -e POSTGRES_DB=kanban \
  -e POSTGRES_USER=kanban \
  -e POSTGRES_PASSWORD=kanban \
  -p 5432:5432 \
  postgres:16
```

### Subir a aplicação

```bash
./gradlew :http_api:run
```

### Build do JAR

```bash
./gradlew :http_api:buildFatJar
java -jar http_api/build/libs/http_api-all.jar
```

---

## Qualidade de código

```bash
# Todos os testes + análise + cobertura
./gradlew testAll

# Por módulo
./gradlew :domain:check
./gradlew :usecases:check

# Formatar código automaticamente
./gradlew ktlintFormat
```

O pipeline de qualidade exige:
- **Detekt** — análise estática sem violações
- **Ktlint** — estilo de código consistente
- **JaCoCo** — cobertura mínima de 90% de instruções por módulo

---

## Testes

```bash
# Rodar todos
./gradlew testAll

# Rodar um módulo
./gradlew :domain:test
./gradlew :usecases:test

# Rodar uma classe específica
./gradlew :domain:test --tests "com.kanbanvision.domain.model.BoardTest"
```

Os testes de casos de uso utilizam **MockK** para isolar os repositórios e **kotlinx-coroutines-test** para testar funções suspensas.

---

## Variáveis de configuração

Configuradas via `application.conf` (Ktor):

```hocon
database {
  url      = "jdbc:postgresql://localhost:5432/kanban"
  driver   = "org.postgresql.Driver"
  user     = "kanban"
  password = "kanban"
  poolSize = 10
}
```

---

## Licença

Este projeto é de uso educacional e de referência arquitetural.