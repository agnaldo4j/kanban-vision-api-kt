package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.di.AppModule
import com.kanbanvision.httpapi.plugins.configureAuthentication
import com.kanbanvision.httpapi.plugins.configureCors
import com.kanbanvision.httpapi.plugins.configureMetrics
import com.kanbanvision.httpapi.plugins.configureObservability
import com.kanbanvision.httpapi.plugins.configureOpenApi
import com.kanbanvision.httpapi.plugins.configureRateLimit
import com.kanbanvision.httpapi.plugins.configureRequestLimits
import com.kanbanvision.httpapi.plugins.configureRouting
import com.kanbanvision.httpapi.plugins.configureSecurityHeaders
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.plugins.configureStatusPages
import com.kanbanvision.httpapi.plugins.configureTelemetry
import com.kanbanvision.httpapi.plugins.configureVersioningHeaders
import com.kanbanvision.httpapi.plugins.instrumentDatabaseConfig
import com.kanbanvision.httpapi.routes.authRoutes
import com.kanbanvision.persistence.DatabaseConfig
import com.kanbanvision.persistence.DatabaseFactory
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

// EngineMain carrega o application.conf do classpath (database.*, jwt.*,
// ktor.application.modules) e faz graceful shutdown via ktor.deployment.shutdown* —
// o embeddedServer programático usado antes NÃO lia o conf e quebrava o fat JAR em
// runtime ("Property database.url not found"; descoberto no baseline do GAP-AR).
fun main(args: Array<String>) =
    io.ktor.server.netty.EngineMain
        .main(args)

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(AppModule.koinModule)
    }

    // Telemetria ANTES do pool: o driver JDBC OTel e o withSpan dependem do
    // GlobalOpenTelemetry já registrado quando a primeira conexão é criada (ADR-0031).
    val telemetry = configureTelemetry()

    val migrationsEnabled = System.getenv("FLYWAY_ENABLED")?.lowercase() != "false"
    DatabaseFactory.init(
        instrumentDatabaseConfig(buildDatabaseConfig(), telemetryEnabled = telemetry != null),
        migrationsEnabled = migrationsEnabled,
    )

    configureMetrics()
    configureObservability()
    configureOpenApi()
    configureSerialization()
    configureStatusPages()
    configureSecurityHeaders()
    configureVersioningHeaders()
    configureRequestLimits()
    configureRateLimit()
    configureCors()
    configureAuthentication()
    configureRouting()
    configureDevAuthRoutes()
}

private fun Application.buildDatabaseConfig(): DatabaseConfig {
    val dbConfig = environment.config.config("database")
    val base =
        DatabaseConfig(
            url = dbConfig.property("url").getString(),
            driver = dbConfig.property("driver").getString(),
            user = dbConfig.property("user").getString(),
            password = dbConfig.property("password").getString(),
            poolSize = dbConfig.property("poolSize").getString().toInt(),
        )
    // Native Image (ADR-0032): a imagem nativa seta FLYWAY_LOCATIONS=filesystem:/app/db/migration
    // porque o ClassPathScanner do Flyway não lê resources do binário; JVM usa o default.
    val locations = System.getenv("FLYWAY_LOCATIONS")
    return if (locations.isNullOrBlank()) base else base.copy(migrationsLocation = locations)
}

private fun Application.configureDevAuthRoutes() {
    val devMode = System.getenv("JWT_DEV_MODE")?.lowercase() == "true"
    if (!devMode) return
    val jwtConfig = environment.config.config("jwt")
    routing {
        authRoutes(
            secret = jwtConfig.property("secret").getString(),
            issuer = jwtConfig.property("issuer").getString(),
            audience = jwtConfig.property("audience").getString(),
            ttlMs = jwtConfig.property("ttlMs").getString().toLong(),
        )
    }
}
