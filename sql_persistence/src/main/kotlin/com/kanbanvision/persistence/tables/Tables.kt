package com.kanbanvision.persistence.tables

import org.jetbrains.exposed.v1.core.Table

internal object OrganizationsTable : Table("organizations") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}

internal object SimulationsTable : Table("simulations") {
    val id = varchar("id", 36)
    val organizationId = varchar("organization_id", 36).references(OrganizationsTable.id)
    val wipLimit = integer("wip_limit")
    val teamSize = integer("team_size")
    val seedValue = long("seed_value")

    override val primaryKey = PrimaryKey(id)
}

internal object SimulationStatesTable : Table("simulation_states") {
    val simulationId = varchar("simulation_id", 36).references(SimulationsTable.id)
    val stateJson = jsonb("state_json")

    override val primaryKey = PrimaryKey(simulationId)
}

internal object DailySnapshotsTable : Table("daily_snapshots") {
    val simulationId = varchar("simulation_id", 36).references(SimulationsTable.id)
    val day = integer("day")
    val snapshotJson = jsonb("snapshot_json")

    override val primaryKey = PrimaryKey(simulationId, day)
}
