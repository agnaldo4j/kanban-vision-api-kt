package com.kanbanvision.httpapi.routes

import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun RouteConfig.applyBearerAuthSecurity() {
    securitySchemeNames("BearerAuth")
    response {
        code(HttpStatusCode.Unauthorized) {
            description = "Token JWT ausente, inválido ou expirado."
            body<DomainErrorResponse>()
        }
    }
}
