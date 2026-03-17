# ADR-0005 — Autenticação e Autorização: JWT Bearer Tokens

## Cabeçalho

| Campo     | Valor                                                        |
|-----------|--------------------------------------------------------------|
| Status    | Proposta                                                     |
| Data      | 2026-03-16                                                   |
| Autores   | @agnaldo4j                                                   |
| Branch    | feat/adr-0005-autenticacao-jwt-bearer                        |
| PR        | —                                                            |
| Supersede | —                                                            |

---

## Contexto e Motivação

O projeto `kanban-vision-api-kt` expõe atualmente **todas as rotas sem nenhuma
autenticação**: qualquer cliente com acesso de rede pode criar boards, deletar cenários,
executar simulações e consultar dados de qualquer tenant. Este é o `GAP-A` identificado
no ADR-0004 como bloqueador de produção (P1 — Crítico).

O sistema possui uma entidade `Tenant` no domínio, sinalizando que multi-tenancy é uma
preocupação de design desde o início. O mecanismo de autenticação escolhido agora deve
ser compatível com a evolução para isolamento de dados por tenant no futuro.

As rotas atuais estão organizadas em dois grupos em `Routing.kt`:
- `/health/*` — health checks (devem permanecer públicas para liveness/readiness probes K8s)
- `/api/v1/**` — todas as rotas de negócio (`boards`, `cards`, `columns`, `scenarios`,
  `scenarioAnalytics`) — devem ser protegidas

---

## Forças (Decision Drivers)

- [ ] Zero dependência de estado do servidor para validação de tokens — compatível com múltiplas instâncias (horizontal scaling)
- [ ] Mecanismo padrão de mercado reconhecido por qualquer cliente REST ou ferramenta (curl, Postman, etc.)
- [ ] Ktor 3.1.2 já possui plugin oficial (`ktor-server-auth-jwt`) — sem introdução de framework externo
- [ ] Claims do token devem poder carregar `tenantId` para futura autorização por tenant sem mudança de contrato
- [ ] Tokens devem expirar — credenciais comprometidas têm janela de exposição limitada
- [ ] A solução deve ser configurável via variáveis de ambiente — segredo JWT nunca hardcoded
- [ ] `/health/live`, `/health/ready`, `/swagger`, `/openapi` devem permanecer públicas
- [ ] Nenhuma alteração na camada `domain` ou `usecases` — autenticação é responsabilidade do `http_api`

---

## Opções Consideradas

- **Opção A**: JWT Bearer Tokens (stateless, emitido pela própria API)
- **Opção B**: API Key (header `X-API-Key`, chave armazenada no banco)
- **Opção C**: OAuth2 / OIDC com IdP externo (Keycloak, Auth0, Google)

---

## Decisão

> **Escolhemos a Opção A — JWT Bearer Tokens** porque é o mecanismo stateless mais
> adequado para uma API REST que precisa escalar horizontalmente e que possui multi-tenancy
> como requisito futuro. O JWT permite embutir `tenantId` e `roles` como claims sem
> round-trip ao banco em cada requisição. O plugin `ktor-server-auth-jwt` é oficialmente
> suportado na versão Ktor 3.1.2 já em uso, sem introduzir nova dependência de framework.
> A evolução futura para OAuth2/OIDC é viabilizada pela separação do emissor (`issuer`)
> em variável de ambiente — substituir a emissão interna por um IdP externo não altera
> nenhuma rota existente.

---

## Análise das Opções

### Opção A — JWT Bearer Tokens

**Prós:**
- Stateless: validação feita localmente com a chave pública/secreta — sem consulta ao banco por request
- Expiração nativa (`exp` claim): tokens comprometidos expiram automaticamente
- Claims extensíveis: `tenantId`, `roles`, `sub` carregados no token — pronto para multi-tenancy
- Suporte oficial Ktor: `ktor-server-auth-jwt` integra com `install(Authentication)` e o plugin `authenticate {}` no DSL de rotas
- Standard RFC 7519 reconhecido por qualquer cliente

**Contras:**
- Sem revogação nativa: um token válido não pode ser invalidado antes do `exp` sem estado server-side (blocklist)
- Segredo JWT deve ser rotacionado cuidadosamente em produção
- Requer mecanismo de emissão de tokens (endpoint `POST /auth/token` ou tool externa)

**Mitigação da revogação**: TTL curto (ex: 1h para access token) + refresh token strategy quando necessário. Para o escopo atual (fase de hardening), TTL de 24h é aceitável.

---

### Opção B — API Key

**Prós:**
- Extremamente simples de implementar: interceptor que consulta banco por chave
- Fácil de revogar: deletar a linha no banco

**Contras:**
- Stateful: toda requisição consulta o banco — latência adicional + ponto de falha
- Sem expiração automática por design — responsabilidade de gerenciamento manual
- Não carrega claims — sem tenantId, sem roles: autorização requer consulta adicional ao banco
- Não é padrão para APIs REST de negócio — dificulta integração com clientes modernos (ex: ferramentas que esperem `Authorization: Bearer`)

---

### Opção C — OAuth2 / OIDC com IdP externo

**Prós:**
- SSO, gestão de usuários, MFA, federação de identidades fora da aplicação
- Delegação completa — a API apenas valida tokens, não os emite
- Caminho natural para SaaS multi-tenant com usuários externos

**Contras:**
- Dependência crítica de infraestrutura externa (Keycloak, Auth0) — complexidade operacional alta para a fase atual
- Requer deploy e operação do IdP antes de qualquer deploy da API
- Overkill para o estágio atual: o projeto ainda não tem usuários, apenas um único tenant de desenvolvimento
- Pode ser adotado no futuro simplesmente trocando o `issuer` no `application.conf` — não bloqueia a Opção A agora

---

## Consequências

**Positivas:**
- Todas as rotas `/api/v1/**` passam a exigir `Authorization: Bearer <token>` — zero acesso anônimo a dados de negócio
- Stateless: autenticação não adiciona latência de banco por requisição
- `tenantId` no claim JWT é o alicerce para autorização por tenant quando o domínio evoluir (`Board` pertence ao `Tenant`)
- Ktor `authenticate {}` bloco protege rotas de forma declarativa — nenhuma rota pode ser "esquecida" fora do bloco
- Evolução para OAuth2/OIDC é um swap de `issuer` + `jwksUri` no config — sem mudança de rotas

**Negativas / Trade-offs:**
- Sem revogação antes do `exp`: mitigado com TTL curto e futura implementação de refresh token + blocklist Redis quando necessário
- É necessário um endpoint `POST /auth/token` ou documentação de como emitir tokens para desenvolvimento local — tratado no Plano de Implementação
- OpenAPI precisa ser atualizado com `securityScheme: BearerAuth` em todas as rotas protegidas (GAP-N)

**Neutras:**
- Camadas `domain` e `usecases` não são modificadas
- `health/live` e `health/ready` permanecem públicos — K8s probes não necessitam de token

---

## Plano de Implementação

### Fase 1: Dependências e configuração

- [ ] Adicionar em `http_api/build.gradle.kts`:
  ```kotlin
  implementation("io.ktor:ktor-server-auth-jvm:3.1.2")
  implementation("io.ktor:ktor-server-auth-jwt-jvm:3.1.2")
  ```
- [ ] Adicionar em `application.conf`:
  ```hocon
  jwt {
      secret   = "dev-secret-change-in-production"
      secret   = ${?JWT_SECRET}
      issuer   = "kanban-vision-api"
      issuer   = ${?JWT_ISSUER}
      audience = "kanban-vision-clients"
      audience = ${?JWT_AUDIENCE}
      realm    = "Kanban Vision API"
      ttlMs    = 86400000
      ttlMs    = ${?JWT_TTL_MS}
  }
  ```

### Fase 2: Plugin de autenticação

- [ ] Criar `http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/Authentication.kt`:
  ```kotlin
  package com.kanbanvision.httpapi.plugins

  import com.auth0.jwt.JWT
  import com.auth0.jwt.algorithms.Algorithm
  import io.ktor.server.application.Application
  import io.ktor.server.auth.authentication
  import io.ktor.server.auth.jwt.JWTPrincipal
  import io.ktor.server.auth.jwt.jwt

  fun Application.configureAuthentication() {
      val secret   = environment.config.property("jwt.secret").getString()
      val issuer   = environment.config.property("jwt.issuer").getString()
      val audience = environment.config.property("jwt.audience").getString()
      val realm    = environment.config.property("jwt.realm").getString()

      authentication {
          jwt("jwt-auth") {
              this.realm = realm
              verifier(
                  JWT.require(Algorithm.HMAC256(secret))
                      .withAudience(audience)
                      .withIssuer(issuer)
                      .build()
              )
              validate { credential ->
                  if (credential.payload.audience.contains(audience)) JWTPrincipal(credential.payload)
                  else null
              }
              challenge { _, _ ->
                  call.respond(
                      HttpStatusCode.Unauthorized,
                      DomainErrorResponse(error = "Token inválido ou ausente", requestId = call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown")
                  )
              }
          }
      }
  }
  ```

### Fase 3: Proteger rotas

- [ ] Atualizar `Routing.kt` para envolver `/api/v1` em bloco `authenticate("jwt-auth")`:
  ```kotlin
  routing {
      healthRoutes()             // público — probes K8s
      swaggerUI(...)             // público — documentação
      route("/openapi") { ... }  // público — spec JSON
      authenticate("jwt-auth") {
          route("/api/v1") {
              boardRoutes()
              cardRoutes()
              columnRoutes()
              scenarioRoutes()
              scenarioAnalyticsRoutes()
          }
      }
  }
  ```
- [ ] Registrar `configureAuthentication()` em `Main.kt` **antes** de `configureRouting()`

### Fase 4: Endpoint de emissão de token (desenvolvimento)

- [ ] Criar `http_api/src/main/kotlin/com/kanbanvision/httpapi/routes/AuthRoutes.kt` com `POST /auth/token`:
  - Aceita `{ "subject": "dev", "tenantId": "<uuid>" }` no body
  - Gera token JWT com `sub`, `tenantId`, `aud`, `iss`, `exp`
  - **Disponível apenas quando `JWT_DEV_MODE=true`** (proteger em produção com flag de ambiente)
  - Este endpoint é temporário — em produção será substituído por IdP externo (OAuth2)

### Fase 5: Testes

- [ ] Teste de rota autenticada: request com token válido → `200 OK`
- [ ] Teste de rota sem token: `GET /api/v1/boards` sem header → `401 Unauthorized`
- [ ] Teste com token expirado: token com `exp` no passado → `401 Unauthorized`
- [ ] Teste com token inválido (assinatura errada): → `401 Unauthorized`
- [ ] Teste de rota pública: `GET /health/ready` sem token → `200 OK`
- [ ] `./gradlew testAll` verde — cobertura ≥ 95% mantida

### Fase 6: OpenAPI

- [ ] Adicionar `securityScheme` Bearer em `OpenApi.kt`:
  ```kotlin
  securityScheme("BearerAuth") {
      type = SecuritySchemeType.HTTP
      scheme = "bearer"
      bearerFormat = "JWT"
  }
  ```
- [ ] Adicionar `security("BearerAuth")` em todas as rotas protegidas na spec

---

## Garantias de Qualidade

### DOD — Definition of Done

- [ ] **1. Contrato e Rastreabilidade**: branch `feat/adr-0005-autenticacao-jwt-bearer` ↔ PR ↔ CI rastreáveis
- [ ] **2. Testes Técnicos**: unit tests para `configureAuthentication()`, integration tests para cada cenário de autenticação (válido, expirado, ausente, assinatura inválida)
- [ ] **3. Versionamento e Compatibilidade**: nenhuma rota pública quebrada; `/health/*` e `/swagger` permanecem acessíveis sem token
- [ ] **4. Segurança e Compliance**: `JWT_SECRET` lido de variável de ambiente — nunca hardcoded em código ou teste; segredo de dev documentado como não usar em produção
- [ ] **5. CI/CD**: `./gradlew testAll` verde no CI; cobertura JaCoCo ≥ 95%
- [ ] **6. Observabilidade**: `requestId` preservado nas respostas de erro de autenticação; log de `warn` para tentativas com token inválido (sem logar o token)
- [ ] **7. Documentação**: OpenAPI atualizado com `securityScheme: BearerAuth` + `security` em todas as rotas protegidas

---

## Referências

- ADR-0004 — GAP-A: Autenticação/Autorização (bloqueador P1)
- Ktor Auth JWT: https://ktor.io/docs/server-jwt.html
- RFC 7519 — JSON Web Token: https://www.rfc-editor.org/rfc/rfc7519
- Skill: [adr](.claude/skills/adr/SKILL.md)
- Skill: [clean-architecture](.claude/skills/clean-architecture/SKILL.md)
- Skill: [evolutionary-change](.claude/skills/evolutionary-change/SKILL.md)
- Skill: [definition-of-done](.claude/skills/definition-of-done/SKILL.md)
