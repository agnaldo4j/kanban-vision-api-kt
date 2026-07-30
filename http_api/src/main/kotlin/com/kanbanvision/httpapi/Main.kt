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
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) =
    io.ktor.server.netty.EngineMain
        .main(args)

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(AppModule.koinModule)
    }

    val telemetry = configureTelemetry()

    val migrationsEnabled = System.getenv("FLYWAY_ENABLED")?.lowercase() != "false"
    val meterRegistry: PrometheusMeterRegistry by inject()
    DatabaseFactory.init(
        instrumentDatabaseConfig(buildDatabaseConfig(), telemetryEnabled = telemetry != null),
        migrationsEnabled = migrationsEnabled,
        meterRegistry = meterRegistry,
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
    val poolSize = dbConfig.property("poolSize").getString().toInt()
    val base =
        DatabaseConfig(
            url = dbConfig.property("url").getString(),
            driver = dbConfig.property("driver").getString(),
            user = dbConfig.property("user").getString(),
            password = dbConfig.property("password").getString(),
            poolSize = poolSize,
            minimumIdle =
                dbConfig
                    .propertyOrNull("minimumIdle")
                    ?.getString()
                    ?.takeIf { it.isNotBlank() }
                    ?.toInt() ?: poolSize,
            connectionTimeoutMs = dbConfig.property("connectionTimeoutMs").getString().toLong(),
            maxLifetimeMs = dbConfig.property("maxLifetimeMs").getString().toLong(),
            keepaliveTimeMs = dbConfig.property("keepaliveTimeMs").getString().toLong(),
            leakDetectionThresholdMs = dbConfig.property("leakDetectionThresholdMs").getString().toLong(),
        )
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
