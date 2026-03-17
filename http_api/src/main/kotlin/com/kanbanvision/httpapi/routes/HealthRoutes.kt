package com.kanbanvision.httpapi.routes

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

fun Route.healthRoutes(checkDatabase: () -> Boolean) {
    get("/health", healthSpec()) {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
    }
    get("/health/live", healthLiveSpec()) {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
    }
    get("/health/ready", healthReadySpec()) {
        if (checkDatabase()) {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse(status = "unavailable"))
        }
    }
}

private fun healthSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getHealth"
        summary = "Verifica a saúde da aplicação"
        tags("health")
        description = "Mantido para retrocompatibilidade. Use /health/live ou /health/ready."
        response {
            code(HttpStatusCode.OK) {
                description = "Aplicação operacional."
                body<HealthResponse>()
            }
        }
    }

private fun healthLiveSpec(): RouteConfig.() -> Unit =
    {
        operationId = "getHealthLive"
        summary = "Liveness probe"
        tags("health")
        description = "Verifica se o processo está vivo. Retorna 200 enquanto a JVM está rodando."
        response {
            code(HttpStatusCode.OK) {
                description = "Processo vivo."
                body<HealthResponse>()
            }
        }
    }

private fun healthReadySpec(): RouteConfig.() -> Unit =
    {
        operationId = "getHealthReady"
        summary = "Readiness probe"
        tags("health")
        description = "Verifica se a aplicação está pronta para receber tráfego, incluindo conexão com o banco de dados."
        response {
            code(HttpStatusCode.OK) {
                description = "Aplicação pronta para receber tráfego."
                body<HealthResponse>()
            }
            code(HttpStatusCode.ServiceUnavailable) {
                description = "Banco de dados indisponível."
                body<HealthResponse>()
            }
        }
    }

@Serializable
data class HealthResponse(
    val status: String,
)
