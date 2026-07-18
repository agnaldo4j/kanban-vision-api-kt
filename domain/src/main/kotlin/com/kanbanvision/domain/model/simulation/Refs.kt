package com.kanbanvision.domain.model.simulation

/**
 * IDs opacos do Simulation BC (opaque types) — `@JvmInline value class` de custo zero, type-safe
 * (ADR-0034/GAP-BT). Substituem os antigos `*Ref` `data class(String)` e os `id: String` crus,
 * eliminando a primitive obsession: não se passa um id de um agregado onde se espera outro.
 *
 * Framework-free por design (o `DomainPurityTest` proíbe `kotlinx.serialization` no domínio):
 * a (de)serialização é resolvida na borda (surrogates de `sql_persistence` e DTOs de `http_api`),
 * mapeando `.value` ↔ `XId(String)` — mesmo padrão de `SimulationDay`.
 *
 * IDs do Kanban Management BC (`BoardId`, `StepId`, `CardId`) vivem em `model.kanban.Refs` (GAP-CH/ADR-0038).
 */
@JvmInline
value class SimulationId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "SimulationId must not be blank" }
    }
}

@JvmInline
value class ScenarioId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ScenarioId must not be blank" }
    }
}
