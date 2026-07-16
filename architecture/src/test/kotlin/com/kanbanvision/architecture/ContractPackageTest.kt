package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Fitness function de "pacote de contrato" — o equivalente barato ao `exports` do JPMS
 * (ADR-0033: decisão de NÃO adotar JPMS; Kotlin não tem package-private, então tipos públicos
 * em pacotes de implementação seriam importáveis cross-module).
 *
 * Convenção: um pacote cujo caminho contém um segmento `internal` é privado ao seu módulo.
 * Nenhum arquivo de OUTRO módulo pode referenciá-lo — a única exceção é o `AppModule`
 * (composition root de DI), que instancia os adapters concretos e os liga aos ports de
 * `usecases.repositories`.
 *
 * Cobre os dois vetores (como `ContextBoundaryTest`): `import` directives E referências
 * totalmente qualificadas no código (`com.kanbanvision.persistence.internal.X()` sem import) —
 * senão o gate seria driblável por FQN. Comentários/KDoc são removidos para não gerar
 * falso-positivo com `@link`s / exemplos.
 *
 * Self-service: nomear qualquer pacote `*.internal` passa a protegê-lo automaticamente, sem
 * editar este teste. Subsume a antiga regra "Jdbc/Exposed só no AppModule" (ADR-0028).
 */
class ContractPackageTest {
    @Test
    fun `pacotes internal nao sao referenciados fora do modulo dono`() {
        Konsist
            .scopeFromProduction()
            .files
            .filter { !it.path.endsWith("di/AppModule.kt") }
            .assertFalse { file ->
                val ownModule = moduleOf(file.packagee?.name)
                INTERNAL_REFERENCE
                    .findAll(stripComments(file.text))
                    .any { match -> match.groupValues[1] != ownModule }
            }
    }

    /** Módulo dono de um FQN `com.kanbanvision.<modulo>...` (domain/usecases/persistence/httpapi). */
    private fun moduleOf(fqn: String?): String? = fqn?.removePrefix("com.kanbanvision.")?.substringBefore(".")

    private fun stripComments(source: String): String =
        source
            .replace(BLOCK_COMMENT, "")
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    private companion object {
        // Casa import OU referência totalmente qualificada a um pacote *.internal:
        // com.kanbanvision.<modulo>.(…segmentos…).internal.  — captura o <modulo> no grupo 1.
        private val INTERNAL_REFERENCE = Regex("""com\.kanbanvision\.(\w+)\.(?:\w+\.)*internal\.""")
        private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL))
    }
}
