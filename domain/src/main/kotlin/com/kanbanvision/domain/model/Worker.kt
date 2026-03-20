package com.kanbanvision.domain.model

import java.util.UUID
import kotlin.random.Random

data class Worker(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val abilities: Set<Ability>,
    val audit: Audit = Audit(),
) {
    val createdDate get() = audit.createdAt
    val updatedDate get() = audit.updatedAt
    val deletedDate get() = audit.deletedAt

    init {
        require(id.isNotBlank()) { "Worker id must not be blank" }
        require(name.isNotBlank()) { "Worker name must not be blank" }
        require(abilities.isNotEmpty()) { "Worker must have at least one ability" }

        val hasTester = abilities.any { it.name == AbilityName.TESTER }
        val hasDeployer = abilities.any { it.name == AbilityName.DEPLOYER }
        require(!hasTester || hasDeployer) { "Worker with TESTER ability must also have DEPLOYER ability" }
    }

    fun hasAbility(abilityName: AbilityName): Boolean = abilities.any { it.name == abilityName }

    fun canExecute(step: Step): Boolean = hasAbility(step.requiredAbility)

    /**
     * Generates daily execution capacities for all abilities.
     * Non-deployer capacities are random and uniformly distributed in [minPoints, maxPoints].
     * Deployer gets max capacity because deploy execution is gate-based, not capacity-based.
     */
    fun generateDailyCapacities(
        random: Random,
        minPoints: Int = 0,
        maxPoints: Int = 10,
    ): Map<AbilityName, Int> {
        require(minPoints >= 0) { "minPoints must be non-negative" }
        require(maxPoints >= minPoints) { "maxPoints must be greater than or equal to minPoints" }

        return AbilityName.entries.associateWith { ability ->
            when {
                !hasAbility(ability) -> 0
                ability == AbilityName.DEPLOYER -> Int.MAX_VALUE
                else -> random.nextInt(from = minPoints, until = maxPoints + 1)
            }
        }
    }
}
