package com.kanbanvision.httpapi.plugins

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.ktor.ext.inject

private val PROMETHEUS_CONTENT_TYPE = ContentType.parse("text/plain; version=0.0.4; charset=utf-8")

fun Application.configureMetrics() {
    val registry: PrometheusMeterRegistry by inject()
    install(MicrometerMetrics) {
        this.registry = registry
    }
    routing {
        get("/metrics") {
            call.respondText(registry.scrape(), PROMETHEUS_CONTENT_TYPE)
        }
    }
}
