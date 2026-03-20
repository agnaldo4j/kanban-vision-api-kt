package com.kanbanvision.persistence.support

import com.kanbanvision.domain.model.Ability
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
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
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Seniority
import com.kanbanvision.domain.model.ServiceClass
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationStatus
import com.kanbanvision.domain.model.Squad
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.Tribe
import com.kanbanvision.domain.model.Worker
import com.kanbanvision.persistence.DatabaseConfig
import com.kanbanvision.persistence.DatabaseFactory
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

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
        DatabaseFactory.dataSource.connection.use { conn ->
            conn
                .createStatement()
                .use { stmt ->
                    stmt.execute(
                        """
                        TRUNCATE TABLE daily_snapshots, simulation_states, simulations, cards, steps, boards, organizations
                        RESTART IDENTITY CASCADE
                        """.trimIndent(),
                    )
                }
            conn.commit()
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
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO organizations (id, name) VALUES (?, ?)").use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun insertBoard(
        id: String,
        name: String = "Board",
        createdAt: Long = 0L,
    ) {
        DatabaseFactory.dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO boards (id, name, created_at) VALUES (?, ?, ?)").use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, name)
                stmt.setLong(3, createdAt)
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun insertStep(seed: StepSeed) {
        DatabaseFactory.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    "INSERT INTO steps (id, board_id, name, position, required_ability) VALUES (?, ?, ?, ?, ?)",
                ).use { stmt ->
                    stmt.setString(1, seed.id)
                    stmt.setString(2, seed.boardId)
                    stmt.setString(3, seed.name)
                    stmt.setInt(4, seed.position)
                    stmt.setString(5, seed.requiredAbility)
                    stmt.executeUpdate()
                }
            conn.commit()
        }
    }

    fun insertSimulationRow(seed: SimulationSeed) {
        DatabaseFactory.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    "INSERT INTO simulations (id, organization_id, wip_limit, team_size, seed_value) VALUES (?, ?, ?, ?, ?)",
                ).use { stmt ->
                    stmt.setString(1, seed.id)
                    stmt.setString(2, seed.organizationId)
                    stmt.setInt(3, seed.wipLimit)
                    stmt.setInt(4, seed.teamSize)
                    stmt.setLong(5, seed.seedValue)
                    stmt.executeUpdate()
                }
            conn.commit()
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
                boardId = "60000000-0000-0000-0000-000000000001",
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
            stepId = stepId,
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
        day: Int = 2,
    ): DailySnapshot =
        DailySnapshot(
            id = "c0000000-0000-0000-0000-000000000001",
            simulationId = simulationId,
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
