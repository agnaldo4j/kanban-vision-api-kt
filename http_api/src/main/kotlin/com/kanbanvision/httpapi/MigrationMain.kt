package com.kanbanvision.httpapi

import com.kanbanvision.persistence.DatabaseConfig
import com.kanbanvision.persistence.DatabaseFactory

// Flyway 12 usa DUAS conexões simultâneas (a principal do executor + a de migração);
// pool de 1 estourava SQLTransientConnectionException após 30s (bug latente descoberto
// no GAP-BB — afetava o Job k8s também na JVM).
private const val MIGRATION_POOL_SIZE = 2
private const val POSTGRES_DRIVER = "org.postgresql.Driver"

fun main() {
    val base =
        DatabaseConfig(
            url = requireNotNull(System.getenv("DATABASE_URL")) { "DATABASE_URL is required" },
            driver = POSTGRES_DRIVER,
            user = requireNotNull(System.getenv("DATABASE_USER")) { "DATABASE_USER is required" },
            password = requireNotNull(System.getenv("DATABASE_PASSWORD")) { "DATABASE_PASSWORD is required" },
            poolSize = MIGRATION_POOL_SIZE,
        )
    // Native Image (ADR-0032): mesmo mecanismo do Main — o binário de migração recebe
    // FLYWAY_LOCATIONS=filesystem:/app/db/migration da imagem.
    val locations = System.getenv("FLYWAY_LOCATIONS")
    DatabaseFactory.init(
        if (locations.isNullOrBlank()) base else base.copy(migrationsLocation = locations),
        migrationsEnabled = true,
    )
    DatabaseFactory.close()
}
