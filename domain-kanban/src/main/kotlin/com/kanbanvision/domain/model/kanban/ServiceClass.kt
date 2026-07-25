package com.kanbanvision.domain.model.kanban

/**
 * The four service classes defined by Mike Burrows in *Kanban from the Inside*.
 *
 * Each class represents a distinct cost-of-delay profile **and carries its own scheduling policy** (OOD:
 * enum-carrega-comportamento — a política mora no tipo, não num `when` do engine). O engine ordena os cards TODO
 * genericamente por [schedulingRank] crescente e, dentro do tier, embaralha sse [shuffleWithinTier] — resultando
 * na ordem [EXPEDITE] → [FIXED_DATE] → [STANDARD] → [INTANGIBLE], com STANDARD/INTANGIBLE embaralhados para evitar
 * inanição e FIXED_DATE preservando a ordem (deadline já ordena por urgência).
 *
 * @property schedulingRank menor = agendado antes (tier de prioridade). **Deve ser distinto entre as variantes**
 *   — é ele que define a ordem total do agendamento (o teste de política ancora essa ordem).
 * @property shuffleWithinTier embaralhar os cards do mesmo rank (anti-starvation) vs. manter a ordem.
 */
enum class ServiceClass(
    val schedulingRank: Int,
    val shuffleWithinTier: Boolean,
) {
    /** Normal work; typical throughput-based queue. Scheduled after [FIXED_DATE], before [INTANGIBLE]. */
    STANDARD(schedulingRank = 2, shuffleWithinTier = true),

    /**
     * Highest urgency; always starts before all other classes.
     * Represents critical or emergency work where cost-of-delay is highest.
     */
    EXPEDITE(schedulingRank = 0, shuffleWithinTier = false),

    /**
     * Deadline-driven work with a fixed delivery date.
     * Cost-of-delay is low until the deadline approaches, then spikes sharply.
     * Scheduled after [EXPEDITE] and before [STANDARD]. Not shuffled within its tier.
     */
    FIXED_DATE(schedulingRank = 1, shuffleWithinTier = false),

    /**
     * Strategic or investigative work with no clear deliverable date.
     * Cost-of-delay is roughly flat over time. Yields capacity to all other classes
     * when WIP is constrained — scheduled last. Shuffled within its tier.
     */
    INTANGIBLE(schedulingRank = 3, shuffleWithinTier = true),
}
