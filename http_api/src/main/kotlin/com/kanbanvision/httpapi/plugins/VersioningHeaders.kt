package com.kanbanvision.httpapi.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call

internal const val API_VERSION_HEADER = "API-Version"
internal const val CURRENT_API_VERSION = "1.0"

/**
 * Sinaliza a versão da API em todas as respostas (ADR-0022 — GAP-AL).
 * Headers de deprecação (`Deprecation`, `Sunset`, `Link successor-version`)
 * serão adicionados aqui quando a v2 existir. Política: docs/api-versioning.md.
 */
fun Application.configureVersioningHeaders() {
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.append(API_VERSION_HEADER, CURRENT_API_VERSION)
    }
}
