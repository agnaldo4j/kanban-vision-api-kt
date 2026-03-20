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

@Suppress("LongMethod")
fun Application.configureOpenApi() {
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
