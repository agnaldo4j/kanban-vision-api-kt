package com.kanbanvision.domain.model.simulation

sealed interface MovementType {
    val tag: String

    data object MOVED : MovementType {
        override val tag: String = "MOVED"
    }

    data object BLOCKED : MovementType {
        override val tag: String = "BLOCKED"
    }

    data object UNBLOCKED : MovementType {
        override val tag: String = "UNBLOCKED"
    }

    data object COMPLETED : MovementType {
        override val tag: String = "COMPLETED"
    }

    data class Unknown(
        override val tag: String,
    ) : MovementType

    companion object {
        fun fromTag(raw: String): MovementType =
            when (raw) {
                MOVED.tag -> MOVED
                BLOCKED.tag -> BLOCKED
                UNBLOCKED.tag -> UNBLOCKED
                COMPLETED.tag -> COMPLETED
                else -> Unknown(raw)
            }
    }
}
