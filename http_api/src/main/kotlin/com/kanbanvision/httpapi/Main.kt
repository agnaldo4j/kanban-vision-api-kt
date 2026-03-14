package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.di.AppModule
import com.kanbanvision.httpapi.plugins.configureRouting
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.plugins.configureStatusPages
import com.kanbanvision.persistence.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(Netty, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(AppModule.appModule)
    }

    val dbConfig = environment.config.config("database")
    DatabaseFactory.init(
        url = dbConfig.property("url").getString(),
        driver = dbConfig.property("driver").getString(),
        user = dbConfig.property("user").getString(),
        password = dbConfig.property("password").getString(),
        poolSize = dbConfig.property("poolSize").getString().toInt(),
    )

    configureSerialization()
    configureStatusPages()
    configureRouting()
}
