package com.kanbanvision.usecases.simulation

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.common.errors.DomainError
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.usecases.repositories.SimulationRepository

/**
 * Ponto **único** de autorização de tenancy sobre `Simulation` (GAP-DW; lado use case do seam que o
 * GAP-BJ abriu no http_api): carrega a simulação pelo id e falha com [CommonError.Forbidden] se ela não
 * pertencer à organização do chamador.
 *
 * O par carga+guard estava copiado verbatim em 5 use cases. Nenhum vazava — mas um 6º que esquecesse o
 * `ensure` vazaria cross-tenant **sem quebrar teste algum**, porque nada exigia o guard. Por isso esta
 * função vem acompanhada da fitness function `ConventionsTest`: ela torna fácil acertar, a regra torna
 * difícil errar.
 *
 * **Nunca leia uma `Simulation` por id direto do repositório dentro de um use case** — use esta função.
 * A exceção legítima é a leitura por LISTA (`findAll(organizationId, …)`), onde a tenancy já vem do
 * filtro e não há o que autorizar depois.
 *
 * O `reason` é deliberadamente genérico e **não é observável pelo chamador**: `EitherRespond` mapeia
 * qualquer [CommonError.Forbidden] para o corpo `{"error":"Forbidden"}`. A distinção entre "não existe"
 * e "não é sua" fica no domínio e no log, nunca na resposta (security.md, A01).
 */
suspend fun Raise<DomainError>.loadOwnedSimulation(
    repository: SimulationRepository,
    id: SimulationId,
    callerOrganizationId: String,
): Simulation {
    val simulation = repository.findById(id).bind()
    ensure(simulation.organization.id == callerOrganizationId) {
        CommonError.Forbidden("Simulation does not belong to the caller's organization")
    }
    return simulation
}
