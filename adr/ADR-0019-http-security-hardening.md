# ADR-0019 — HTTP Security Hardening: CORS, Payload Limit e Security Headers

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

Auditoria de qualidade de Jun/2026 revelou que a dimensão **Prontidão para Produção** está em
**8.5/10** — abaixo da meta de 9.0. Três lacunas de segurança HTTP permanecem após a conclusão
de P1–P4:

1. **Sem CORS configurado:** qualquer origem pode fazer requisições ao servidor. Um cliente web
   legítimo (SPA React, Angular) pode ser bloqueado por CORS inconsistente; um atacante pode
   fazer chamadas cross-origin sem restrição. O padrão seguro é lista explícita de origens.

2. **Sem payload size limit:** Ktor/Netty aceita bodies de tamanho arbitrário por padrão.
   Um payload de 100MB enviado concorrentemente pode saturar memória e derrubar o serviço —
   DoS acidental (ou intencional) sem custo para o atacante.

3. **Sem security headers:** browsers modernos respeitam headers como `X-Frame-Options` (evita
   clickjacking), `Content-Security-Policy` (evita XSS inline), `X-Content-Type-Options`
   (evita MIME sniffing). Sem eles, o OWASP A05 (Security Misconfiguration) não é endereçado
   completamente.

Todos os três gaps são do tipo `[N]` Normativo — adicionam proteção sem alterar contratos ou
lógica de negócio. Podem ser executados numa única sessão LLM como um plugin de segurança HTTP.

---

## Gaps Cobertos

| GAP | Título | Tipo |
|-----|--------|------|
| GAP-AG | CORS plugin Ktor | N |
| GAP-AH | Payload size limit | N |
| GAP-AI | Security headers (CSP, X-Frame-Options) | N |

---

## Decisões

### GAP-AG — CORS

**Decisão:** instalar `io.ktor:ktor-server-cors` e configurar origens via variável de ambiente.

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/Cors.kt
fun Application.configureCors() {
    val allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS")
        ?.split(",")
        ?.map { it.trim() }
        ?: emptyList()

    install(CORS) {
        allowedOrigins.forEach { allowHost(it) }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
}
```

**Razões:**
- `anyHost()` é explicitamente proibido pela regra security.md (OWASP A05)
- Origens via env var permite configuração diferente por ambiente (dev vs prod) sem recompilação
- Se `CORS_ALLOWED_ORIGINS` não estiver definida, nenhuma origem cross-origin é permitida (fail closed)

**Alternativa descartada:** lista hardcoded de origens — viola o princípio de não ter configuração de ambiente no código.

### GAP-AH — Payload Size Limit

**Decisão:** configurar `maxContentLength` no plugin do engine Netty ou via `receiveParameters`.

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/RequestLimits.kt
fun Application.configureRequestLimits() {
    val maxBodySize = System.getenv("MAX_REQUEST_BODY_SIZE")
        ?.toLongOrNull()
        ?: (1 * 1024 * 1024L) // 1MB default

    install(RequestBodyLimit) {
        maxBodySize { maxBodySize }
    }
}
```

Se `RequestBodyLimit` não estiver disponível no Ktor 3.5, usar configuração no `engineConfig`:

```kotlin
// Main.kt — dentro do bloco embeddedServer
engineConfig {
    connectionGroupSize = 2
    workerGroupSize = 5
    callGroupSize = 10
    maxInitialLineLength = 2048
    maxHeaderSize = 8192
    maxChunkSize = 1 * 1024 * 1024 // 1MB
}
```

**Razões:**
- Default 1MB é suficiente para payloads JSON da API (nenhum endpoint aceita uploads de arquivo)
- Configurável via env var para testes de carga e ambientes específicos
- Retorna `413 Payload Too Large` via `StatusPages` (já instalado)

### GAP-AI — Security Headers

**Decisão:** novo plugin `SecurityHeaders.kt` que intercepta todas as respostas.

```kotlin
// http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/SecurityHeaders.kt
fun Application.configureSecurityHeaders() {
    intercept(ApplicationCallPipeline.Call) {
        proceed()
        with(call.response.headers) {
            append("X-Frame-Options", "DENY")
            append("X-Content-Type-Options", "nosniff")
            append("Referrer-Policy", "strict-origin-when-cross-origin")
            append("Content-Security-Policy", "default-src 'self'")
            append("X-XSS-Protection", "1; mode=block")
        }
    }
}
```

**Razões:**
- `X-Frame-Options: DENY` — previne clickjacking (OWASP A05)
- `X-Content-Type-Options: nosniff` — previne MIME sniffing (OWASP A05)
- `Referrer-Policy` — limita vazamento de URL nos headers de referência
- `Content-Security-Policy: default-src 'self'` — baseline restritiva, sem risco de quebrar API JSON
- `X-XSS-Protection` — legacy browsers; browsers modernos ignoram mas não prejudica

**Alternativa descartada:** Ktor `ResponseHeaders` plugin (não existe nativamente; o interceptor é a forma idiomática).

---

## Plano de Implementação

**1 sessão LLM — 1 PR:**

1. Criar `http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/Cors.kt`
2. Criar `http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/RequestLimits.kt`
3. Criar `http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/SecurityHeaders.kt`
4. Instalar os 3 plugins em `Application.kt` (função `configurePlugins()`)
5. Adicionar `CORS_ALLOWED_ORIGINS` e `MAX_REQUEST_BODY_SIZE` ao `docker-compose.yml` (dev: `http://localhost:3000`) e ao K8s ConfigMap (`k8s/02-configmap.yml`)
6. Testes: `testApplication { }` verificando headers de resposta e rejeição de origem não permitida
7. Verificar JaCoCo ≥ 97% e Detekt 0

**Arquivos modificados:**
- `http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/Cors.kt` (criar)
- `http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/RequestLimits.kt` (criar)
- `http_api/src/main/kotlin/com/kanbanvision/httpapi/plugins/SecurityHeaders.kt` (criar)
- `http_api/src/main/kotlin/com/kanbanvision/httpapi/Application.kt` (instalar plugins)
- `docker-compose.yml` (env vars CORS)
- `k8s/02-configmap.yml` (env vars CORS + MAX_REQUEST_BODY_SIZE)

---

## Consequências

**Positivas:**
- Prontidão para Produção sobe de 8.5 → 9.0
- OWASP A05 (Security Misconfiguration) completamente endereçado
- Proteção contra clickjacking, MIME sniffing e payload DoS sem alteração de lógica de negócio
- Configuração de origens CORS por ambiente via env var — deploy-friendly

**Negativas:**
- `Content-Security-Policy: default-src 'self'` pode precisar de ajuste se Swagger UI carregar
  recursos externos (fontes, scripts CDN). Verificar no Swagger UI após implementação.

**Neutras:**
- Nenhuma alteração em contratos de API, DTOs ou lógica de domínio

---

## Referências

- OWASP Top 10 (2025) — A05 Security Misconfiguration
- Ktor CORS: https://ktor.io/docs/server-cors.html
- MDN Security Headers: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers
- Skill: [owasp](.claude/skills/owasp/SKILL.md)
- Skill: [kotlin-quality-pipeline](.claude/skills/kotlin-quality-pipeline/SKILL.md)
