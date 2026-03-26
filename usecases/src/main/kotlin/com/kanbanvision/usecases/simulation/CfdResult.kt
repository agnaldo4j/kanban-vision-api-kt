package com.kanbanvision.usecases.simulation

data class CfdDataPoint(
    val day: Int,
    val throughputCumulative: Int,
    val wipCount: Int,
    val blockedCount: Int,
)

data class CfdResult(
    val simulationId: String,
    val series: List<CfdDataPoint>,
)
