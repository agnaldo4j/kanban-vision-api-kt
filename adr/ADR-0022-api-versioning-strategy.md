# ADR-0022 — API Versioning Strategy: URL-based Versioning v1/v2

## Cabeçalho

| Campo     | Valor                        |
|-----------|------------------------------|
| Status    | Aceita                       |
| Data      | 2026-06-20                   |
| Execução  | —                            |
| Autores   | @agnaldo4j                   |
| Branch    | —                            |
| PR        | —                            |
| Supersede | —                            |

---

## Contexto e Motivação

A dimensão **OpenAPI** está em 8.5/10. Uma das lacunas apontadas é a ausência de uma
**estratégia de versionamento de API documentada e implementada**. Atualmente:

- Todas as rotas usam o prefixo `/api/v1` hardcoded em `Routing.kt`
- Não há documentação de quando e como introduzir `/api/v2`
- Não há política de ciclo de vida de versões (deprecação, remoção)
- A spec OpenAPI gerada não distingue versões

Sem uma estratégia explícita, duas situações de risco surgem:
1. **Breaking changes silenciosas:** uma mudança em `/api/v1` quebra clientes existentes sem aviso
2. **Paralisia evolutiva:** sem política clara de como introduzir v2, mudanças necessárias são
   postergadas indefinidamente para não quebrar compatibilidade

Este gap é do tipo `[E]` Estrutural porque:
- Afeta a estrutura de routing (como rotas são organizadas no Ktor)
- Afeta a geração de spec OpenAPI (como specs por versão são expostas)
- Define contratos de longo prazo (ciclo de vida de versões)

---

## Gap Coberto

| GAP | Título | Tipo |
|-----|--------|------|
| GAP-AL | API versioning strategy (v1 → v2) | E |

---

## Decisão

### Estratégia: URL-based Versioning

**Escolhida:** versão no caminho da URL (`/api/v1/...`, `/api/v2/...`).

**Razões:**
- Já é o padrão atual do projeto (`/api/v1` hardcoded)
- Mais visível e cacheável por proxies/CDNs
- Mais fácil de testar (curl, Swagger UI)
- Compatível com o DSL de routing do Ktor (prefixo de rota)

**Alternativas descartadas:**
- **Header versioning (`Accept: application/vnd.api+json;version=1`):** invisível em URLs,
  não funciona bem com Swagger UI, dificulta caching
- **Query param (`?version=1`):** semanticamente errado (versão não é filtro de dados),
  polui logs e analytics

### Regras de Compatibilidade (Additive-Only em v1)

As seguintes mudanças são **permitidas** em `/api/v1` sem criar v2:
- Adicionar novos endpoints
- Adicionar campos **opcionais** em responses (clientes devem ignorar campos desconhecidos)
- Adicionar novos valores em enums (clientes devem ter fallback para valores desconhecidos)
- Corrigir bugs (comportamento incorreto documentado)

As seguintes mudanças **requerem nova versão (`/api/v2`)**:
- Remover ou renomear campo em request/response
- Alterar tipo de campo (ex: `String` → `Int`)
- Alterar semântica de um endpoint existente
- Remover endpoint
- Alterar códigos de resposta HTTP

### Ciclo de Vida de Versões

```
v1 lançada    → suporte ativo (sem deprecação)
v2 lançada    → v1 entra em modo deprecado (header `Deprecation: true` nas respostas)
v1 deprecated → suporte garantido por 12 meses após lançamento de v2
v1 removed    → após 12 meses de deprecação; comunicado com 90 dias de antecedência
```

Header de deprecação (adicionado automaticamente pelo `SecurityHeaders.kt` quando versão é deprecada):
```
Deprecation: true
Sunset: <data de remoção em RFC7231>
Link: </api/v2>; rel="successor-version"
```

### Implementação no Ktor

**Estrutura de routing:**

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/Routing.kt
fun Application.configureRouting(
    boardRepository: BoardRepository,
    simulationRepository: SimulationRepository,
    // ...
) {
    routing {
        route("/api/v1") {
            authenticate("jwt-auth") {
                boardRoutes(boardRepository)
                simulationRoutes(simulationRepository)
                simulationAnalyticsRoutes(simulationRepository)
            }
        }
        // v2 será adicionado aqui quando necessário:
        // route("/api/v2") { ... }

        // Rotas sem versão (public):
        healthRoutes(healthCheckUseCase)
        get("/metrics") { /* Prometheus */ }
    }
}
```

**Spec OpenAPI por versão:**

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/OpenApi.kt
fun Application.configureOpenApi() {
    install(OpenApiPlugin) {
        openApiSpec {
            info {
                title = "Kanban Vision API"
                version = "1.0.0"
                description = "Kanban simulation and analytics API"
            }
            servers {
                server { url = "/api/v1"; description = "Version 1 (current)" }
            }
        }
        swaggerUI { path = "/swagger" }
    }
}
```

Quando v2 for introduzida:
```kotlin
openApiSpec("v2") {
    info { version = "2.0.0" }
    servers { server { url = "/api/v2" } }
}
swaggerUI("v2") { path = "/swagger/v2" }
```

### Documentação de Deprecação na Spec

Quando um endpoint v1 for deprecado (ao lançar v2 equivalente):
```kotlin
get<SimulationsPath>(
    info("List Simulations", deprecated = true),
) { /* ... */ }
```

---

## Plano de Implementação

**1 sessão LLM — 1 PR:**

1. Criar `docs/api-versioning.md` com política completa (regras de compatibilidade, ciclo de vida,
   processo de deprecação)
2. Refatorar `Routing.kt`: extrair bloco `route("/api/v1") { ... }` explicitamente no código
   (atualmente o prefixo pode estar implícito em cada rota — padronizar)
3. Atualizar `OpenApi.kt`: adicionar `servers { }` explícito apontando para `/api/v1`
4. Adicionar header `API-Version: 1.0` nas responses (via `SecurityHeaders.kt` já planejado no GAP-AI)
5. Verificar que todos os endpoints usam o prefixo `/api/v1` consistentemente (nenhuma rota em `/`)
6. Testes: verificar que rotas em `/api/v1/...` retornam corretamente; verificar headers de versão

**Arquivos modificados:**
- `docs/api-versioning.md` (criar — política pública)
- `http_api/.../plugins/Routing.kt` (prefixo v1 explícito)
- `http_api/.../plugins/OpenApi.kt` (servers block)
- `http_api/.../plugins/SecurityHeaders.kt` (header `API-Version`)

---

## Consequências

**Positivas:**
- OpenAPI sobe de 8.5 → 9.0 — estratégia de versioning documentada e implementada
- Clientes externos têm garantia de 12 meses de suporte após deprecação
- Breaking changes futuras têm caminho claro (nova versão, não patch silencioso)
- Swagger UI apresenta versão ativa explicitamente

**Negativas:**
- Manutenção de múltiplas versões em paralelo aumenta complexidade do código de routing
- Ciclo de vida de 12 meses é um compromisso de longo prazo que requer discipline de equipe

**Neutras:**
- Nenhuma breaking change em v1 nesta ADR — apenas formalização do que já existe
- A implementação de v2 será uma ADR futura quando houver breaking change necessária

---

## Referências

- Fielding, Roy T. *Architectural Styles and the Design of Network-based Software Architectures*. 2000
- RFC 7231 — HTTP/1.1 Semantics and Content
- RFC 8594 — The Sunset HTTP Header Field
- Microsoft API Guidelines: https://github.com/microsoft/api-guidelines
- Ktor Routing: https://ktor.io/docs/server-routing.html
- Skill: [openapi-quality](.claude/skills/openapi-quality/SKILL.md)
- Skill: [evolutionary-change](.claude/skills/evolutionary-change/SKILL.md)
