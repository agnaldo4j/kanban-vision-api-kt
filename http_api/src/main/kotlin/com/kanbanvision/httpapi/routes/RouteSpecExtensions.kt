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

// 403 de tenancy: a simulação não pertence à organização do chamador (GAP-BJ).
internal fun RouteConfig.applyCrossTenantForbiddenResponse() {
    response {
        code(HttpStatusCode.Forbidden) {
            description = "A simulação não pertence à organização do chamador (tenancy do JWT)."
            body<DomainErrorResponse>()
            header<String>("X-Request-ID") { description = "Correlation ID para rastreamento de logs." }
        }
    }
}
