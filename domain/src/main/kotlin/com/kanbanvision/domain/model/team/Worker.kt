package com.kanbanvision.domain.model.team

import com.kanbanvision.domain.model.Column

data class Worker(
    val name: String,
    val abilities: Set<Ability>,
) {
    init {
        require(name.isNotBlank()) { "Worker name must not be blank" }
        require(abilities.isNotEmpty()) { "Worker must have at least one ability" }
        val hasTester = abilities.any { it.name == AbilityName.TESTER }
        val hasDeployer = abilities.any { it.name == AbilityName.DEPLOYER }
        require(!hasTester || hasDeployer) { "Worker with TESTER ability must also have DEPLOYER ability" }
    }

    fun hasAbility(abilityName: AbilityName): Boolean = abilities.any { it.name == abilityName }

    fun canExecute(column: Column): Boolean = hasAbility(column.requiredAbility)
}
