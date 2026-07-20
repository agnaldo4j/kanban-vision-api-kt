package com.kanbanvision.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoParentProvider
import com.lemonappdev.konsist.api.provider.KoPropertyProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fitness function de aciclicidade classe↔classe DENTRO de um mesmo pacote (ADR-0026).
 *
 * [PackageCycleTest] pega ciclos ENTRE pacotes (grafo import→pacote), mas duas classes no mesmo
 * pacote referenciam-se por nome simples, **sem import** — logo um ciclo classe↔classe intra-pacote
 * é invisível àquele gate. A skill `/circular-dependency-control` classifica esse caso como o de
 * maior severidade (init recursiva em DI → `StackOverflowError` no Koin; testes inviáveis em
 * isolamento). Os value-class IDs (ADR-0034: `BoardId`/`StepId`/…) quebram os back-refs de agregado,
 * então cada pacote de domínio é hoje um DAG de composição — este teste **trava** essa propriedade.
 *
 * **Escopo da aresta (decisão do gap): composição + supertipos.** A→B existe quando B é o tipo de uma
 * PROPRIEDADE de A (desembrulhando genéricos: `List<Card>` → `Card`) ou um SUPERTIPO de A. Tipos em
 * assinaturas de método (parâmetros/retorno) NÃO contam — é o grafo de "quem constrói/contém quem",
 * exatamente o risco de inicialização recursiva. (Ex.: `Worker.canExecute(step: Step)` é ignorado;
 * só `Step.workers: List<Worker>` conta.)
 *
 * **Identidade de nó por FQN (GAP-CV):** cada nó é o FQN da declaração (incl. enclosing —
 * `pkg.Outer.Nested`), não o nome simples. Assim aninhadas homônimas no mesmo pacote (`Outer.State` vs
 * `Other.State`) e `companion object` não colapsam num nó só. As refs (nomes simples tokenizados) são
 * resolvidas ao FQN por [buildClassGraph]; resíduo: uma ref homônima irresolvível por escopo é
 * conservadoramente pulada (viés a falso-negativo, nunca a falso-positivo — ver [ClassNode]).
 *
 * **Limitação (declaration-surface-only, igual ao gate de pacote):** Konsist é declaration-level, não
 * um motor de type-resolution. Referências só no CORPO de um método (instanciações, `val` locais),
 * tipos inferidos (property sem tipo explícito) e indireção por `typealias` ficam invisíveis.
 */
class ClassCycleTest {
    @Test
    fun `nao ha ciclos classe-classe dentro de um pacote de producao`() {
        val violations =
            declarationsByPackage().mapNotNull { (pkg, decls) ->
                findCycle(buildClassGraph(decls))?.let { cycle ->
                    "$pkg: ${cycle.joinToString(" -> ")}"
                }
            }

        assertTrue(violations.isEmpty()) {
            buildString {
                appendLine("Ciclo(s) de composição classe↔classe intra-pacote detectado(s):")
                violations.forEach { appendLine("  - $it") }
                append(
                    "Quebre com um value-class ID (ADR-0034) ou inversão de dependência " +
                        "(skill /circular-dependency-control).",
                )
            }
        }
    }

    /**
     * Todas as declarações de tipo de produção agrupadas por pacote, cada uma reduzida a um [ClassNode]
     * `(fqn, nomeSimples, tiposReferenciados)` — refs de propriedades diretas + supertipos.
     * Agrupamos por FILE (que carrega o pacote) e unimos os pacotes espalhados por vários arquivos.
     */
    private fun declarationsByPackage(): Map<String, List<ClassNode>> {
        val byPackage = mutableMapOf<String, MutableList<ClassNode>>()
        Konsist.scopeFromProduction().files.forEach { file ->
            val pkg = file.packagee?.name ?: return@forEach
            val bucket = byPackage.getOrPut(pkg) { mutableListOf() }
            file.classes(includeNested = true).forEach { bucket += it.toNode() }
            file.interfaces(includeNested = true).forEach { bucket += it.toNode() }
            file.objects(includeNested = true).forEach { bucket += it.toNode() }
        }
        return byPackage
    }

    private companion object {
        /** Identificadores nus de um texto de tipo — `List<Card>` → {List, Card}; `Card?` → {Card}. */
        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /**
         * Reduz uma declaração a um [ClassNode]. O nó é identificado pelo FQN (incl. enclosing).
         * Referências vêm de propriedades DIRETAS (sem nested/local — corpos e classes aninhadas são
         * nós próprios) e supertipos. Genéricos são cobertos tokenizando o texto do tipo (`type.text`).
         */
        private fun <T> T.toNode(): ClassNode
            where T : KoNameProvider, T : KoPropertyProvider, T : KoParentProvider, T : KoFullyQualifiedNameProvider {
            val refs = mutableSetOf<String>()
            properties(includeNested = false).forEach { prop ->
                prop.type?.text?.let { refs += IDENTIFIER.findAll(it).map(MatchResult::value) }
            }
            parents(indirectParents = false).forEach { refs += it.name }
            return ClassNode(fqn = fullyQualifiedName ?: name, simpleName = name, refs = refs)
        }
    }
}
