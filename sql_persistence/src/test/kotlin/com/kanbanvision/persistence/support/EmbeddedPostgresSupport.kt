package com.kanbanvision.persistence.support

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.BoardRef
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.DecisionType
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Movement
import com.kanbanvision.domain.model.MovementType
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.PolicySet
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRef
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationRef
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.domain.model.Squad
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.StepRef
import com.kanbanvision.domain.model.Tribe
import com.kanbanvision.domain.model.Worker
import com.kanbanvision.persistence.DatabaseConfig
import com.kanbanvision.persistence.DatabaseFactory
import com.kanbanvision.persistence.tables.BoardsTable
import com.kanbanvision.persistence.tables.OrganizationsTable
import com.kanbanvision.persistence.tables.SimulationsTable
import com.kanbanvision.persistence.tables.StepsTable
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal object EmbeddedPostgresSupport {
    data class StepSeed(
        val id: String,
        val boardId: String,
        val name: String = "Step",
        val position: Int = 0,
        val requiredAbility: String = AbilityName.DEVELOPER.name,
    )

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

    fun insertBoard(
        id: String,
        name: String = "Board",
        createdAt: Long = 0L,
    ) {
        transaction {
            BoardsTable.insert {
                it[BoardsTable.id] = id
                it[BoardsTable.name] = name
                it[BoardsTable.createdAt] = createdAt
            }
        }
    }

    fun insertStep(seed: StepSeed) {
        transaction {
            StepsTable.insert {
                it[StepsTable.id] = seed.id
                it[boardId] = seed.boardId
                it[StepsTable.name] = seed.name
                it[position] = seed.position
                it[requiredAbility] = seed.requiredAbility
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
        val scenario = sampleScenario(worker, simulationId)
        val organization = sampleOrganization(organizationId, worker)
        return Simulation(
            id = simulationId,
            name = "Simulation 1",
            currentDay = SimulationDay(2),
            status = SimulationStatus.RUNNING,
            organization = organization,
            scenario = scenario,
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

    private fun sampleScenario(
        worker: Worker,
        simulationId: String,
    ): Scenario {
        val board = scenarioBoard(worker)
        val scenarioRules = scenarioRules()
        val decision = scenarioDecision()
        return Scenario(
            id = "a0000000-0000-0000-0000-000000000001",
            name = "Scenario 1",
            rules = scenarioRules,
            board = board,
            decisions = listOf(decision),
            history = listOf(snapshot(simulationId = simulationId)),
        )
    }

    private fun scenarioBoard(worker: Worker): Board {
        val step =
            Step(
                id = "50000000-0000-0000-0000-000000000001",
                board = BoardRef("60000000-0000-0000-0000-000000000001"),
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
                cards = listOf(card()),
                workers = listOf(worker),
            )
        return Board(id = "60000000-0000-0000-0000-000000000001", name = "Main Board", steps = listOf(step))
    }

    private fun scenarioRules() =
        ScenarioRules(
            id = "70000000-0000-0000-0000-000000000001",
            policySet = PolicySet(id = "80000000-0000-0000-0000-000000000001", wipLimit = 2),
            wipLimit = 2,
            teamSize = 2,
            seedValue = 42L,
        )

    private fun scenarioDecision() =
        Decision(
            id = "90000000-0000-0000-0000-000000000001",
            type = DecisionType.MOVE_ITEM,
            payload = mapOf("cardId" to "c-1"),
        )

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
            id = id,
            step = StepRef(stepId),
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
            simulation = SimulationRef(simulationId),
            scenario = ScenarioRef(scenarioId),
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
                        cardId = "b0000000-0000-0000-0000-000000000001",
                        day = SimulationDay(day),
                        reason = "progress",
                    ),
                ),
        )
}
