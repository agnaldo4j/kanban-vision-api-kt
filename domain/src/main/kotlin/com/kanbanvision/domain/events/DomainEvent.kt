package com.kanbanvision.domain.events

import java.time.Instant

sealed class DomainEvent {
    abstract val occurredAt: Instant

    data class SimulationCreated(
        val simulationId: String,
        val simulationName: String,
        val organizationId: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class SimulationDayExecuted(
        val simulationId: String,
        val day: Int,
        val throughput: Int,
        val wipCount: Int,
        val blockedCount: Int,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class CardMoved(
        val simulationId: String,
        val cardId: String,
        val day: Int,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class CardBlocked(
        val simulationId: String,
        val cardId: String,
        val day: Int,
        val reason: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class CardUnblocked(
        val simulationId: String,
        val cardId: String,
        val day: Int,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()

    data class CardCompleted(
        val simulationId: String,
        val cardId: String,
        val day: Int,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent()
}
