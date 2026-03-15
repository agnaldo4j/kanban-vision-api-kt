package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.routes.boardRoutes
import com.kanbanvision.httpapi.routes.cardRoutes
import com.kanbanvision.httpapi.routes.columnRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            boardRoutes()
            cardRoutes()
            columnRoutes()
        }
    }
}
