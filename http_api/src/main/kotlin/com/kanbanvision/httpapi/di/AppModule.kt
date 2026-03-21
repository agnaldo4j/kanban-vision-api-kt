package com.kanbanvision.httpapi.di

import com.kanbanvision.httpapi.metrics.DomainMetrics
import com.kanbanvision.persistence.repositories.JdbcOrganizationRepository
import com.kanbanvision.persistence.repositories.JdbcSimulationRepository
import com.kanbanvision.persistence.repositories.JdbcSnapshotRepository
import com.kanbanvision.usecases.ports.DefaultSimulationEngine
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.CreateSimulationUseCase
import com.kanbanvision.usecases.simulation.GetDailySnapshotUseCase
import com.kanbanvision.usecases.simulation.GetSimulationUseCase
import com.kanbanvision.usecases.simulation.RunDayUseCase
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.module

object AppModule {
    val koinModule =
        module {
            single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
            single { DomainMetrics(get()) }

            single<OrganizationRepository> { JdbcOrganizationRepository() }
            single<SimulationRepository> { JdbcSimulationRepository() }
            single<SnapshotRepository> { JdbcSnapshotRepository() }
            single<SimulationEnginePort> { DefaultSimulationEngine() }

            single { CreateSimulationUseCase(get(), get()) }
            single { GetSimulationUseCase(get()) }
            single { RunDayUseCase(get(), get(), get()) }
            single { GetDailySnapshotUseCase(get()) }
        }
}
