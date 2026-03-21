package com.kanbanvision.domain.model

import java.time.Instant
import java.util.UUID

data class Card(
    override val id: String = UUID.randomUUID().toString(),
    val stepId: String,
    val title: String,
    val description: String = "",
    val position: Int = 0,
    val serviceClass: ServiceClass = ServiceClass.STANDARD,
    val state: CardState = CardState.TODO,
    val agingDays: Int = 0,
    val analysisEffort: Int = 0,
    val developmentEffort: Int = 0,
    val testEffort: Int = 0,
    val deployEffort: Int = 0,
    val remainingAnalysisEffort: Int = analysisEffort,
    val remainingDevelopmentEffort: Int = developmentEffort,
    val remainingTestEffort: Int = testEffort,
    val remainingDeployEffort: Int = deployEffort,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Card id must not be blank" }
        require(stepId.isNotBlank()) { "Card stepId must not be blank" }
        require(title.isNotBlank()) { "Card title must not be blank" }
        require(position >= 0) { "Card position must be non-negative" }
        require(agingDays >= 0) { "Card agingDays must be non-negative" }
        require(analysisEffort >= 0) { "analysisEffort must be non-negative" }
        require(developmentEffort >= 0) { "developmentEffort must be non-negative" }
        require(testEffort >= 0) { "testEffort must be non-negative" }
        require(deployEffort >= 0) { "deployEffort must be non-negative" }
        require(remainingAnalysisEffort in 0..analysisEffort) { "remainingAnalysisEffort must be between 0 and analysisEffort" }
        require(remainingDevelopmentEffort in 0..developmentEffort) {
            "remainingDevelopmentEffort must be between 0 and developmentEffort"
        }
        require(remainingTestEffort in 0..testEffort) { "remainingTestEffort must be between 0 and testEffort" }
        require(remainingDeployEffort in 0..deployEffort) { "remainingDeployEffort must be between 0 and deployEffort" }
    }

    companion object {
        fun create(
            stepId: String,
            title: String,
            description: String = "",
            position: Int,
        ): Card {
            require(stepId.isNotBlank()) { "Card stepId must not be blank" }
            require(title.isNotBlank()) { "Card title must not be blank" }
            return Card(
                id = UUID.randomUUID().toString(),
                stepId = stepId,
                title = title,
                description = description,
                position = position,
            )
        }
    }

    fun moveTo(
        targetStepId: String,
        newPosition: Int,
    ): Card {
        require(targetStepId.isNotBlank()) { "Card target stepId must not be blank" }
        require(newPosition >= 0) { "Card target position must be non-negative" }
        return copy(stepId = targetStepId, position = newPosition)
    }

    fun advance(): Card =
        when (state) {
            CardState.TODO -> copy(state = CardState.IN_PROGRESS)
            CardState.IN_PROGRESS -> copy(state = CardState.DONE)
            CardState.BLOCKED -> copy(state = CardState.IN_PROGRESS)
            CardState.DONE -> this
        }

    fun block(): Card {
        require(state == CardState.IN_PROGRESS) { "Only IN_PROGRESS cards can be blocked" }
        return copy(state = CardState.BLOCKED)
    }

    fun incrementAge(): Card = copy(agingDays = agingDays + 1)

    fun remainingEffortFor(ability: AbilityName): Int =
        when (ability) {
            AbilityName.PRODUCT_MANAGER -> remainingAnalysisEffort
            AbilityName.DEVELOPER -> remainingDevelopmentEffort
            AbilityName.TESTER -> remainingTestEffort
            AbilityName.DEPLOYER -> remainingDeployEffort
        }

    fun consumeEffort(
        ability: AbilityName,
        points: Int,
        now: Instant = Instant.now(),
    ): Card {
        require(points >= 0) { "Consumed points must be non-negative" }

        return when (ability) {
            AbilityName.PRODUCT_MANAGER ->
                copy(
                    remainingAnalysisEffort = (remainingAnalysisEffort - points).coerceAtLeast(0),
                    audit = audit.touch(now),
                )

            AbilityName.DEVELOPER ->
                copy(
                    remainingDevelopmentEffort = (remainingDevelopmentEffort - points).coerceAtLeast(0),
                    audit = audit.touch(now),
                )

            AbilityName.TESTER ->
                copy(
                    remainingTestEffort = (remainingTestEffort - points).coerceAtLeast(0),
                    audit = audit.touch(now),
                )

            AbilityName.DEPLOYER ->
                copy(
                    remainingDeployEffort = (remainingDeployEffort - points).coerceAtLeast(0),
                    audit = audit.touch(now),
                )
        }
    }
}
