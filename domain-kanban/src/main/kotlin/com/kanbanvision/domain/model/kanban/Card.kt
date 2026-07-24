package com.kanbanvision.domain.model.kanban

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import java.time.Instant
import java.util.UUID

data class Card(
    override val id: CardId = CardId(UUID.randomUUID().toString()),
    val step: StepId,
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
) : Domain<CardId> {
    init {
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
            step: StepId,
            title: String,
            description: String = "",
            position: Int,
        ): Card {
            require(title.isNotBlank()) { "Card title must not be blank" }
            return Card(
                id = CardId(UUID.randomUUID().toString()),
                step = step,
                title = title,
                description = description,
                position = position,
            )
        }
    }

    fun moveTo(
        targetStep: StepId,
        newPosition: Int,
    ): Card {
        require(newPosition >= 0) { "Card target position must be non-negative" }
        return copy(step = targetStep, position = newPosition)
    }

    fun advance(): Card =
        when (state) {
            CardState.TODO -> copy(state = CardState.IN_PROGRESS)
            CardState.IN_PROGRESS -> copy(state = CardState.DONE)
            CardState.BLOCKED -> copy(state = CardState.IN_PROGRESS)
            CardState.DONE -> this
        }

    // ADR-0044: transição de estado inválida (bloquear card não-IN_PROGRESS) é regra de domínio → Either.
    fun block(): Either<KanbanError, Card> =
        either {
            ensure(state == CardState.IN_PROGRESS) { KanbanError.CardNotInProgress(id.value) }
            copy(state = CardState.BLOCKED)
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
        now: Instant,
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
