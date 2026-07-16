package com.kanbanvision.domain.model

/**
 * Opaque IDs (opaque types) — `@JvmInline value class` de custo zero, type-safe (ADR-0034/GAP-BT).
 * Substituem os antigos `*Ref` `data class(String)` e os `id: String` crus, eliminando a
 * primitive obsession: não se passa um id de um agregado onde se espera outro.
 *
 * Framework-free por design (o `DomainPurityTest` proíbe `kotlinx.serialization` no domínio):
 * a (de)serialização é resolvida na borda (surrogates de `sql_persistence` e DTOs de `http_api`),
 * mapeando `.value` ↔ `XId(String)` — mesmo padrão de `SimulationDay`.
 */
@JvmInline
value class BoardId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "BoardId must not be blank" }
    }
}

@JvmInline
value class StepId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "StepId must not be blank" }
    }
}

@JvmInline
value class CardId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CardId must not be blank" }
    }
}

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
