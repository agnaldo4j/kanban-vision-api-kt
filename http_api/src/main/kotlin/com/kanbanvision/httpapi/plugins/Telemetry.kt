package com.kanbanvision.httpapi.plugins

import com.kanbanvision.persistence.DatabaseConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

/**
 * Traces em build time — ADR-0031 (supersede parcialmente a ADR-0009).
 *
 * Substitui o OTel Java Agent: o SDK é montado via autoconfigure (lê as mesmas envs
 * `OTEL_*` que o agente lia) e a instrumentação HTTP vem do plugin de biblioteca
 * [KtorServerTelemetry]. Quando `OTEL_TRACES_EXPORTER` está ausente ou `none`
 * (default do ConfigMap k8s), nada é instalado e o custo é zero — mesmo
 * comportamento de rodar sem o agente.
 */
internal const val OTEL_JDBC_URL_PREFIX = "jdbc:otel:"
internal const val OTEL_JDBC_DRIVER = "io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver"

fun Application.configureTelemetry(
    openTelemetry: OpenTelemetrySdk? = autoConfiguredSdk(System.getenv("OTEL_TRACES_EXPORTER")),
): OpenTelemetrySdk? {
    if (openTelemetry == null) return null
    install(KtorServerTelemetry) {
        setOpenTelemetry(openTelemetry)
    }
    // O OpenTelemetryDriver NÃO lê o GlobalOpenTelemetry — nasce com noop() e exige
    // install() na instância registrada no DriverManager ANTES do pool ser criado
    // (o Hikari resolve o driver registrado por nome de classe, não cria outro).
    OpenTelemetryDriver.install(openTelemetry)
    monitor.subscribe(ApplicationStopped) { openTelemetry.close() }
    return openTelemetry
}

/**
 * Monta o SDK via autoconfigure quando há exporter de traces configurado; `null` desliga.
 *
 * [setAsGlobal] registra o SDK no `GlobalOpenTelemetry` — necessário em produção para o
 * [withSpan] e para o driver JDBC OTel, mas o registro global é write-once por JVM:
 * testes devem passar `false`.
 */
internal fun autoConfiguredSdk(
    tracesExporter: String?,
    setAsGlobal: Boolean = true,
): OpenTelemetrySdk? {
    if (tracesExporter.isNullOrBlank() || tracesExporter.equals("none", ignoreCase = true)) return null
    return AutoConfiguredOpenTelemetrySdk
        .builder()
        .addPropertiesSupplier {
            // Defaults sobrescrevíveis por env/system property: só traces saem pelo OTLP;
            // métricas continuam Micrometer/Prometheus e logs continuam SLF4J (ADR-0009).
            mapOf(
                "otel.service.name" to "kanban-vision-api",
                "otel.metrics.exporter" to "none",
                "otel.logs.exporter" to "none",
            )
        }.apply { if (setAsGlobal) setResultAsGlobal() }
        .build()
        .openTelemetrySdk
}

/**
 * Roteia o JDBC pelo driver OTel ([OTEL_JDBC_DRIVER]) quando a telemetria está ativa —
 * spans de query sem nenhuma dependência OTel em `sql_persistence/`: o wrap acontece por
 * configuração (URL `jdbc:otel:` + driver delegante), e o driver real segue no classpath.
 */
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
