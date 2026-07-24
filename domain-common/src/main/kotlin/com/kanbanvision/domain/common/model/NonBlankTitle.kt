package com.kanbanvision.domain.common.model

/**
 * Refinamentos de `String` não-branca como value classes (smart constructors) — GAP-DH.
 *
 * Centralizam o guard `isNotBlank()` que antes era duplicado em cada `init {}`/`create()` de agregado, e tornam
 * "nome/título em branco" **irrepresentável no tipo**: um `NonBlankTitle` só existe se o valor não for branco.
 * Seguem o padrão dos IDs (`Refs.kt`, ADR-0034): `@JvmInline`, sem anotações de framework (o domínio é puro —
 * `DomainPurityTest`), (des)serialização resolvida na borda via `.value` ↔ `NonBlankTitle(String)`.
 */
@JvmInline
value class NonBlankTitle(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "title must not be blank" }
    }
}
