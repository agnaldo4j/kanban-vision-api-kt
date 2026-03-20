package com.kanbanvision.domain.model

import java.time.Instant
import java.util.UUID

data class SimulatorCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val analysisEffort: Int,
    val developmentEffort: Int,
    val testEffort: Int,
    val deployEffort: Int,
    val remainingAnalysisEffort: Int = analysisEffort,
    val remainingDevelopmentEffort: Int = developmentEffort,
    val remainingTestEffort: Int = testEffort,
    val remainingDeployEffort: Int = deployEffort,
    val audit: Audit = Audit(),
) {
    val createdDate get() = audit.createdAt
    val updatedDate get() = audit.updatedAt
    val deletedDate get() = audit.deletedAt

    init {
        require(id.isNotBlank()) { "Card id must not be blank" }
        require(title.isNotBlank()) { "Card title must not be blank" }
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
    ): SimulatorCard {
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
