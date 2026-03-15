package com.kanbanvision.domain.model.metrics

data class FlowMetrics(
    val throughput: Int,
    val wipCount: Int,
    val blockedCount: Int,
    val avgAgingDays: Double,
) {
    init {
        require(throughput >= 0) { "Throughput must be non-negative" }
        require(wipCount >= 0) { "WIP count must be non-negative" }
        require(blockedCount >= 0) { "Blocked count must be non-negative" }
        require(avgAgingDays >= 0.0) { "Average aging days must be non-negative" }
    }
}
