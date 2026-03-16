package com.kanbanvision.httpapi.plugins

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureOpenApi() {
    install(OpenApi) {
        info {
            title = "Kanban Vision API"
            version = "1.0.0"
            summary = "API REST para simulação e gerenciamento de quadros Kanban"
            description = "Simulador de quadro Kanban — gerencie boards, colunas e cartões via REST."
            contact {
                name = "Kanban Vision"
            }
        }
        tags {
            tag("boards") { description = "Gerenciamento de quadros Kanban" }
            tag("columns") { description = "Colunas dentro de um quadro" }
            tag("cards") { description = "Cartões e movimentações" }
            tag("scenarios") { description = "Motor de simulação — criação e execução de cenários" }
            tag("health") { description = "Liveness e readiness da aplicação" }
        }
    }

    routing {
        route("/api.json") {
            openApi()
        }
        route("/swagger") {
            swaggerUI("/api.json")
        }
    }
}
