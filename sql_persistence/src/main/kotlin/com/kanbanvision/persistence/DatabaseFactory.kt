package com.kanbanvision.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import io.micrometer.core.instrument.MeterRegistry
import org.flywaydb.core.Flyway
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class DatabaseConfig(
    val url: String,
    val driver: String,
    val user: String,
    val password: String,
    // Parâmetros do pool Hikari — todos configuráveis por env (GAP-BX). Defaults = valores
    // históricos, então nenhum ambiente muda de comportamento se as DATABASE_* não forem setadas.
    val poolSize: Int = DEFAULT_POOL_SIZE,
    // minimumIdle default = poolSize ⇒ pool de tamanho FIXO (o comportamento implícito de antes,
    // quando o Hikari igualava minimumIdle ao máximo). Setar < poolSize torna o pool elástico.
    val minimumIdle: Int = poolSize,
    val connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
    val maxLifetimeMs: Long = DEFAULT_MAX_LIFETIME_MS,
    val keepaliveTimeMs: Long = DEFAULT_KEEPALIVE_TIME_MS,
    val leakDetectionThresholdMs: Long = DEFAULT_LEAK_DETECTION_THRESHOLD_MS,
    val baselineOnMigrate: Boolean = false,
    // Native Image (ADR-0032): o ClassPathScanner do Flyway não suporta o protocolo
    // "resource" do binário — a imagem nativa usa "filesystem:/app/db/migration"
    // (migrations copiadas como arquivos). Na JVM o default classpath permanece.
    val migrationsLocation: String = "classpath:db/migration",
) {
    companion object {
        const val DEFAULT_POOL_SIZE = 10
        const val DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L
        const val DEFAULT_MAX_LIFETIME_MS = 1_800_000L
        const val DEFAULT_KEEPALIVE_TIME_MS = 120_000L
        const val DEFAULT_LEAK_DETECTION_THRESHOLD_MS = 60_000L
    }
}

object DatabaseFactory {
    // Timeouts do pool migraram para DatabaseConfig (campos com default) — GAP-BX os tornou
    // configuráveis por env. POOL_NAME (identidade da métrica) e os de readiness ficam fixos.
    private const val POOL_NAME = "KanbanVisionPool"
    private const val READINESS_VALIDATION_TIMEOUT_SECS = 2
    private const val READINESS_CHECK_TIMEOUT_MS = 3_000L

    lateinit var dataSource: HikariDataSource
        private set

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    fun isReady(): Boolean {
        if (Thread.currentThread().isInterrupted) return false

        val check =
            CompletableFuture.supplyAsync {
                try {
                    dataSource.connection.use { it.isValid(READINESS_VALIDATION_TIMEOUT_SECS) }
                } catch (e: Exception) {
                    false
                }
            }
        return try {
            check.get(READINESS_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            check.cancel(true)
            Thread.currentThread().interrupt()
            false
        } catch (_: TimeoutException) {
            check.cancel(true)
            false
        } catch (_: Exception) {
            check.cancel(true)
            false
        }
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    /**
     * @param meterRegistry quando presente, o pool publica `hikaricp_*` nele. O binding vive AQUI, no
     * nascimento do pool, e não num `bindMetrics()` posterior: pool e suas métricas têm o mesmo ciclo
     * de vida, então é impossível criar um sem o outro. O alerta `HikariPoolExhaustion` existia desde
     * o GAP-U consultando `hikaricp_connections_active`, que NUNCA foi exposto — a métrica nunca foi
     * bindada e o alerta nunca pôde disparar (GAP-BW). Um `bindMetrics()` opcional reabriria essa
     * porta. Null só nos casos sem Micrometer (Job de migração, testes de unidade).
     */
    fun init(
        config: DatabaseConfig,
        migrationsEnabled: Boolean = true,
        meterRegistry: MeterRegistry? = null,
    ) {
        if (::dataSource.isInitialized) {
            dataSource.close()
        }
        dataSource = HikariDataSource(buildHikariConfig(config, meterRegistry))
        DbCircuitBreaker.reset()
        org.jetbrains.exposed.v1.jdbc.Database
            .connect(datasource = CircuitBreakerDataSource(dataSource, DbCircuitBreaker.circuitBreaker))
        if (migrationsEnabled) runMigrations(config)
    }

    private fun runMigrations(config: DatabaseConfig) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations(config.migrationsLocation)
            .baselineOnMigrate(config.baselineOnMigrate)
            .validateOnMigrate(true)
            .load()
            .migrate()
    }

    private fun buildHikariConfig(
        config: DatabaseConfig,
        meterRegistry: MeterRegistry?,
    ): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = config.url
            driverClassName = config.driver
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            minimumIdle = config.minimumIdle
            connectionTimeout = config.connectionTimeoutMs
            maxLifetime = config.maxLifetimeMs
            keepaliveTime = config.keepaliveTimeMs
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            leakDetectionThreshold = config.leakDetectionThresholdMs
            poolName = POOL_NAME
            // Antes do HikariDataSource ser construído: o tracker precisa existir quando as
            // primeiras conexões nascem, senão os contadores começam incompletos.
            meterRegistry?.let { metricsTrackerFactory = MicrometerMetricsTrackerFactory(it) }
            validate()
        }
}
