package com.kanbanvision.domain.model

import java.util.UUID

data class FlowMetrics(
    override val id: String = UUID.randomUUID().toString(),
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "FlowMetrics id must not be blank" }
        require(throughput >= 0) { "Throughput must be non-negative" }
        require(wipCount >= 0) { "WIP count must be non-negative" }
        require(blockedCount >= 0) { "Blocked count must be non-negative" }
        require(avgAgingDays >= 0.0) { "Average aging days must be non-negative" }
    }
}
