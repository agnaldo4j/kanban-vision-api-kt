package com.kanbanvision.domain.common.model

/**
 * Nome não-branco como value class (smart constructor) — GAP-DH. Irmão de [NonBlankTitle].
 *
 * Centraliza o guard `isNotBlank()` antes duplicado no `init {}`/`create()` de cada agregado de organização
 * (`Organization`/`Tribe`/`Squad`/`Worker`) e torna "nome em branco" **irrepresentável no tipo**. Segue o padrão
 * dos IDs (`Refs.kt`, ADR-0034): `@JvmInline`, sem anotações de framework (`DomainPurityTest`), (des)serialização
 * na borda via `.value` ↔ `NonBlankName(String)`.
 */
@JvmInline
value class NonBlankName(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "name must not be blank" }
    }
}
