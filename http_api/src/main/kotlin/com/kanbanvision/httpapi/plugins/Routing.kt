package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.routes.boardRoutes
import com.kanbanvision.httpapi.routes.cardRoutes
import com.kanbanvision.httpapi.routes.columnRoutes
import com.kanbanvision.httpapi.routes.healthRoutes
import com.kanbanvision.httpapi.routes.scenarioAnalyticsRoutes
import com.kanbanvision.httpapi.routes.scenarioRoutes
import com.kanbanvision.httpapi.routes.stepRoutes
import com.kanbanvision.persistence.DatabaseFactory
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        healthRoutes(::isDatabaseReady)
        authenticate("jwt-auth") {
            route("/api/v1") {
                boardRoutes()
                cardRoutes()
                stepRoutes()
                columnRoutes()
                scenarioRoutes()
                scenarioAnalyticsRoutes()
            }
        }
    }
}

private fun isDatabaseReady(): Boolean = DatabaseFactory.isReady()
