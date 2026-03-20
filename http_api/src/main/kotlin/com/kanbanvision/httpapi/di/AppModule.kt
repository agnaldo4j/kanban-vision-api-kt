package com.kanbanvision.httpapi.di

import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.persistence.repositories.JdbcBoardRepository
import com.kanbanvision.persistence.repositories.JdbcCardRepository
import com.kanbanvision.persistence.repositories.JdbcOrganizationRepository
import com.kanbanvision.persistence.repositories.JdbcScenarioRepository
import com.kanbanvision.persistence.repositories.JdbcSnapshotRepository
import com.kanbanvision.persistence.repositories.JdbcStepRepository
import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.GetCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import com.kanbanvision.usecases.ports.DefaultSimulationEngine
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.scenario.CreateScenarioUseCase
import com.kanbanvision.usecases.scenario.GetDailySnapshotUseCase
import com.kanbanvision.usecases.scenario.GetFlowMetricsRangeUseCase
import com.kanbanvision.usecases.scenario.GetMovementsByDayUseCase
import com.kanbanvision.usecases.scenario.GetScenarioUseCase
import com.kanbanvision.usecases.scenario.RunDayUseCase
import com.kanbanvision.usecases.step.CreateStepUseCase
import com.kanbanvision.usecases.step.GetStepUseCase
import com.kanbanvision.usecases.step.ListStepsByBoardUseCase
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module

object AppModule {
    val koinModule =
        module {
            single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
            single { DomainMetrics(get()) }

            single<BoardRepository> { JdbcBoardRepository() }
            single<CardRepository> { JdbcCardRepository() }
            single<StepRepository> { JdbcStepRepository() }
            single<OrganizationRepository> { JdbcOrganizationRepository() }
            single<ScenarioRepository> { JdbcScenarioRepository() }
            single<SnapshotRepository> { JdbcSnapshotRepository() }
            single<SimulationEnginePort> { DefaultSimulationEngine() }

            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get(), get(), get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateStepUseCase(get(), get()) }
            single { GetStepUseCase(get()) }
            single { ListStepsByBoardUseCase(get(), get()) }
            single { CreateScenarioUseCase(get(), get()) }
            single { GetScenarioUseCase(get()) }
            single { RunDayUseCase(get(), get(), get()) }
            single { GetDailySnapshotUseCase(get()) }
            single { GetMovementsByDayUseCase(get()) }
            single { GetFlowMetricsRangeUseCase(get()) }
        }
}
