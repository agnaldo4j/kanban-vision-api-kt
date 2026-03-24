---
name: owasp
description: >
  Aplique os princípios do OWASP Top 10 (2025) ao projetar, revisar e implementar
  código neste projeto. Use este skill ao criar rotas HTTP, autenticação JWT,
  queries de banco, serialização, logging ou qualquer funcionalidade com risco
  de segurança. Cobre todos os 10 riscos críticos com exemplos Kotlin/Ktor específicos
  do projeto e checklists de verificação.
argument-hint: "[rota, feature ou módulo a auditar (opcional)]"
allowed-tools: Read, Grep, Glob, Bash
---

# OWASP Top 10 — 2025 Security Guide

> Referência oficial: https://owasp.org/Top10/2025/
>
> Este skill aplica os 10 riscos mais críticos de segurança ao stack deste projeto:
> **Kotlin + Ktor 3.1 + JWT + Exposed ORM + PostgreSQL + Flyway + Koin + Arrow-kt**

---

## Como Usar Este Skill

Ao receber `/owasp [contexto]`:

1. Identifique qual(is) categoria(s) OWASP se aplicam ao contexto.
2. Revise o código existente contra os checklists abaixo.
3. Aplique os padrões de correção específicos do projeto.
4. Execute o checklist final antes de fechar.

---

## A01:2025 — Broken Access Control

**Risco:** 100% das aplicações testadas têm alguma forma de controle de acesso quebrado.
Taxa máxima de incidência: 20,15% | 1.839.701 ocorrências | 32.654 CVEs

### Padrões de Risco (Ktor)

```kotlin
// ❌ Rota sem autenticação
routing {
    get("/boards/{id}") {         // qualquer um pode acessar
        val id = call.parameters["id"]!!
        call.respond(boardService.find(id))
    }
}

// ❌ IDOR — acessa recurso de outro tenant sem verificar propriedade
get("/boards/{id}") {
    authenticate {
        val board = boardRepo.findById(id)  // não verifica se pertence ao tenant atual
        call.respond(board)
    }
}

// ❌ Privilege escalation via parâmetro manipulável
post("/admin/action") {
    val role = call.request.queryParameters["role"]  // cliente controla o papel
}
```

### Padrões Seguros (Ktor + JWT)

```kotlin
// ✅ Rota protegida com autenticação + verificação de propriedade
authenticate("jwt") {
    get("/boards/{id}") {
        val tenantId = call.principal<JWTPrincipal>()!!
            .payload.getClaim("tenantId").asString()
        val boardId = BoardId(UUID.fromString(call.parameters["id"]!!))

        getBoardUseCase.execute(GetBoardQuery(boardId, TenantId(tenantId)))
            .fold(
                ifLeft = { call.respondWithDomainError(it) },
                ifRight = { call.respond(HttpStatusCode.OK, it.toResponse()) }
            )
    }
}

// ✅ Verificação de propriedade no use case (domain boundary)
fun Raise<DomainError>.execute(query: GetBoardQuery): Board {
    val board = repository.findById(query.boardId).bind()
        ?: raise(DomainError.NotFound("Board", query.boardId.value.toString()))
    ensure(board.tenantId == query.tenantId) { DomainError.Forbidden("Board") }
    return board
}
```

### Checklist A01

- [ ] Todas as rotas não-públicas estão dentro de `authenticate("jwt") { }`.
- [ ] Todo use case verifica que o recurso pertence ao tenant do caller.
- [ ] Nenhum papel (role) ou permissão vem de parâmetros controlados pelo cliente.
- [ ] CORS configurado explicitamente — não `allowAnyHost()` em produção.
- [ ] Rate limiting ativo (já configurado: 100 req/min por IP).
- [ ] Tentativas de acesso negado são logadas (MDC com `tenantId` e `userId`).

---

## A02:2025 — Security Misconfiguration

**Risco:** 100% das aplicações testadas têm alguma misconfiguration (incidência média 3,00%).

### Padrões de Risco

```kotlin
// ❌ CORS aberto — qualquer origem pode fazer requests
install(CORS) {
    anyHost()                    // nunca em produção
    allowHeader(HttpHeaders.Authorization)
}

// ❌ Detalhes internos expostos em erros HTTP
install(StatusPages) {
    exception<Throwable> { call, cause ->
        call.respond(HttpStatusCode.InternalServerError, cause.stackTraceToString())
    }
}

// ❌ Swagger UI exposto em produção
routing {
    swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
    // sem restrição de ambiente
}
```

### Padrões Seguros

```kotlin
// ✅ CORS restrito por lista de origens
install(CORS) {
    allowHost(System.getenv("ALLOWED_ORIGIN") ?: "localhost:3000", schemes = listOf("https"))
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
}

// ✅ Erros genéricos para o cliente, detalhes apenas em log
install(StatusPages) {
    exception<Throwable> { call, cause ->
        val correlationId = MDC.get("correlationId") ?: UUID.randomUUID().toString()
        log.error("Unhandled error [correlationId=$correlationId]", cause)
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "Internal error", "correlationId" to correlationId)
        )
    }
}

// ✅ Swagger apenas em dev/staging
if (System.getenv("ENABLE_SWAGGER") == "true") {
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
    }
}
```

### Checklist A02

- [ ] CORS configurado com lista explícita de origens (sem `anyHost()`).
- [ ] Stack traces nunca retornados ao cliente — apenas `correlationId`.
- [ ] Swagger UI desabilitado em produção (`ENABLE_SWAGGER != true`).
- [ ] `JWT_DEV_MODE` nunca `true` em produção (cria endpoint `/auth/token`).
- [ ] Variáveis de ambiente validadas no startup — falhar rápido se ausentes.
- [ ] Headers de segurança configurados (HSTS, X-Content-Type-Options, X-Frame-Options).
- [ ] Portas desnecessárias fechadas no Dockerfile/K8s (somente 8080 exposta).

---

## A03:2025 — Software Supply Chain Failures

**Risco:** Ataques de alto impacto (SolarWinds, Log4Shell, Shai-Hulud worm npm 2025).

### Ações Preventivas para Este Projeto

```bash
# Escanear dependências com vulnerabilidades conhecidas (CVE/NVD)
./gradlew dependencyCheckAnalyze          # OWASP Dependency Check (adicionar se necessário)

# Verificar versões desatualizadas
./gradlew dependencyUpdates               # ben-manes/gradle-versions-plugin

# Verificar integridade de artefatos (já feito pelo Gradle com checksums)
./gradlew --write-verification-metadata sha256
```

### Configuração Recomendada

```kotlin
// build.gradle.kts (root) — adicionar se não existir
plugins {
    id("org.owasp.dependencycheck") version "10.0.4"
}

dependencyCheck {
    failBuildOnCVSS = 7.0f              // falha em vulnerabilidades HIGH+
    format = "HTML"
    outputDirectory = "build/reports/dependency-check"
    suppressionFile = "config/dependency-check-suppressions.xml"
}
```

### Checklist A03

- [ ] Todas as versões de dependências são declaradas explicitamente (sem `+` ou ranges).
- [ ] `gradle/verification-metadata.xml` com checksums SHA-256 atualizado.
- [ ] CI executa scan de vulnerabilidades (OWASP Dependency Check ou Snyk).
- [ ] Dependências transitivas monitoradas (não só diretas).
- [ ] Base Docker usa digest fixo (não `latest`): `eclipse-temurin:21-jre@sha256:...`.
- [ ] Nenhuma dependência de fonte não-oficial ou não verificada.

---

## A04:2025 — Cryptographic Failures

**Risco:** Taxa máxima de incidência: 13,77% | 1.665.348 ocorrências | 2.185 CVEs.

### Padrões de Risco

```kotlin
// ❌ Algoritmo hash fraco para senhas
val hash = MessageDigest.getInstance("MD5").digest(password.toByteArray())

// ❌ Chave JWT hardcoded
val jwtSecret = "mysecretkey123"

// ❌ Dados sensíveis em texto plano no banco
data class User(val cpf: String, val password: String)  // senha em plain text

// ❌ ECB mode (sem IV — mesmos dados = mesmo ciphertext)
val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
```

### Padrões Seguros

```kotlin
// ✅ Segredo JWT via variável de ambiente — validado no startup
val jwtSecret = requireNotNull(System.getenv("JWT_SECRET")) {
    "JWT_SECRET environment variable is required"
}
require(jwtSecret.length >= 32) { "JWT_SECRET must be at least 32 characters" }

// ✅ JWT com claims corretos — issuer, audience, expiração curta
JWT.create()
    .withIssuer(jwtIssuer)
    .withAudience(jwtAudience)
    .withClaim("tenantId", tenantId.value.toString())
    .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
    .sign(Algorithm.HMAC256(jwtSecret))

// ✅ Validação JWT completa no Ktor
install(Authentication) {
    jwt("jwt") {
        verifier(
            JWT.require(Algorithm.HMAC256(jwtSecret))
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .build()
        )
        validate { credential ->
            if (credential.payload.getClaim("tenantId").asString().isNotBlank())
                JWTPrincipal(credential.payload) else null
        }
        challenge { _, _ ->
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
        }
    }
}

// ✅ TLS forçado — HSTS header
install(DefaultHeaders) {
    header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    header("X-Content-Type-Options", "nosniff")
    header("X-Frame-Options", "DENY")
    header("Referrer-Policy", "strict-origin-when-cross-origin")
}
```

### Checklist A04

- [ ] `JWT_SECRET` ≥ 32 chars, via env var — nunca hardcoded.
- [ ] JWT tem expiração curta (≤ 1 hora para access token).
- [ ] JWT valida `issuer`, `audience`, `expiresAt` — sem bypass.
- [ ] TLS 1.2+ obrigatório — HSTS habilitado.
- [ ] Nenhum uso de MD5, SHA-1, DES, ECB.
- [ ] Dados sensíveis não cacheados em responses HTTP.
- [ ] Segredos em `docker-compose.yml` via `secrets:` ou env var — nunca em texto literal.

---

## A05:2025 — Injection

**Risco:** 100% das apps vulneráveis a alguma forma de injection. 37 CWEs | 62.445 CVEs.
**Subtipos:** SQL, NoSQL, OS Command, LDAP, XSS (Cross-Site Scripting), Prompt Injection (LLM).

### Padrões de Risco — SQL Injection

```kotlin
// ❌ Concatenação de string em query SQL — injetável
fun findByName(name: String): Board? {
    val sql = "SELECT * FROM boards WHERE name = '$name'"   // VULNERÁVEL
    return connection.prepareStatement(sql).executeQuery()...
}

// ❌ String template em query Exposed
transaction {
    exec("SELECT * FROM boards WHERE name = '$name'")      // VULNERÁVEL
}
```

### Padrões Seguros — Exposed DSL (mandatório)

```kotlin
// ✅ Exposed DSL — parametrizado por padrão, sem interpolação
fun findByName(name: String): Board? = transaction {
    BoardsTable
        .selectAll()
        .where { BoardsTable.name eq name }    // parametrizado automaticamente
        .singleOrNull()
        ?.toDomain()
}

// ✅ Se precisar de raw SQL — usar PreparedStatement com parâmetros
connection.prepareStatement("SELECT * FROM boards WHERE name = ?").apply {
    setString(1, name)
}.executeQuery()
```

### Padrões de Risco — Command Injection

```kotlin
// ❌ Shell command com input do usuário
Runtime.getRuntime().exec("ls ${userInput}")

// ✅ Nunca executar comandos shell com dados do usuário
// Se necessário, use ProcessBuilder com lista de argumentos (sem shell)
ProcessBuilder("ls", "-la", safePath).start()
```

### Checklist A05

- [ ] Todas as queries usam Exposed DSL (parâmetros implícitos) — sem concatenação.
- [ ] Nenhum `exec()` ou `executeQuery(String)` com interpolação de variáveis.
- [ ] Input do usuário validado com `ensure()` antes de qualquer operação.
- [ ] Nenhum `Runtime.exec()` com dados do usuário.
- [ ] Respostas de erro não refletem input do usuário sem sanitização.
- [ ] SAST (`./gradlew detekt`) verde — zero violações.

---

## A06:2025 — Insecure Design

**Risco:** Falhas de design não podem ser corrigidas apenas com melhor código —
exigem redesenho. CWEs: 256, 269, 434, 501, 522.

### Padrões de Risco

```kotlin
// ❌ Upload de arquivo sem validação de tipo
post("/upload") {
    val file = call.receiveMultipart()
    // salva sem verificar tipo, tamanho ou conteúdo
}

// ❌ Regra de negócio crítica sem validação no domínio
// Use case confia que o caller já validou — sem segunda verificação
fun execute(cmd: DeleteBoardCommand) {
    repository.delete(cmd.boardId)  // sem verificar propriedade, sem soft-delete
}
```

### Padrões Seguros

```kotlin
// ✅ Threat modeling → domain enforces constraints
fun Raise<BoardError>.addColumn(name: String): Board {
    ensure(name.isNotBlank()) { BoardError.EmptyColumnName }
    ensure(name.length <= 100) { BoardError.ColumnNameTooLong }
    ensure(columns.none { it.name == name }) { BoardError.DuplicateColumnName(name) }
    return copy(columns = columns + Column(ColumnId.generate(), name, emptyList()))
}

// ✅ Upload com validação estrita
post("/upload") {
    authenticate("jwt") {
        val part = call.receiveMultipart().readPart() as? PartData.FileItem
            ?: return@post call.respond(HttpStatusCode.BadRequest, "File required")

        val contentType = part.contentType
        val allowedTypes = setOf(ContentType.Image.PNG, ContentType.Image.JPEG)
        ensure(contentType in allowedTypes) { /* ... */ }

        val bytes = part.streamProvider().readBytes()
        ensure(bytes.size <= 5 * 1024 * 1024) { /* max 5MB */ }
    }
}
```

### Checklist A06

- [ ] ADR criado para toda feature com impacto de segurança (auth, upload, pagamento).
- [ ] Threat model documentado para fluxos críticos (ver skill `/adr`).
- [ ] Domínio valida todas as invariantes — use case nunca confia no caller.
- [ ] Upload de arquivos: tipo, tamanho e conteúdo validados.
- [ ] Rate limiting granular para operações sensíveis (login, reset, signup).
- [ ] Tenant isolation validada no agregado, não só na rota.

---

## A07:2025 — Authentication Failures

**Risco:** Taxa máxima de incidência: 15,80% | 1.120.673 ocorrências | 7.147 CVEs | 36 CWEs.

### Padrões de Risco

```kotlin
// ❌ JWT aceito sem validação de claims
jwt("jwt") {
    validate { credential -> JWTPrincipal(credential.payload) }  // aceita qualquer token
}

// ❌ JWT_DEV_MODE vazando em produção
val devMode = System.getenv("JWT_DEV_MODE")?.toBoolean() ?: true  // default inseguro

// ❌ Sem rate limiting em endpoints de autenticação
post("/auth/token") {
    // sem proteção contra brute force
}

// ❌ Logout não invalida o token (JWT stateless sem blocklist)
post("/auth/logout") {
    call.respond(HttpStatusCode.OK)  // token ainda válido até expirar
}
```

### Padrões Seguros

```kotlin
// ✅ JWT_DEV_MODE — default false, explícito e logado
val devMode = System.getenv("JWT_DEV_MODE")?.toBoolean() ?: false
if (devMode) {
    log.warn("JWT_DEV_MODE=true — POST /auth/token is enabled. DO NOT use in production!")
    routing { devAuthRoutes() }
}

// ✅ Rate limiting específico para auth
rateLimit(RateLimitName("auth")) {
    rateLimiter(limit = 5, refillPeriod = 1.minutes)   // 5 tentativas/min
    register(RateLimitName("auth"))
}

routing {
    rateLimit(RateLimitName("auth")) {
        post("/auth/token") { ... }
    }
}

// ✅ Validação rigorosa de JWT
jwt("jwt") {
    verifier(
        JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(issuer)
            .withAudience(audience)
            .acceptLeeway(3)         // 3 segundos de clock skew
            .build()
    )
    validate { credential ->
        val tenantId = credential.payload.getClaim("tenantId").asString()
        val userId = credential.payload.getClaim("userId").asString()
        if (tenantId.isNotBlank() && userId.isNotBlank())
            JWTPrincipal(credential.payload) else null
    }
}
```

### Checklist A07

- [ ] `JWT_DEV_MODE=true` nunca em produção — validado no startup.
- [ ] Rate limiting em `/auth/*` — stricter que o limite global de 100 req/min.
- [ ] JWT valida: `issuer`, `audience`, `tenantId`, `userId`, `expiresAt`.
- [ ] Tokens com expiração curta — refresh token para renovação.
- [ ] Sem credenciais default — banco exige senha forte via env var.
- [ ] Enumerate protection — mesmo tempo de resposta para usuário inexistente.
- [ ] Logout invalida token (blocklist em Redis ou expiração muito curta).

---

## A08:2025 — Software or Data Integrity Failures

**Risco:** 14 CWEs mapeados. Inclui CWE-502 (deserialização insegura), CWE-829 (fontes não confiáveis).

### Padrões de Risco

```kotlin
// ❌ Desserialização de objeto Java arbitrário
val obj = ObjectInputStream(inputStream).readObject()  // RCE potencial

// ❌ Aceitar JSON sem schema validation
@Serializable
data class UpdateRequest(
    val role: String,          // cliente pode enviar "ADMIN"
    val tenantId: String,      // cliente pode sobreescrever tenant
    val internalFlag: Boolean  // campo interno exposto
)
```

### Padrões Seguros

```kotlin
// ✅ kotlinx.serialization — type-safe, sem Java ObjectInputStream
@Serializable
data class CreateBoardRequest(
    val name: String,          // apenas campos que o cliente pode enviar
    val description: String?
)

// ✅ Separar DTO de entrada do modelo interno
fun CreateBoardRequest.toCommand(tenantId: TenantId): CreateBoardCommand {
    require(name.isNotBlank()) { "name is required" }
    require(name.length <= 100) { "name too long" }
    return CreateBoardCommand(name = name.trim(), tenantId = tenantId)
}

// ✅ CI verifica integridade de artefatos
// gradle/verification-metadata.xml com SHA-256 de todas as dependências
```

### Checklist A08

- [ ] Nenhum uso de `ObjectInputStream` ou Java serialization nativa.
- [ ] DTOs de entrada separados dos modelos de domínio (sem binding direto).
- [ ] kotlinx.serialization com `@Serializable` em todos os DTOs HTTP.
- [ ] Campos internos (`role`, `tenantId`, `isAdmin`) não aceitos em request bodies.
- [ ] `gradle/verification-metadata.xml` gerado e versionado.
- [ ] Imagem Docker construída via CI com digest verificado.

---

## A09:2025 — Security Logging and Alerting Failures

**Risco:** Exemplos reais: brecha de 7 anos não detectada (3,5M registros de crianças),
multa de 20M+ GDPR por vulnerabilidade em app de pagamento.

### Padrões de Risco

```kotlin
// ❌ Dados sensíveis nos logs
log.info("User login: email=${user.email}, password=${user.password}")

// ❌ Falha de auth sem log
authenticate("jwt") {
    challenge { _, _ ->
        call.respond(HttpStatusCode.Unauthorized)  // sem registrar tentativa
    }
}

// ❌ Log injection via input do usuário
log.info("Search: ${userInput}")  // usuário pode injetar newlines/control chars
```

### Padrões Seguros (MDC + SLF4J + Logback)

```kotlin
// ✅ MDC com contexto de segurança — sem dados sensíveis
fun ApplicationCall.setupSecurityMDC() {
    val tenantId = principal<JWTPrincipal>()?.payload?.getClaim("tenantId")?.asString()
    val userId = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
    MDC.put("tenantId", tenantId ?: "anonymous")
    MDC.put("userId", userId ?: "anonymous")
    MDC.put("correlationId", request.headers["X-Correlation-ID"] ?: UUID.randomUUID().toString())
    MDC.put("remoteAddr", request.origin.remoteAddress)
}

// ✅ Log de eventos de segurança — sem dados sensíveis
log.warn("Authentication failed: tenantId={}, remoteAddr={}, path={}",
    MDC.get("tenantId"), MDC.get("remoteAddr"), request.uri)

// ✅ Sanitizar input antes de logar
fun String.sanitizeForLog(): String =
    replace("\n", "\\n").replace("\r", "\\r").take(500)

log.info("Search query: {}", userInput.sanitizeForLog())
```

### Checklist A09

- [ ] Nenhum log contém: senha, token, CPF, cartão, chave de API.
- [ ] Toda falha de autenticação é logada com contexto (IP, path, tenantId).
- [ ] MDC propagado em todas as requisições (correlationId, tenantId, userId).
- [ ] Logs em formato JSON em produção (`LOG_FORMAT=json`).
- [ ] Input do usuário sanitizado antes de logar (sem newlines, truncado).
- [ ] Alertas configurados no Grafana para taxa anormal de 401/403 (ver skill `/opentelemetry`).
- [ ] Logs com integridade — append-only, sem alteração possível.

---

## A10:2025 — Mishandling of Exceptional Conditions *(NOVO em 2025)*

**Risco:** 24 CWEs mapeados. Inclui: CWE-209 (info exposure via erros),
CWE-476 (null pointer), CWE-636 (fail open = falhar aberto).

### Padrões de Risco

```kotlin
// ❌ Fail open — erro vira acesso permitido
fun checkAccess(userId: String): Boolean {
    return try {
        accessService.check(userId)
    } catch (e: Exception) {
        true   // erro → acesso liberado — NUNCA faça isso
    }
}

// ❌ Exceção expõe stack trace ao cliente
exception<SQLException> { call, cause ->
    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "error")
}

// ❌ Recurso não liberado em exceção
fun processFile(path: String): String {
    val stream = FileInputStream(path)
    return stream.readAllBytes().toString(Charsets.UTF_8)
    // stream nunca fechado se exceção ocorrer
}
```

### Padrões Seguros (Arrow Either + Ktor StatusPages)

```kotlin
// ✅ Fail closed — erro implica negação de acesso
fun Raise<SecurityError>.checkAccess(userId: String): AccessResult =
    catch({
        accessService.check(userId)
    }) { e: Exception ->
        log.error("Access check failed for userId={} — denying access", userId, e)
        raise(SecurityError.AccessCheckFailed)   // nega por padrão
    }

// ✅ StatusPages centralizado — erro genérico ao cliente, detalhes no log
install(StatusPages) {
    exception<DomainError.ValidationError> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
    }
    exception<DomainError.NotFound> { call, cause ->
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Resource not found"))
    }
    exception<Throwable> { call, cause ->
        val correlationId = MDC.get("correlationId") ?: UUID.randomUUID().toString()
        log.error("Unhandled exception [correlationId={}]", correlationId, cause)
        call.respond(HttpStatusCode.InternalServerError,
            mapOf("error" to "Internal error", "correlationId" to correlationId))
    }
}

// ✅ Recursos gerenciados com use/try-with-resources
fun processFile(path: String): String =
    FileInputStream(path).use { stream ->
        stream.readAllBytes().toString(Charsets.UTF_8)
    }

// ✅ Either encapsula falha — never throws
fun Raise<DomainError>.executeWithRollback(operation: () -> Unit) =
    catch({ operation() }) { e: Exception ->
        log.error("Operation failed — rolling back", e)
        raise(DomainError.PersistenceError(e.message ?: "Operation failed"))
    }
```

### Checklist A10

- [ ] Nenhum `catch` com `return true` ou acesso liberado em caso de erro.
- [ ] `StatusPages` instalado e cobre todos os tipos de erro do domínio.
- [ ] Stack traces nunca enviados ao cliente.
- [ ] Recursos (streams, conexões) gerenciados com `use { }` ou `transaction { }`.
- [ ] Transações revertidas em caso de falha (Exposed `transaction {}` faz rollback automático).
- [ ] Rate limiting e quotas de recursos para prevenir exaustão.

---

## Checklist de Segurança Integrado — Antes de Abrir PR

### Controle de Acesso (A01)
- [ ] Toda rota sensível dentro de `authenticate("jwt") { }`.
- [ ] Propriedade de recurso verificada no use case (tenant check).
- [ ] CORS sem `anyHost()`.

### Configuração (A02)
- [ ] `JWT_DEV_MODE=false` em produção.
- [ ] Swagger UI desabilitado em produção.
- [ ] Stack traces não vazam para responses.

### Dependências (A03)
- [ ] Versões explícitas em todas as dependências.
- [ ] CI com scan de vulnerabilidades.

### Criptografia (A04)
- [ ] Segredos via env vars — nenhum hardcoded.
- [ ] JWT com expiração curta e claims validados.
- [ ] TLS + HSTS habilitados.

### Injection (A05)
- [ ] Apenas Exposed DSL — sem raw SQL com interpolação.
- [ ] Input validado com `ensure()` no domínio.

### Design (A06)
- [ ] ADR para features com impacto de segurança.
- [ ] Invariantes de domínio no agregado, não no caller.

### Autenticação (A07)
- [ ] Rate limiting em endpoints de auth.
- [ ] JWT valida todos os claims obrigatórios.

### Integridade (A08)
- [ ] DTOs de entrada separados de modelos internos.
- [ ] Sem Java ObjectInputStream.

### Logging (A09)
- [ ] Sem dados sensíveis em logs.
- [ ] Falhas de auth logadas com contexto.
- [ ] MDC propagado.

### Exceções (A10)
- [ ] Fail closed — erro implica acesso negado.
- [ ] `StatusPages` cobre todos os casos.
- [ ] Recursos liberados com `use { }`.

---

## Relação com Outros Skills

| Skill | Como se conecta com OWASP |
|---|---|
| **clean-architecture** | Boundaries claros = superfície de ataque menor |
| **fp-oo-kotlin** | `Either`/`Raise` = sem exceções invisíveis (A10) |
| **testing-and-observability** | Testes de segurança + alertas para violações (A09) |
| **opentelemetry** | Métricas de 401/403 + traces de falhas de auth |
| **openapi-quality** | Documenta security schemes corretamente |
| **definition-of-done** | Seção 4 — Security and Compliance |
| **adr** | Toda mudança de auth/crypto exige ADR aprovado |

---

## Ferramentas de Scanning

```bash
# Detekt (análise estática já no CI)
./gradlew detekt

# OWASP Dependency Check (adicionar ao build.gradle.kts se necessário)
./gradlew dependencyCheckAnalyze

# Verificar segredos no código
git log --all -p | grep -iE "(password|secret|apikey|token)\s*=" | head -20

# Verificar headers de segurança na resposta
curl -I http://localhost:8080/health | grep -iE "(strict-transport|x-content|x-frame|referrer)"

# Verificar JWT sem validação de expiração
./gradlew :http_api:test --tests "*SecurityTest*"
```