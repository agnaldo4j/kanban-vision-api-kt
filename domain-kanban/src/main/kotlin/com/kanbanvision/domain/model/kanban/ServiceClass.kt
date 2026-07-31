package com.kanbanvision.domain.model.kanban

enum class ServiceClass(
    val schedulingRank: Int,
    val shuffleWithinTier: Boolean,
) {
    STANDARD(schedulingRank = 2, shuffleWithinTier = true),
    EXPEDITE(schedulingRank = 0, shuffleWithinTier = false),
    FIXED_DATE(schedulingRank = 1, shuffleWithinTier = false),
    INTANGIBLE(schedulingRank = 3, shuffleWithinTier = true),
    ;

    companion object {
        fun fromNameOrDefault(raw: String?): ServiceClass = entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}
