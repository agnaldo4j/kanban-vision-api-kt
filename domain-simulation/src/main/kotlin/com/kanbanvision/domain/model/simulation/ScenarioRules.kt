package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import com.kanbanvision.domain.model.organization.PolicySet
import java.util.UUID

data class ScenarioRules(
    override val id: String = UUID.randomUUID().toString(),
    val policySet: PolicySet,
    val teamSize: Int,
    val seedValue: Long,
    override val audit: Audit = Audit(),
) : Domain<String> {
    /**
     * Single source of truth for the WIP limit: [policySet]. This is a delegating accessor, not a
     * stored field, so a value diverging from the policy set is unrepresentable — no reconciliation
     * guard is needed, and `PolicySet` already enforces `wipLimit > 0`.
     */
    val wipLimit: Int get() = policySet.wipLimit

    init {
        require(id.isNotBlank()) { "ScenarioRules id must not be blank" }
        require(teamSize > 0) { "Team size must be greater than zero" }
    }

    companion object {
        fun create(
            wipLimit: Int,
            teamSize: Int,
            seedValue: Long,
        ): ScenarioRules =
            ScenarioRules(
                policySet = PolicySet(wipLimit = wipLimit),
                teamSize = teamSize,
                seedValue = seedValue,
            )
    }
}
