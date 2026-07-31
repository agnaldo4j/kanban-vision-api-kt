package com.kanbanvision.persistence

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cada `config.setXxx(...)` de `buildHikariConfig` tinha um mutante `VoidMethodCallMutator` que
 * nenhuma asserção matava: o que os derrubava era um teste de integração lento estourar o orçamento
 * do PITest — kill por CRONÔMETRO, não por asserção. Medido duas vezes (#391 e #394), o mesmo
 * conjunto migrou `KILLED → SURVIVED` sozinho, por variação de tempo.
 *
 * Um mapeamento sem asserção também é um buraco de produto: apagar `keepaliveTime` ou
 * `leakDetectionThreshold` do pool não quebraria teste algum, e as envs do GAP-BX viraram
 * configuração que ninguém confere.
 */
class HikariConfigMappingTest {
    private val config =
        DatabaseConfig(
            url = "jdbc:postgresql://localhost:5432/kanban",
            driver = "org.postgresql.Driver",
            user = "kanban",
            password = "secret-from-env",
            poolSize = 17,
            minimumIdle = 5,
            connectionTimeoutMs = 4_321L,
            maxLifetimeMs = 654_321L,
            keepaliveTimeMs = 60_000L,
            leakDetectionThresholdMs = 12_345L,
        )

    @Test
    fun `given a database config when building the hikari config then every field is mapped`() {
        val hikari = DatabaseFactory.buildHikariConfig(config, meterRegistry = null)

        assertEquals(config.url, hikari.jdbcUrl)
        assertEquals(config.driver, hikari.driverClassName)
        assertEquals(config.user, hikari.username)
        assertEquals(config.password, hikari.password)
        assertEquals(config.poolSize, hikari.maximumPoolSize)
        assertEquals(config.minimumIdle, hikari.minimumIdle)
        assertEquals(config.connectionTimeoutMs, hikari.connectionTimeout)
        assertEquals(config.maxLifetimeMs, hikari.maxLifetime)
        assertEquals(config.keepaliveTimeMs, hikari.keepaliveTime)
        assertEquals(config.leakDetectionThresholdMs, hikari.leakDetectionThreshold)
    }

    @Test
    fun `given a database config when building the hikari config then the transactional invariants hold`() {
        val hikari = DatabaseFactory.buildHikariConfig(config, meterRegistry = null)

        assertFalse(hikari.isAutoCommit, "o repositório controla a transação; auto-commit quebraria dbQuery")
        assertEquals("TRANSACTION_REPEATABLE_READ", hikari.transactionIsolation)
        assertEquals("KanbanVisionPool", hikari.poolName, "o poolName é a identidade da métrica hikaricp_*")
    }

    @Test
    fun `given a meter registry when building the hikari config then the metrics tracker is bound at pool birth`() {
        // GAP-BW: o alerta HikariPoolExhaustion consultava `hikaricp_connections_active`, que NUNCA foi
        // exposto porque ninguém bindava a métrica. O binding nasce com o pool — sem factory, sem métrica.
        val comRegistro = DatabaseFactory.buildHikariConfig(config, meterRegistry = SimpleMeterRegistry())
        val semRegistro = DatabaseFactory.buildHikariConfig(config, meterRegistry = null)

        assertNotNull(comRegistro.metricsTrackerFactory)
        assertNull(semRegistro.metricsTrackerFactory)
    }
}
