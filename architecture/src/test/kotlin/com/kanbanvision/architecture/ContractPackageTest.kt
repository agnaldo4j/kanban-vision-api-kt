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
 * Nenhum arquivo de OUTRO módulo pode importá-lo — a única exceção é o `AppModule` (composition
 * root de DI), que instancia os adapters concretos e os liga aos ports de `usecases.repositories`.
 *
 * Self-service: nomear qualquer pacote `*.internal` passa a protegê-lo automaticamente, sem
 * editar este teste. Subsume a antiga regra "Jdbc/Exposed só no AppModule" (ADR-0028).
 */
class ContractPackageTest {
    @Test
    fun `pacotes internal nao sao importados fora do modulo dono`() {
        Konsist
            .scopeFromProduction()
            .files
            .filter { !it.path.endsWith("di/AppModule.kt") }
            .assertFalse { file ->
                val ownModule = moduleOf(file.packagee?.name)
                file.imports.any { import ->
                    isInternal(import.name) && moduleOf(import.name) != ownModule
                }
            }
    }

    /** Módulo dono de um FQN `com.kanbanvision.<modulo>...` (domain/usecases/persistence/httpapi). */
    private fun moduleOf(fqn: String?): String? = fqn?.removePrefix("com.kanbanvision.")?.substringBefore(".")

    /** Um pacote é interno quando tem um segmento `internal` no caminho. */
    private fun isInternal(fqn: String): Boolean = fqn.contains(".internal.")
}
