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
            description = "Simulador de quadro Kanban — gerencie boards, colunas e cartões via REST."
        }
        server {
            url = "http://localhost:8080"
            description = "Local"
        }
    }

    routing {
        route("api.json") {
            openApi()
        }
        route("swagger") {
            swaggerUI("/api.json")
        }
    }
}
