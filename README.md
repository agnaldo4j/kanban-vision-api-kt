[![CI](https://github.com/agnaldo4j/kanban-vision-api-kt/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/agnaldo4j/kanban-vision-api-kt/actions/workflows/ci.yml)

# Kanban Vision API

> Simulador de quadro Kanban via API REST — construído em Kotlin com foco em arquitetura limpa, qualidade de código e boas práticas de engenharia de software.

---

## Quick Start

```bash
# 1. Clone e rode os testes
git clone https://github.com/agnaldo4j/kanban-vision-api-kt.git
cd kanban-vision-api-kt
./gradlew testAll

# 2. Suba o banco de dados
docker run -d --name kanban-db \
  -e POSTGRES_DB=kanbanvision \
  -e POSTGRES_USER=kanban \
  -e POSTGRES_PASSWORD=kanban \
  -p 5432:5432 \
  postgres:16

# 3. Execute a aplicação
./gradlew :http_api:run
```

Acesse a documentação interativa: **http://localhost:8080/swagger**

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
sql_persistence → usecases   (implementa as interfaces de repositório)
http_api → sql_persistence   (somente na camada de DI via Koin)
```

O módulo `domain` não conhece nenhum framework. O módulo `usecases` não conhece banco de dados nem HTTP.

---

## Padrão CQS (Command Query Separation)

Cada caso de uso recebe um objeto tipado que implementa `Command` (modifica estado) ou `Query` (lê estado), com validação explícita antes da execução:

```
CreateBoardCommand       → CreateBoardUseCase       → BoardId
GetBoardQuery            → GetBoardUseCase          → Board

CreateCardCommand        → CreateCardUseCase        → CardId
MoveCardCommand          → MoveCardUseCase          → Unit
GetCardQuery             → GetCardUseCase           → Card

CreateColumnCommand      → CreateColumnUseCase      → ColumnId
GetColumnQuery           → GetColumnUseCase         → Column
ListColumnsByBoardQuery  → ListColumnsByBoardUseCase → List<Column>
```

---

## Tratamento de Erros (Either)

Os erros de domínio são modelados como valores com Arrow-kt `Either<DomainError, T>`, eliminando exceções como mecanismo de controle de fluxo.

### Hierarquia DomainError

```
sealed class DomainError
├── ValidationError(message)  → HTTP 400
├── BoardNotFound(id)          → HTTP 404
├── ColumnNotFound(id)         → HTTP 404
├── CardNotFound(id)           → HTTP 404
└── PersistenceError(message)  → HTTP 500
```

### Padrão nos Use Cases

```kotlin
suspend fun execute(query: GetBoardQuery): Either<DomainError, Board>
```

Chamadas de repositório são envolvidas com `arrow.core.raise.catch` para converter exceções JDBC em `PersistenceError` tipado:

```kotlin
val (result, duration) = catch(
    { measureTimedValue { repository.findById(id) } }
) { e -> raise(DomainError.PersistenceError(e.message ?: "Database error")) }
```

### Padrão nas Rotas

```kotlin
useCase.execute(query).fold(
    ifLeft = { error -> call.respondWithDomainError(error) },
    ifRight = { result -> call.respond(result) },
)
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
| Documentação API | ktor-openapi + Swagger UI |
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
├── column/
│   ├── commands/CreateColumnCommand.kt
│   ├── queries/GetColumnQuery.kt
│   ├── queries/ListColumnsByBoardQuery.kt
│   ├── CreateColumnUseCase.kt
│   ├── GetColumnUseCase.kt
│   └── ListColumnsByBoardUseCase.kt
└── repositories/
    ├── BoardRepository.kt
    ├── CardRepository.kt
    └── ColumnRepository.kt

sql_persistence/
├── DatabaseFactory.kt
└── repositories/
    ├── JdbcBoardRepository.kt
    ├── JdbcCardRepository.kt
    └── JdbcColumnRepository.kt

http_api/
├── Main.kt
├── adapters/EitherRespond.kt
├── di/AppModule.kt
├── plugins/
│   ├── Observability.kt
│   ├── Routing.kt
│   ├── Serialization.kt
│   ├── StatusPages.kt
│   └── OpenApi.kt
└── routes/
    ├── BoardRoutes.kt
    ├── CardRoutes.kt
    └── ColumnRoutes.kt
```

---

## API REST

Todas as rotas seguem o prefixo `/api/v1`.

### Quadros (Boards)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/boards` | Cria um novo quadro |
| `GET` | `/api/v1/boards/{id}` | Busca um quadro pelo ID |

**Criar quadro:**
```http
POST /api/v1/boards
Content-Type: application/json

{ "name": "Meu Projeto" }
```

```json
HTTP 201 Created
{ "id": "uuid", "name": "Meu Projeto" }
```

### Colunas (Columns)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/columns` | Cria uma coluna em um quadro |
| `GET` | `/api/v1/columns/{id}` | Busca uma coluna pelo ID |
| `GET` | `/api/v1/boards/{boardId}/columns` | Lista todas as colunas de um quadro |

### Cartões (Cards)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/cards` | Cria um cartão em uma coluna |
| `PATCH` | `/api/v1/cards/{id}/move` | Move o cartão para outra coluna/posição |

**Criar cartão:**
```http
POST /api/v1/cards
Content-Type: application/json

{ "columnId": "uuid", "title": "Implementar login", "description": "Opcional" }
```

**Mover cartão:**
```http
PATCH /api/v1/cards/{id}/move
Content-Type: application/json

{ "columnId": "uuid-destino", "position": 2 }
```

### Exemplos curl

```bash
# Criar um quadro
curl -s -X POST http://localhost:8080/api/v1/boards \
  -H "Content-Type: application/json" \
  -d '{"name": "Meu Projeto"}' | jq

# Buscar um quadro
curl -s http://localhost:8080/api/v1/boards/{boardId} | jq

# Criar uma coluna
curl -s -X POST http://localhost:8080/api/v1/columns \
  -H "Content-Type: application/json" \
  -d '{"boardId": "{boardId}", "name": "To Do"}' | jq

# Listar colunas de um quadro
curl -s http://localhost:8080/api/v1/boards/{boardId}/columns | jq

# Buscar uma coluna
curl -s http://localhost:8080/api/v1/columns/{columnId} | jq

# Criar um cartão
curl -s -X POST http://localhost:8080/api/v1/cards \
  -H "Content-Type: application/json" \
  -d '{"columnId": "{columnId}", "title": "Implementar login", "description": "Opcional"}' | jq

# Buscar um cartão
curl -s http://localhost:8080/api/v1/cards/{cardId} | jq

# Mover cartão para outra coluna
curl -s -X PATCH http://localhost:8080/api/v1/cards/{cardId}/move \
  -H "Content-Type: application/json" \
  -d '{"columnId": "{targetColumnId}", "position": 0}' | jq
```

---

## Rastreabilidade (Observabilidade)

Cada requisição recebe um identificador único de correlação propagado em toda a execução:

- **Header de entrada**: `X-Request-ID` — se enviado pelo cliente, o mesmo valor é reutilizado.
- **Header de resposta**: `X-Request-ID` — sempre presente na resposta, seja sucesso ou erro.
- **Logs**: o `requestId` é adicionado ao MDC (Mapped Diagnostic Context) e aparece em todas as linhas de log da requisição no formato `[rid=<uuid>]`.
- **Erros**: todas as respostas de erro (`4xx`, `5xx`) incluem o campo `requestId` no corpo JSON para facilitar a correlação com os logs.

**Exemplo de log:**
```
14:22:01.123 [ktor-nio-thread-1] INFO  RequestLogging [rid=3f2a1b4c-...] - POST /api/v1/boards → 201 Created
```

**Exemplo de resposta de erro:**
```json
{
  "error": "Nome do quadro não pode ser vazio.",
  "requestId": "3f2a1b4c-8e2d-4f1a-b9c3-..."
}
```

---

## Documentação OpenAPI

A API expõe documentação interativa via Swagger UI quando a aplicação está em execução:

- **Swagger UI**: `http://localhost:8080/swagger`
- **OpenAPI JSON**: `http://localhost:8080/api.json`

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
  -e POSTGRES_DB=kanbanvision \
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
java -jar http_api/build/libs/kanban-vision-api.jar
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

Configuradas via `application.conf` (Ktor), com fallback para variáveis de ambiente:

```hocon
database {
  url      = "jdbc:postgresql://localhost:5432/kanbanvision"
  driver   = "org.postgresql.Driver"
  user     = "kanban"
  password = "kanban"
  poolSize = 10
}
```

| Variável de ambiente | Padrão |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/kanbanvision` |
| `DATABASE_DRIVER` | `org.postgresql.Driver` |
| `DATABASE_USER` | `kanban` |
| `DATABASE_PASSWORD` | `kanban` |
| `DATABASE_POOL_SIZE` | `10` |

---

## Troubleshooting

### Java 21 não encontrado

O projeto exige Java 21. O Gradle usa o path configurado em `gradle.properties` (`org.gradle.java.home`). Se o build falhar com `Could not determine java version`:

```bash
# Verificar a versão ativa
java -version

# Configurar manualmente (exemplo macOS com SDKMAN)
sdk install java 21-tem
sdk use java 21-tem

# Ou exportar a variável antes do build
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew testAll
```

### PostgreSQL recusado na inicialização

Se a aplicação falhar com `Connection refused` ao subir:

```bash
# Verificar se o container está rodando
docker ps | grep kanban-db

# Subir novamente se necessário
docker start kanban-db

# Verificar logs do container
docker logs kanban-db
```

Confirme que as variáveis de ambiente `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD` estão alinhadas com a configuração do container.

### JaCoCo falhando com cobertura abaixo de 90%

O gate de cobertura é por módulo. Para identificar qual módulo está abaixo:

```bash
./gradlew :domain:test :domain:jacocoTestCoverageVerification
./gradlew :usecases:test :usecases:jacocoTestCoverageVerification
./gradlew :sql_persistence:test :sql_persistence:jacocoTestCoverageVerification
./gradlew :http_api:test :http_api:jacocoTestCoverageVerification
```

O relatório HTML em `build/reports/jacoco/test/html/index.html` mostra a cobertura linha a linha.

### Detekt ou KtLint bloqueando o build

```bash
# Ver todos os problemas de uma vez
./gradlew detekt

# Corrigir formatação automaticamente (KtLint)
./gradlew ktlintFormat

# Rodar Detekt apenas em um módulo
./gradlew :domain:detekt
```

---

## Licença

Este projeto é de uso educacional e de referência arquitetural.
