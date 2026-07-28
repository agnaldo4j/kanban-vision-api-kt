package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.SimulationDay

/**
 * Decode tolerante a legado dos blobs JSONB (GAP-DH #355 · GAP-DS #366 · GAP-DV).
 *
 * Os blobs são **registro imutável de leitura**: contêm dados gravados por releases anteriores, quando a
 * borda aceitava valores que hoje seriam inválidos — ou por releases MAIS NOVOS, quando um rollback deixa
 * pods antigos lendo tags que não conhecem. Reconstruir o agregado com construtores crús
 * (`NonBlankName`, `Enum.valueOf`) faz o `require` rodar sobre esse histórico e **lançar**:
 * `Either.catch` → `PersistenceError` → 500 em `findById` **e na página inteira** de `findAll`, que mapeia
 * as linhas dentro do mesmo bloco.
 *
 * A regra (`.claude/rules/migrations.md`) é **coagir a sentinel, nunca descartar**: o repositório
 * re-serializa o agregado INTEIRO a cada save, então um `filter`/`skip` no decode vira deleção permanente
 * no próximo `runDay` — o dado sumiria do banco por um caminho que ninguém leu como escrita.
 *
 * Limitação declarada: coagir a uma constante **perde o valor cru** no próximo save. Preservá-lo exigiria
 * converter os enums em sum type portador (como `MovementType.Unknown(raw)`), o que é [M] — `CardState` é
 * usado em `when` no engine, nos DTOs e nas métricas.
 */

internal fun decodeName(raw: String): NonBlankName = NonBlankName(raw.ifBlank { "(unnamed)" })

internal fun decodeTitle(raw: String): NonBlankTitle = NonBlankTitle(raw.ifBlank { "(untitled)" })

internal fun decodeCardId(raw: String): CardId = CardId(raw.ifBlank { "(unknown)" })

internal fun decodeId(raw: String): String = raw.ifBlank { "(unknown)" }

/** `SimulationDay.init` exige `value >= 1`; um dia 0/negativo num blob legado lançaria. */
internal fun decodeDay(raw: Int): SimulationDay = SimulationDay(raw.coerceAtLeast(1))

internal inline fun <reified E : Enum<E>> decodeEnum(
    raw: String,
    fallback: E,
): E = runCatching { enumValueOf<E>(raw) }.getOrDefault(fallback)

/**
 * `DEVELOPER`, e não `TESTER`: `Worker.init` exige `!hasTester || hasDeployer`, então um worker coagido
 * para TESTER sem DEPLOYER passaria a lançar — o fallback não pode participar de nenhum cross-field.
 *
 * Aplicado nos **dois lados** (o `requiredAbility` do Step e as `abilities` dos Workers), o que preserva
 * `Step.init`'s `workers.all { hasAbility(requiredAbility) }` por *renomeio consistente*: no cenário real
 * (release novo cria uma ability, rollback) os dois lados carregam o mesmo valor ilegível e degradam
 * juntos. Filtrar os workers incompatíveis também resolveria o require — mas seria descarte, proibido.
 */
internal val ABILITY_FALLBACK = AbilityName.DEVELOPER

/**
 * `BLOCKED`, e não `TODO`: é o único estado que significa "visível mas inerte". `autoAdvance` só toca
 * TODO, `applyMove` recusa BLOCKED (GAP-DU) e a execução de worker só toca IN_PROGRESS — então a carga
 * não quebra **e** a simulação não muda sozinha por causa de um dado ilegível. Com TODO, o card voltaria
 * à fila e seria iniciado no próximo dia, ressuscitando trabalho em silêncio. O card fica visível em
 * `blockedCount` e sai da quarentena por um `UnblockItem` explícito.
 */
internal val CARD_STATE_FALLBACK = CardState.BLOCKED

/**
 * Herdado do GAP-DS: o payload de uma decisão legada pode não trazer `serviceClass`. Chave ausente e
 * valor ilegível colapsam no mesmo caminho — `decodeEnum` já devolve o fallback para `""`, então não há
 * um `?:` externo (que seria um ramo a mais, sem comportamento a mais).
 */
internal fun surrogateServiceClass(payload: Map<String, String>): ServiceClass =
    decodeEnum(payload["serviceClass"].orEmpty(), ServiceClass.STANDARD)
