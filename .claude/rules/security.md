# Security — OWASP Top 10 (2025) Rules

> Referência: https://owasp.org/Top10/2025/
> Skill completo: `/owasp`
> Esta regra é carregada automaticamente para todo arquivo `**/*.kt`.

---

## Regras Invioláveis (Zero Tolerance)

### 1. Sem Segredos Hardcoded (A04)

```kotlin
// ❌ PROIBIDO — causa deny no hook guard-security.sh
val jwtSecret = "mysecretkey"
val password  = "admin123"
val apiKey    = "sk-abc123"

// ✅ OBRIGATÓRIO — sempre de variável de ambiente
val jwtSecret = requireNotNull(System.getenv("JWT_SECRET")) { "JWT_SECRET is required" }
```

**Hook ativo**: `guard-security.sh` detecta e bloqueia escrita de segredos hardcoded.

---

### 2. Sem SQL por Concatenação de String (A05)

```kotlin
// ❌ PROIBIDO — SQL injection
val sql = "SELECT * FROM boards WHERE name = '$name'"
connection.prepareStatement(sql).executeQuery()

// ❌ PROIBIDO — exec com interpolação
transaction { exec("SELECT * FROM boards WHERE id = '$id'") }

// ✅ OBRIGATÓRIO — Exposed DSL (parametrizado automaticamente)
BoardsTable.selectAll().where { BoardsTable.id eq boardId }
```

---

### 3. Toda Rota Não-Pública Dentro de `authenticate("jwt-auth")` (A01)

```kotlin
// ❌ PROIBIDO
routing {
    get("/boards") { ... }      // sem autenticação

// ✅ OBRIGATÓRIO
routing {
    authenticate("jwt-auth") {
        get("/boards") { ... }
    }
}
```

Exceções permitidas sem `authenticate`:
- `GET /health`
- `GET /metrics`
- `GET /swagger` (somente quando `ENABLE_SWAGGER=true`)
- `POST /auth/token` (somente quando `JWT_DEV_MODE=true`)

---

### 4. Stack Traces Nunca ao Cliente (A10)

```kotlin
// ❌ PROIBIDO
call.respond(HttpStatusCode.InternalServerError, cause.stackTraceToString())
call.respond(HttpStatusCode.InternalServerError, cause.message ?: "error")

// ✅ OBRIGATÓRIO — correlationId para rastrear sem expor detalhes
val correlationId = MDC.get("correlationId") ?: UUID.randomUUID().toString()
log.error("Error [correlationId={}]", correlationId, cause)
call.respond(HttpStatusCode.InternalServerError,
    mapOf("error" to "Internal error", "correlationId" to correlationId))
```

---

### 5. Sem Dados Sensíveis em Logs (A09)

```kotlin
// ❌ PROIBIDO
log.info("Login: email=${user.email}, password=${user.password}")
log.debug("Token: Bearer $token")
log.info("User CPF: ${user.cpf}")

// ✅ CORRETO — apenas contexto de rastreamento sem PII/credenciais
log.warn("Authentication failed: organizationId={}, path={}", MDC.get("organizationId"), request.uri)
```

---

### 6. Fail Closed — Nunca Fail Open (A10)

```kotlin
// ❌ PROIBIDO — erro vira acesso liberado
try { accessService.check(userId) } catch (e: Exception) { return true }

// ✅ OBRIGATÓRIO — erro implica acesso negado
catch({ accessService.check(userId) }) { e: Exception ->
    log.error("Access check failed — denying", e)
    raise(SecurityError.AccessCheckFailed)
}
```

---

## Padrões Obrigatórios por Módulo

### `http_api/` — Rotas e Plugins

| Requisito | Verificação |
|---|---|
| `authenticate("jwt-auth")` em rotas privadas | Todo `routing { }` que não seja health/metrics |
| `StatusPages` instalado com handler para `Throwable` | `Application.kt` ou plugin dedicado |
| CORS sem `anyHost()` | `install(CORS) { }` deve listar origens explícitas |
| Rate limit em `/auth/*` | `rateLimit(RateLimitName("auth")) { }` |
| `JWT_DEV_MODE` logado como WARNING se `true` | Startup log obrigatório |

### `usecases/` — Casos de Uso

| Requisito | Verificação |
|---|---|
| Verificar `organizationId` no recurso acessado | `ensure(resource.organizationId == callerOrganizationId)` |
| Erros de autorização com `DomainError.Forbidden` | Nunca retornar `null` silencioso |
| Input validado com `ensure()` antes de persistir | Antes de chamar `repository.save()` |

### `domain/` — Domínio

| Requisito | Verificação |
|---|---|
| Invariantes de negócio no agregado | `Board.addStep()` e `Board.addCard()` validam pré-condições (nome único, step existente) |
| Sem dependências de framework ou segredos | `domain/` é 100% puro |

### `sql_persistence/` — Repositórios

| Requisito | Verificação |
|---|---|
| Apenas Exposed DSL — sem raw SQL com interpolação | Grep: `exec\(".*\$` deve ser zero |
| `either {}` + `catch {}` em todo método | Exceções de DB nunca vazam para `usecases/` |
| Sem log de dados sensíveis antes de persistir | Revisar antes de `log.debug` em repositórios |

---

## Detekt Rules de Segurança

O projeto usa Detekt com `warningsAsErrors: true`. A regra `ForbiddenImport` atualmente aplicada:

```yaml
# config/detekt/detekt.yml (arquivo imutável por política — não editar diretamente)
style:
  ForbiddenImport:
    active: true
    imports:
      - value: 'com.kanbanvision.persistence.repositories.Jdbc*'
        reason: 'Repositórios JDBC só podem ser usados em wiring de DI (AppModule)'
```

As entradas abaixo são **recomendadas** para adição futura via ADR — ainda não estão no `detekt.yml`:

```yaml
# Recomendado (abrir ADR para aplicar):
      - value: 'java.io.ObjectInputStream'
        reason: 'Use kotlinx.serialization instead — ObjectInputStream enables RCE via deserialization'
      - value: 'java.security.MessageDigest'
        reason: 'Weak crypto — use SHA-256+ for hashing, Argon2 for passwords'
```

> **Nota**: Para adicionar novas regras Detekt de segurança, abra uma ADR — `config/detekt/detekt.yml` é imutável por política.

---

## Verificações Rápidas em Code Review

```bash
# Segredos hardcoded (deve retornar zero linhas)
grep -rn 'password\s*=\s*"[^"]' --include="*.kt" .

# SQL por interpolação/concatenação (deve retornar zero linhas)
grep -rn -E '(executeQuery|executeUpdate|prepareStatement|exec)\s*\([^)]*"[^"]*(\$\{[^}]+\}|\$[a-zA-Z_])' --include="*.kt" .

# Rotas sem autenticação (revisar manualmente cada resultado)
grep -rn 'routing {' --include="*.kt" . -A 5 | grep -v authenticate

# Dados sensíveis em logs (deve retornar zero linhas)
grep -rn 'log\.\(info\|debug\|warn\|error\).*\(password\|token\|secret\|cpf\|credit\)' --include="*.kt" .

# Imports proibidos de segurança (deve retornar zero linhas)
grep -rn 'import java\.io\.ObjectInputStream\|import java\.security\.MessageDigest' --include="*.kt" .
```

---

## Integração com Definition of Done

A seção **4. Security and Compliance** do DoD exige, para cada entrega:

- [ ] Security scans: Detekt (SAST) + OWASP Dependency Check (SCA) — zero violações.
- [ ] Nenhum segredo em código, histórico git ou logs.
- [ ] Autenticação e autorização verificadas por testes de integração.
- [ ] Runbook de incidente atualizado se a feature tem risco de segurança.

> Use `/definition-of-done` antes de marcar qualquer item como concluído.