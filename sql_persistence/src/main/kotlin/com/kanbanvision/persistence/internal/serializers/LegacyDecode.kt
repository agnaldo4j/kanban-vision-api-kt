package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.SimulationDay

// Os blobs são registro imutável de leitura: um `require` do domínio rodando sobre dado histórico lança, e
// `Either.catch` transforma isso em 500 no `findById` E na página inteira do `findAll`.
//
// Coagir a sentinel, NUNCA descartar (`migrations.md`): o repositório re-serializa o agregado inteiro a
// cada save, então um `filter`/`skip` aqui vira deleção permanente no próximo `runDay`.

internal fun decodeName(raw: String): NonBlankName = NonBlankName(raw.ifBlank { "(unnamed)" })

internal fun decodeTitle(raw: String): NonBlankTitle = NonBlankTitle(raw.ifBlank { "(untitled)" })

internal fun decodeCardId(raw: String): CardId = CardId(raw.ifBlank { "(unknown)" })

internal fun decodeId(raw: String): String = raw.ifBlank { "(unknown)" }

internal fun decodeDay(raw: Int): SimulationDay = SimulationDay(raw.coerceAtLeast(1))

internal inline fun <reified E : Enum<E>> decodeEnum(
    raw: String,
    fallback: E,
): E = runCatching { enumValueOf<E>(raw) }.getOrDefault(fallback)

// Não pode ser TESTER: `Worker.init` exige `!hasTester || hasDeployer`, então o fallback não pode
// participar de cross-field. Aplicado nos dois lados (requiredAbility do Step e abilities do Worker), o
// que preserva `Step.init` por renomeio consistente.
internal val ABILITY_FALLBACK = AbilityName.DEVELOPER

// BLOCKED e não TODO: é o único estado inerte para o engine. Com TODO o card voltaria à fila e seria
// iniciado no dia seguinte — dado ilegível mudando a simulação sozinho.
internal val CARD_STATE_FALLBACK = CardState.BLOCKED

internal fun surrogateServiceClass(payload: Map<String, String>): ServiceClass =
    decodeEnum(payload["serviceClass"].orEmpty(), ServiceClass.STANDARD)
