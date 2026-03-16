package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.routes.boardRoutes
import com.kanbanvision.httpapi.routes.cardRoutes
import com.kanbanvision.httpapi.routes.columnRoutes
import com.kanbanvision.httpapi.routes.healthRoutes
import com.kanbanvision.httpapi.routes.scenarioRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        healthRoutes()
        route("/api/v1") {
            boardRoutes()
            cardRoutes()
            columnRoutes()
            scenarioRoutes()
        }
    }
}
