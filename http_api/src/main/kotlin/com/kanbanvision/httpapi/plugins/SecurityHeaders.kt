package com.kanbanvision.httpapi.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call

fun Application.configureSecurityHeaders() {
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.append("X-Frame-Options", "DENY")
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Referrer-Policy", "strict-origin-when-cross-origin")
        call.response.headers.append("Content-Security-Policy", "default-src 'self'")
        call.response.headers.append("X-XSS-Protection", "1; mode=block")
    }
}
