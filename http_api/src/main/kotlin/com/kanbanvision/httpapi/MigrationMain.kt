package com.kanbanvision.httpapi

import com.kanbanvision.persistence.DatabaseConfig
import com.kanbanvision.persistence.DatabaseFactory

private const val MIGRATION_POOL_SIZE = 1
private const val POSTGRES_DRIVER = "org.postgresql.Driver"

fun main() {
    DatabaseFactory.init(
        DatabaseConfig(
            url = requireNotNull(System.getenv("DATABASE_URL")) { "DATABASE_URL is required" },
            driver = POSTGRES_DRIVER,
            user = requireNotNull(System.getenv("DATABASE_USER")) { "DATABASE_USER is required" },
            password = requireNotNull(System.getenv("DATABASE_PASSWORD")) { "DATABASE_PASSWORD is required" },
            poolSize = MIGRATION_POOL_SIZE,
        ),
        migrationsEnabled = true,
    )
    DatabaseFactory.close()
}
