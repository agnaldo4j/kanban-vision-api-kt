package com.kanbanvision.domain.model

/**
 * The four service classes defined by Mike Burrows in *Kanban from the Inside*.
 *
 * Each class represents a distinct cost-of-delay profile and scheduling policy.
 * [com.kanbanvision.domain.simulation.SimulationEngine] schedules TODO cards in the order:
 * [EXPEDITE] → [FIXED_DATE] → [STANDARD] → [INTANGIBLE].
 * [STANDARD] and [INTANGIBLE] are shuffled within their tier to avoid starvation;
 * [FIXED_DATE] is not shuffled because deadline items carry inherent ordering by urgency.
 */
enum class ServiceClass {
    /** Normal work; typical throughput-based queue. Scheduled after [FIXED_DATE], before [INTANGIBLE]. */
    STANDARD,

    /**
     * Highest urgency; always starts before all other classes.
     * Represents critical or emergency work where cost-of-delay is highest.
     */
    EXPEDITE,

    /**
     * Deadline-driven work with a fixed delivery date.
     * Cost-of-delay is low until the deadline approaches, then spikes sharply.
     * Scheduled after [EXPEDITE] and before [STANDARD]. Not shuffled within its tier.
     */
    FIXED_DATE,

    /**
     * Strategic or investigative work with no clear deliverable date.
     * Cost-of-delay is roughly flat over time. Yields capacity to all other classes
     * when WIP is constrained — scheduled last. Shuffled within its tier.
     */
    INTANGIBLE,
}
