package com.kanbanvision.domain.common.errors

/**
 * Contrato de erro de domínio (ADR-0038). Interface NÃO-sealed de propósito: os grupos por contexto
 * ([CommonError], [KanbanError], [SimulationError]) são `sealed` e exaustivos, mas o topo é aberto —
 * o que (a) força o mapper HTTP a ser total com `else` fail-closed (security.md §Fail Closed) e
 * (b) permite extrair os grupos em módulos por bounded context na Fase 2, já que Kotlin não admite
 * subtipos `sealed` cross-módulo.
 */
interface DomainError
