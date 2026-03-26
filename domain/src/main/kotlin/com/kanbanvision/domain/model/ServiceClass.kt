package com.kanbanvision.domain.model

/**
 * The four service classes defined by Mike Burrows in *Kanban from the Inside*.
 *
 * Each class represents a distinct cost-of-delay profile and scheduling policy.
 * In [com.kanbanvision.domain.simulation.SimulationEngine], only [EXPEDITE] has active
 * prioritization; [FIXED_DATE], [STANDARD] and [INTANGIBLE] are currently scheduled
 * with equal weight after expedite items are served.
 */
enum class ServiceClass {
    /** Normal work; typical throughput-based queue. Scheduled after [EXPEDITE]. */
    STANDARD,

    /**
     * Highest urgency; always starts before all other classes.
     * Represents critical or emergency work where cost-of-delay is highest.
     */
    EXPEDITE,

    /**
     * Deadline-driven work with a fixed delivery date.
     * Cost-of-delay is low until the deadline approaches, then spikes sharply.
     * Currently scheduled with the same weight as [STANDARD].
     */
    FIXED_DATE,

    /**
     * Strategic or investigative work with no clear deliverable date.
     * Cost-of-delay is roughly flat over time; should yield capacity to
     * [EXPEDITE] and [FIXED_DATE] items when WIP is constrained.
     * Currently scheduled with the same weight as [STANDARD].
     */
    INTANGIBLE,
}
