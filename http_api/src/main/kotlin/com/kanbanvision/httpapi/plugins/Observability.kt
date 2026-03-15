package com.kanbanvision.httpapi.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import org.slf4j.event.Level
import java.util.UUID

val REQUEST_ID_KEY = AttributeKey<String>("RequestId")

private const val REQUEST_ID_HEADER = "X-Request-ID"

val RequestIdPlugin =
    createApplicationPlugin("RequestId") {
        onCall { call ->
            val requestId = call.request.headers[REQUEST_ID_HEADER] ?: UUID.randomUUID().toString()
            call.attributes.put(REQUEST_ID_KEY, requestId)
            call.response.headers.append(REQUEST_ID_HEADER, requestId)
        }
    }

fun Application.configureObservability() {
    install(RequestIdPlugin)
    install(CallLogging) {
        level = Level.INFO
        mdc("requestId") { call -> call.attributes.getOrNull(REQUEST_ID_KEY) ?: "unknown" }
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} → ${call.response.status()}"
        }
    }
}
