package com.kanbanvision.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

data class DatabaseConfig(
    val url: String,
    val driver: String,
    val user: String,
    val password: String,
    val poolSize: Int = 10,
    val baselineOnMigrate: Boolean = false,
)

object DatabaseFactory {
    private const val CONNECTION_TIMEOUT_MS = 30_000L
    private const val MAX_LIFETIME_MS = 1_800_000L
    private const val KEEPALIVE_TIME_MS = 120_000L
    private const val LEAK_DETECTION_THRESHOLD_MS = 60_000L
    private const val POOL_NAME = "KanbanVisionPool"

    lateinit var dataSource: HikariDataSource
        private set

    fun init(config: DatabaseConfig) {
        if (::dataSource.isInitialized) {
            dataSource.close()
        }
        dataSource = HikariDataSource(buildHikariConfig(config))
        runMigrations(config.baselineOnMigrate)
    }

    private fun runMigrations(baselineOnMigrate: Boolean) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(baselineOnMigrate)
            .validateOnMigrate(true)
            .load()
            .migrate()
    }

    private fun buildHikariConfig(config: DatabaseConfig): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = config.url
            driverClassName = config.driver
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            connectionTimeout = CONNECTION_TIMEOUT_MS
            maxLifetime = MAX_LIFETIME_MS
            keepaliveTime = KEEPALIVE_TIME_MS
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            leakDetectionThreshold = LEAK_DETECTION_THRESHOLD_MS
            poolName = POOL_NAME
            validate()
        }
}
