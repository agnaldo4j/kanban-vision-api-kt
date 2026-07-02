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
import com.kanbanvision.httpapi.plugins.configureVersioningHeaders
import com.kanbanvision.httpapi.routes.authRoutes
import com.kanbanvision.persistence.DatabaseConfig
import com.kanbanvision.persistence.DatabaseFactory
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

private const val SHUTDOWN_GRACE_PERIOD_MS = 1_000L
private const val SHUTDOWN_TIMEOUT_MS = 5_000L

fun main() {
    val server = embeddedServer(Netty, module = Application::module)
    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(
                gracePeriodMillis = SHUTDOWN_GRACE_PERIOD_MS,
                timeoutMillis = SHUTDOWN_TIMEOUT_MS,
            )
        },
    )
    server.start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(AppModule.koinModule)
    }

    val migrationsEnabled = System.getenv("FLYWAY_ENABLED")?.lowercase() != "false"
    DatabaseFactory.init(buildDatabaseConfig(), migrationsEnabled = migrationsEnabled)

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
    return DatabaseConfig(
        url = dbConfig.property("url").getString(),
        driver = dbConfig.property("driver").getString(),
        user = dbConfig.property("user").getString(),
        password = dbConfig.property("password").getString(),
        poolSize = dbConfig.property("poolSize").getString().toInt(),
    )
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
