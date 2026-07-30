package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.SimulationDay

internal fun decodeName(raw: String): NonBlankName = NonBlankName(raw.ifBlank { "(unnamed)" })

internal fun decodeTitle(raw: String): NonBlankTitle = NonBlankTitle(raw.ifBlank { "(untitled)" })

internal fun decodeCardId(raw: String): CardId = CardId(raw.ifBlank { "(unknown)" })

internal fun decodeId(raw: String): String = raw.ifBlank { "(unknown)" }

internal fun decodeDay(raw: Int): SimulationDay = SimulationDay(raw.coerceAtLeast(1))

internal inline fun <reified E : Enum<E>> decodeEnum(
    raw: String,
    fallback: E,
): E = runCatching { enumValueOf<E>(raw) }.getOrDefault(fallback)

internal val CROSS_FIELD_NEUTRAL_ABILITY = AbilityName.DEVELOPER

internal val QUARANTINE_CARD_STATE = CardState.BLOCKED

internal fun surrogateServiceClass(payload: Map<String, String>): ServiceClass =
    decodeEnum(payload["serviceClass"].orEmpty(), ServiceClass.STANDARD)
