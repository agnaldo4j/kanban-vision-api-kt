package com.kanbanvision.persistence.tables

import org.jetbrains.exposed.v1.core.Table

internal object BoardsTable : Table("boards") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

internal object StepsTable : Table("steps") {
    val id = varchar("id", 36)
    val boardId = varchar("board_id", 36).references(BoardsTable.id)
    val name = varchar("name", 255)
    val position = integer("position")
    val requiredAbility = varchar("required_ability", 64)

    override val primaryKey = PrimaryKey(id)
}

internal object CardsTable : Table("cards") {
    val id = varchar("id", 36)
    val stepId = varchar("step_id", 36).references(StepsTable.id)
    val title = varchar("title", 255)
    val description = text("description")
    val position = integer("position")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

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
    val stateJson = text("state_json")

    override val primaryKey = PrimaryKey(simulationId)
}

internal object DailySnapshotsTable : Table("daily_snapshots") {
    val simulationId = varchar("simulation_id", 36).references(SimulationsTable.id)
    val day = integer("day")
    val snapshotJson = text("snapshot_json")

    override val primaryKey = PrimaryKey(simulationId, day)
}
