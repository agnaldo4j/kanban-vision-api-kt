package com.kanbanvision.persistence.support

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.PolicySet
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioId
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.domain.model.simulation.SimulationStatus
import com.kanbanvision.persistence.DatabaseConfig
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.internal.tables.OrganizationsTable
import com.kanbanvision.persistence.internal.tables.SimulationsTable
import io.micrometer.core.instrument.MeterRegistry
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal object EmbeddedPostgresSupport {
    data class SimulationSeed(
        val id: String,
        val organizationId: String,
        val wipLimit: Int = 2,
        val teamSize: Int = 2,
        val seedValue: Long = 10L,
    )

    private var started = false
    private lateinit var postgres: EmbeddedPostgres

    fun ensureStarted() {
        if (started) return
        postgres = EmbeddedPostgres.start()
        DatabaseFactory.init(databaseConfig())
        started = true
    }

    fun refreshDataSource() {
        ensureStarted()
        DatabaseFactory.init(databaseConfig())
    }

    /** Recria o pool publicando `hikaricp_*` no registry — usado para provar o binding (GAP-BW). */
    fun refreshDataSourceWithMetrics(meterRegistry: MeterRegistry) {
        ensureStarted()
        DatabaseFactory.init(databaseConfig(), meterRegistry = meterRegistry)
    }

    fun resetDatabase() {
        transaction {
            exec(
                """
                TRUNCATE TABLE daily_snapshots, simulation_states, simulations, cards, steps, boards, organizations
                RESTART IDENTITY CASCADE
                """.trimIndent(),
            )
        }
    }

    private fun databaseConfig() =
        DatabaseConfig(
            url = postgres.getJdbcUrl("postgres", "postgres"),
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = "postgres",
            poolSize = 4,
        )

    fun insertOrganization(
        id: String,
        name: String = "Org",
    ) {
        transaction {
            OrganizationsTable.insert {
                it[OrganizationsTable.id] = id
                it[OrganizationsTable.name] = name
            }
        }
    }

    fun insertSimulationRow(seed: SimulationSeed) {
        transaction {
            SimulationsTable.insert {
                it[SimulationsTable.id] = seed.id
                it[organizationId] = seed.organizationId
                it[wipLimit] = seed.wipLimit
                it[teamSize] = seed.teamSize
                it[seedValue] = seed.seedValue
            }
        }
    }
}

internal object PersistenceFixtures {
    fun simulation(
        simulationId: String = "10000000-0000-0000-0000-000000000001",
        organizationId: String = "20000000-0000-0000-0000-000000000001",
    ): Simulation {
        val worker = sampleWorker()
        val scenario = sampleScenario(worker)
        val organization = sampleOrganization(organizationId, worker)
        return Simulation(
            id = SimulationId(simulationId),
            name = "Simulation 1",
            currentDay = SimulationDay(2),
            status = SimulationStatus.RUNNING,
            organization = organization,
            scenario = scenario,
            decisions = listOf(scenarioDecision()),
            history = listOf(snapshot(simulationId = simulationId)),
        )
    }

    private fun sampleWorker(): Worker {
        val developerAbility =
            Ability(id = "30000000-0000-0000-0000-000000000001", name = AbilityName.DEVELOPER, seniority = Seniority.PL)
        val testerAbility =
            Ability(id = "30000000-0000-0000-0000-000000000002", name = AbilityName.TESTER, seniority = Seniority.SR)
        val deployerAbility =
            Ability(id = "30000000-0000-0000-0000-000000000003", name = AbilityName.DEPLOYER, seniority = Seniority.SR)
        return Worker(
            id = "40000000-0000-0000-0000-000000000001",
            name = "worker-1",
            abilities = setOf(developerAbility, testerAbility, deployerAbility),
        )
    }

    private fun sampleScenario(worker: Worker): Scenario {
        val board = scenarioBoard(worker)
        val scenarioRules = scenarioRules()
        return Scenario(
            id = ScenarioId("a0000000-0000-0000-0000-000000000001"),
            name = "Scenario 1",
            rules = scenarioRules,
            board = board,
        )
    }

    private fun scenarioBoard(worker: Worker): Board {
        val step =
            Step(
                id = StepId("50000000-0000-0000-0000-000000000001"),
                board = BoardId("60000000-0000-0000-0000-000000000001"),
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
                cards = listOf(card()),
                workers = listOf(worker),
            )
        return Board(id = BoardId("60000000-0000-0000-0000-000000000001"), name = "Main Board", steps = listOf(step))
    }

    private fun scenarioRules() =
        ScenarioRules(
            id = "70000000-0000-0000-0000-000000000001",
            policySet = PolicySet(id = "80000000-0000-0000-0000-000000000001", wipLimit = 2),
            wipLimit = 2,
            teamSize = 2,
            seedValue = 42L,
        )

    private fun scenarioDecision() = Decision.MoveItem(cardId = CardId("c-1"))

    private fun sampleOrganization(
        organizationId: String,
        worker: Worker,
    ) = Organization(
        id = organizationId,
        name = "Org 1",
        tribes = listOf(Tribe(name = "Tribe A", squads = listOf(Squad(name = "Squad A", workers = listOf(worker))))),
    )

    fun card(
        id: String = "b0000000-0000-0000-0000-000000000001",
        stepId: String = "50000000-0000-0000-0000-000000000001",
    ): Card =
        Card(
            id = CardId(id),
            step = StepId(stepId),
            title = "Card 1",
            description = "desc",
            position = 0,
            serviceClass = ServiceClass.STANDARD,
            analysisEffort = 3,
            developmentEffort = 5,
            testEffort = 2,
            deployEffort = 1,
            remainingAnalysisEffort = 1,
            remainingDevelopmentEffort = 2,
            remainingTestEffort = 1,
            remainingDeployEffort = 0,
        )

    fun snapshot(
        simulationId: String = "10000000-0000-0000-0000-000000000001",
        scenarioId: String = "a0000000-0000-0000-0000-000000000001",
        day: Int = 2,
    ): DailySnapshot =
        DailySnapshot(
            id = "c0000000-0000-0000-0000-000000000001",
            simulation = SimulationId(simulationId),
            scenario = ScenarioId(scenarioId),
            day = SimulationDay(day),
            metrics =
                FlowMetrics(
                    id = "d0000000-0000-0000-0000-000000000001",
                    throughput = 3,
                    wipCount = 2,
                    blockedCount = 1,
                    avgAgingDays = 1.5,
                ),
            movements =
                listOf(
                    Movement(
                        id = "e0000000-0000-0000-0000-000000000001",
                        type = MovementType.MOVED,
                        cardId = CardId("b0000000-0000-0000-0000-000000000001"),
                        day = SimulationDay(day),
                        reason = "progress",
                    ),
                ),
        )
}
