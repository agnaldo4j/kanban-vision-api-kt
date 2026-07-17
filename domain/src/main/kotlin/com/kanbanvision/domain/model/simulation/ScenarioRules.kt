package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Domain
import com.kanbanvision.domain.model.organization.PolicySet
import java.util.UUID

data class ScenarioRules(
    override val id: String = UUID.randomUUID().toString(),
    val policySet: PolicySet,
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long,
    override val audit: Audit = Audit(),
) : Domain<String> {
    init {
        require(id.isNotBlank()) { "ScenarioRules id must not be blank" }
        require(wipLimit > 0) { "WIP limit must be greater than zero" }
        require(teamSize > 0) { "Team size must be greater than zero" }
        require(policySet.wipLimit == wipLimit) { "PolicySet wipLimit must match ScenarioRules wipLimit" }
    }

    companion object {
        fun create(
            wipLimit: Int,
            teamSize: Int,
            seedValue: Long,
        ): ScenarioRules =
            ScenarioRules(
                policySet = PolicySet(wipLimit = wipLimit),
                wipLimit = wipLimit,
                teamSize = teamSize,
                seedValue = seedValue,
            )
    }
}
