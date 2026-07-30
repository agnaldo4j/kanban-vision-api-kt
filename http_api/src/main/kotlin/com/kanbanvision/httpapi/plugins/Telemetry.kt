package com.kanbanvision.httpapi.plugins

import com.kanbanvision.persistence.DatabaseConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

internal const val OTEL_JDBC_URL_PREFIX = "jdbc:otel:"
internal const val OTEL_JDBC_DRIVER = "io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver"

fun Application.configureTelemetry(openTelemetry: OpenTelemetrySdk? = autoConfiguredSdk(resolvedTracesExporter())): OpenTelemetrySdk? {
    if (openTelemetry == null) return null
    install(KtorServerTelemetry) {
        setOpenTelemetry(openTelemetry)
    }
    openTelemetry.installOnJdbcDriverThatIgnoresTheGlobalSdk()
    monitor.subscribe(ApplicationStopped) { openTelemetry.close() }
    return openTelemetry
}

private fun OpenTelemetrySdk.installOnJdbcDriverThatIgnoresTheGlobalSdk() = OpenTelemetryDriver.install(this)

internal fun resolvedTracesExporter(): String? = System.getProperty("otel.traces.exporter") ?: System.getenv("OTEL_TRACES_EXPORTER")

internal fun autoConfiguredSdk(
    tracesExporter: String?,
    setAsGlobal: Boolean = true,
): OpenTelemetrySdk? {
    if (tracesExporter.isNullOrBlank() || tracesExporter.equals("none", ignoreCase = true)) return null
    return AutoConfiguredOpenTelemetrySdk
        .builder()
        .addPropertiesSupplier {
            mapOf(
                "otel.service.name" to "kanban-vision-api",
                "otel.metrics.exporter" to "none",
                "otel.logs.exporter" to "none",
            )
        }.apply { if (setAsGlobal) setResultAsGlobal() }
        .build()
        .openTelemetrySdk
}

internal fun instrumentDatabaseConfig(
    config: DatabaseConfig,
    telemetryEnabled: Boolean,
): DatabaseConfig =
    if (!telemetryEnabled) {
        config
    } else {
        config.copy(
            url = OTEL_JDBC_URL_PREFIX + config.url.removePrefix("jdbc:"),
            driver = OTEL_JDBC_DRIVER,
        )
    }
