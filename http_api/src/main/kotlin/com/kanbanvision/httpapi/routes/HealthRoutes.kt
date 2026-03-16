package com.kanbanvision.httpapi.routes

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

fun Route.healthRoutes() {
    get("/health", healthSpec()) {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
    }
}

private fun healthSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getHealth"
        summary = "Verifica a saúde da aplicação (liveness)"
        tags("health")
        description = "Verifica se a aplicação está em execução (liveness probe)."
        response {
            code(HttpStatusCode.OK) {
                description = "Aplicação operacional. O campo `status` retorna \"ok\"."
                body<HealthResponse>()
            }
        }
    }

@Serializable
data class HealthResponse(
    val status: String,
)
