package com.kanbanvision.httpapi.plugins

import com.kanbanvision.httpapi.routes.boardRoutes
import com.kanbanvision.httpapi.routes.cardRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            boardRoutes()
            cardRoutes()
        }
    }
}
