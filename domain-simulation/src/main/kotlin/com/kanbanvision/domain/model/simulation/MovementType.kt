package com.kanbanvision.domain.model.simulation

/**
 * Kind of a [Movement] recorded by the simulation engine.
 *
 * A sum type rather than an enum so a tag read from a persisted blob that this release does not know
 * — written by a newer release that was later rolled back — can be carried as [Unknown] instead of
 * throwing. [tag] is the wire representation and reproduces exactly what the former `enum.name`
 * emitted, so `state_json` and `snapshot_json` stay byte-identical for the four known kinds.
 */
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

    /** Legacy or unrecognised tag from a persisted blob — round-trips faithfully, drives no behaviour. */
    data class Unknown(
        override val tag: String,
    ) : MovementType

    companion object {
        /** Tolerant decode for persisted tags: an unrecognised value becomes [Unknown] instead of throwing. */
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
