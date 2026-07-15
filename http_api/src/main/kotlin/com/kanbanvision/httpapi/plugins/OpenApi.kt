package com.kanbanvision.httpapi.plugins

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("OpenApi")

/**
 * Reads the `ENABLE_SWAGGER` flag. Default-off, mirroring `JWT_DEV_MODE`: the OpenAPI spec and
 * Swagger UI are exposed only when explicitly enabled (`security.md` §3). The `env` seam keeps this
 * unit-testable without touching real environment variables (same idiom as `Cors.loadCorsOrigins`).
 */
internal fun swaggerEnabled(env: (String) -> String? = System::getenv): Boolean = env("ENABLE_SWAGGER")?.lowercase() == "true"

// LongMethod: the OpenAPI info/tags/security block is a flat declarative config, not branching logic.
@Suppress("LongMethod")
fun Application.configureOpenApi(enabled: Boolean = swaggerEnabled()) {
    install(OpenApi) {
        info {
            title = "Kanban Vision API"
            version = "1.0.0"
            summary = "API REST para simulação e gerenciamento de quadros Kanban"
            description = "Simulador de quadro Kanban — gerencie boards, steps e cards via REST."
            contact {
                name = "Kanban Vision"
            }
        }
        server {
            url = "/"
            description = "Servidor atual — rotas de negócio versionadas sob /api/v1 (ADR-0022; política em docs/api-versioning.md)."
        }
        tags {
            tag("boards") { description = "Gerenciamento de quadros Kanban" }
            tag("steps") { description = "Etapas dentro de um quadro" }
            tag("cards") { description = "Cartões e movimentações" }
            tag("simulations") { description = "Motor de simulação — criação e execução de simulações" }
            tag("health") { description = "Liveness e readiness da aplicação" }
            tag("auth") { description = "Emissão de tokens (somente ambiente de desenvolvimento)" }
        }
        security {
            securityScheme("BearerAuth") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
            }
        }
    }
    if (!enabled) {
        log.info("ENABLE_SWAGGER not 'true' — /api.json and /swagger disabled")
        return
    }
    log.warn("Swagger UI enabled at /swagger (ENABLE_SWAGGER=true) — do not enable in production")
    configureOpenApiRoutes()
}

private fun Application.configureOpenApiRoutes() {
    routing {
        route("/api.json") {
            openApi()
        }
        route("/swagger") {
            swaggerUI("/api.json")
        }
    }
}
