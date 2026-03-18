---
name: openapi-quality
description: >
  Audita e melhora a qualidade da documentação OpenAPI/Swagger neste projeto
  (Ktor + ktor-openapi DSL). Use quando adicionar, revisar ou corrigir specs
  de rotas HTTP. Garante conformidade com OAS 3.x e máxima qualidade no
  Swagger UI.
argument-hint: "[route file or endpoint to audit (optional)]"
allowed-tools: Read, Grep, Glob, Edit
---

# OpenAPI Quality — Ktor + ktor-openapi

Você é um especialista em documentação OpenAPI 3.x aplicada ao stack deste
projeto: **Kotlin + Ktor 3 + ktor-openapi 5 + Swagger UI**.

Quando esta skill for invocada:
1. Leia os arquivos de rotas indicados (ou todos os `*Routes.kt` em `http_api/`)
2. Leia `OpenApi.kt` para avaliar o bloco `install(OpenApi) { info { ... } }`
3. Aplique o checklist abaixo **item a item**
4. Produza as correções diretamente no código — não apenas liste problemas
5. Execute `./gradlew :http_api:test` ao final para garantir que nada quebrou

---

## 1. Info Object (`OpenApi.kt`)

```kotlin
install(OpenApi) {
    info {
        title = "..."          // obrigatório — nome do produto, não da tech
        version = "..."        // obrigatório — semver (ex: "1.0.0")
        summary = "..."        // REQUIRED pela OAS 3.1+ — frase de uma linha
        description = "..."    // markdown — contexto, autenticação, limites
        contact {
            name = "..."
            url  = "..."
            email = "..."
        }
    }
    tags {                     // declarar TODAS as tags aqui para ordem garantida
        tag("boards",    "Gerenciamento de quadros Kanban")
        tag("columns",   "Colunas dentro de um quadro")
        tag("cards",     "Cartões e movimentações")
        tag("scenarios", "Motor de simulação — criação e execução de cenários")
        tag("health",    "Liveness e readiness da aplicação")
    }
}
```

**Checklist Info Object:**
- [ ] `title` descreve o produto, não o framework
- [ ] `version` em semver
- [ ] `summary` presente (≤ 120 chars, sem ponto final)
- [ ] `description` em markdown, explica o domínio e links úteis
- [ ] Todas as tags declaradas em `tags { }` no nível global
- [ ] Tags ordenadas na ordem de exibição desejada no Swagger UI

---

## 2. Route Spec (`RouteConfig.() -> Unit`)

### Estrutura mínima obrigatória

```kotlin
private fun criarBoardSpec(): RouteConfig.() -> Unit = {
    operationId = "createBoard"            // camelCase, único na API
    summary     = "Cria um novo quadro"   // ≤ 120 chars, verbo no presente
    description = """
        Cria um quadro Kanban vazio para a organização informada.
        O quadro começa sem colunas — use `POST /columns` para adicioná-las.
    """.trimIndent()
    tags("boards")
    deprecated = false                    // explícito quando remover

    request {
        body<CreateBoardRequest> {
            description = "Dados do novo quadro."
            required    = true
            example("mínimo") { value = CreateBoardRequest(name = "Sprint 1") }
        }
    }

    response {
        code(HttpStatusCode.Created) {
            description = "Quadro criado. O `id` retornado é usado em todas as rotas filhas."
            body<BoardCreatedResponse>()
        }
        code(HttpStatusCode.BadRequest) {
            description = "Validação falhou — `errors` lista os campos inválidos."
            body<ValidationErrorResponse>()
        }
        code(HttpStatusCode.InternalServerError) {
            description = "Erro de persistência inesperado."
            body<DomainErrorResponse>()
        }
    }
}
```

### Checklist por rota

**Operação:**
- [ ] `operationId` presente, camelCase, único (`listBoardColumns` ≠ `getColumn`)
- [ ] `summary` presente — máx 120 chars, começa com verbo no infinitivo (Cria/Retorna/Move/Lista/Executa)
- [ ] `description` presente — explica comportamento, pré-condições, efeitos colaterais
- [ ] `tags(...)` — exatamente uma tag por rota (a tag do domínio principal)
- [ ] `deprecated` explícito apenas quando verdadeiro

**Parâmetros de path:**
- [ ] Cada `pathParameter<T>(name)` tem `description` (o que representa, formato esperado)
- [ ] `required = true` declarado explicitamente (path params são sempre obrigatórios)
- [ ] Tipo `<T>` correto: `String` para UUID, `Int` para inteiro, `Boolean` para flag

**Parâmetros de query:**
- [ ] `queryParameter<T>(name)` tem `description` com semântica e valor padrão se houver
- [ ] `required` declarado; parâmetros opcionais têm `required = false`
- [ ] Parâmetros numéricos têm `minimum`/`maximum` quando aplicável

**Request body:**
- [ ] `required = true` quando obrigatório
- [ ] `description` explica a intenção do payload (não só o tipo)
- [ ] Ao menos um `example(...)` com valor realista

**Responses:**
- [ ] Todos os status HTTP que o handler pode retornar estão documentados
- [ ] **2xx**: `description` explica o que o campo principal significa; `body<T>()` sempre
- [ ] **400**: `description` menciona `errors` e `requestId`; `body<ValidationErrorResponse>()`
- [ ] **404**: `description` especifica *qual* recurso não foi encontrado
- [ ] **409**: presente em rotas que podem conflitar (ex: `RunDay` quando dia já executado)
- [ ] **500**: presente em todas as rotas que acessam repositório; `body<DomainErrorResponse>()`
- [ ] Nenhuma resposta sem `description`

---

## 3. DTOs como Schema

Todos os DTOs usados em `body<T>()` devem ser `@Serializable data class` com:

```kotlin
@Serializable
data class CreateBoardRequest(
    val name: String,         // ktor-openapi infere o schema via reflection
    val tenantId: String,
)
```

**Checklist DTOs:**
- [ ] Todos os DTOs de request/response são `@Serializable data class`
- [ ] Nomes seguem o padrão: `[Entidade][Ação]Request` / `[Entidade]Response` / `[Entidade]CreatedResponse`
- [ ] DTOs de erro reutilizam tipos compartilhados: `ValidationErrorResponse`, `DomainErrorResponse`

---

## 4. Tipos de erro — DTOs compartilhados

Documente as respostas de erro com tipos concretos (não `Any`):

```kotlin
// Definir uma vez, referenciar em todas as rotas
@Serializable
data class ValidationErrorResponse(
    val errors: List<String>,
    val requestId: String,
)

@Serializable
data class DomainErrorResponse(
    val error: String,
    val requestId: String,
)
```

Em cada rota de erro:
```kotlin
code(HttpStatusCode.BadRequest)          { description = "..."; body<ValidationErrorResponse>() }
code(HttpStatusCode.NotFound)            { description = "..."; body<DomainErrorResponse>() }
code(HttpStatusCode.Conflict)            { description = "..."; body<DomainErrorResponse>() }
code(HttpStatusCode.InternalServerError) { description = "..."; body<DomainErrorResponse>() }
```

---

## 5. Verificação via Swagger UI

Após as alterações:

1. Suba o banco: `docker start kanban-db` (ou via Docker)
2. Inicie a aplicação: `./gradlew :http_api:run`
3. Abra `http://localhost:8080/swagger`
4. Valide visualmente:
   - [ ] Todas as tags aparecem na ordem correta
   - [ ] Cada operação tem título (`summary`) e subtítulo (`description`) visíveis
   - [ ] `operationId` aparece na URL do endpoint no Swagger UI (se configurado)
   - [ ] Parâmetros têm descrições no formulário
   - [ ] Bodies de exemplo aparecem no "Try it out"
   - [ ] Respostas de erro têm schema visível (não "No response body")
5. Valide o JSON: `curl -s http://localhost:8080/api.json | jq '.paths | keys'`
6. Execute os testes: `./gradlew :http_api:test`

---

## 6. OpenAPI Test Coverage

Cada nova rota deve ter um teste em `OpenApiSpecTest.kt`:

```kotlin
@Test
fun `openapi spec contains health route`() = testApplication {
    // ...
    val paths = Json.parseToJsonElement(client.get("/api.json").bodyAsText())
        .jsonObject["paths"]?.jsonObject
    assertNotNull(paths)
    assertTrue(paths["/health"]?.jsonObject?.containsKey("get") == true)
}

@Test
fun `all routes have summary and operationId`() = testApplication {
    // ...
    val paths = Json.parseToJsonElement(client.get("/api.json").bodyAsText())
        .jsonObject["paths"]?.jsonObject
    paths?.forEach { (path, pathItem) ->
        pathItem.jsonObject.forEach { (method, operation) ->
            val summary     = operation.jsonObject["summary"]?.jsonPrimitive?.content
            val operationId = operation.jsonObject["operationId"]?.jsonPrimitive?.content
            assertFalse(summary.isNullOrBlank(),     "Rota $method $path sem summary")
            assertFalse(operationId.isNullOrBlank(), "Rota $method $path sem operationId")
        }
    }
}
```

---

## 7. Antipadrões a evitar

| Antipadrão | Correto |
|---|---|
| `description = "Retorna o board."` | Explique o que o campo principal significa, pré-condições e links |
| Sem `operationId` | Sempre defina — quebra geração de clientes SDK |
| `body<Any>()` em erros | Use `body<ValidationErrorResponse>()` ou `body<DomainErrorResponse>()` |
| `description = "Parâmetros inválidos."` em 400 | Mencione os campos `errors` e `requestId` que o cliente deve ler |
| Tags só na rota, nunca no `install(OpenApi)` | Declare todas as tags globalmente para controle de ordem |
| `summary` igual a `description` | `summary` = título curto; `description` = contexto completo |
| `pathParameter` sem `required = true` | Path params são sempre obrigatórios — declare explicitamente |
| Response sem `body<T>()` em 2xx | Sempre documente o schema do corpo de sucesso |

---

## 8. Referências

- OAS 3.2: https://spec.openapis.org/oas/v3.2.0.html
- Swagger Spec: https://swagger.io/specification/
- ktor-openapi DSL: https://github.com/smiley4/ktor-openapi
- Swagger UI (Ktor): http://localhost:8080/swagger
- OpenAPI JSON: http://localhost:8080/api.json