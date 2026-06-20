package com.kanbanvision.httpapi.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path

internal val DOC_PATHS = listOf("/swagger", "/api.json")

fun Application.configureSecurityHeaders() {
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        call.response.headers.append("X-Frame-Options", "DENY")
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Referrer-Policy", "strict-origin-when-cross-origin")
        // X-XSS-Protection: 0 disables the legacy XSS auditor; modern browsers ignore it, but
        // explicit 0 prevents IE/older proxies from enabling intrusive filtering behavior.
        call.response.headers.append("X-XSS-Protection", "0")
        // CSP is omitted for Swagger UI paths (/swagger, /api.json) which may serve
        // bundled assets that require relaxed directives. All API endpoints get the strict policy.
        if (DOC_PATHS.none { path.startsWith(it) }) {
            call.response.headers.append("Content-Security-Policy", "default-src 'self'")
        }
    }
}
