package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.routes.healthRoutes
import com.kanbanvision.httpapi.routes.simulationRoutes
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.DbCircuitBreaker
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        healthRoutes(::isDatabaseReady)
        authenticate("jwt-auth") {
            route("/api/v1") {
                simulationRoutes()
            }
        }
    }
}

// Short-circuit: com o circuito aberto não se tenta conexão — o probe não gera side effects no breaker.
private fun isDatabaseReady(): Boolean = !DbCircuitBreaker.isOpen() && DatabaseFactory.isReady()
