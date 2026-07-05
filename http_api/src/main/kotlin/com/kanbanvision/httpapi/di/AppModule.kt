package com.kanbanvision.httpapi.di

import com.kanbanvision.httpapi.events.MicrometerEventPublisher
import com.kanbanvision.persistence.repositories.JdbcOrganizationRepository
import com.kanbanvision.persistence.repositories.JdbcSimulationRepository
import com.kanbanvision.persistence.repositories.JdbcSnapshotRepository
import com.kanbanvision.usecases.ports.DefaultSimulationEngine
import com.kanbanvision.usecases.ports.EventPublisherPort
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.CreateSimulationUseCase
import com.kanbanvision.usecases.simulation.GetDailySnapshotUseCase
import com.kanbanvision.usecases.simulation.GetSimulationCfdUseCase
import com.kanbanvision.usecases.simulation.GetSimulationDaysUseCase
import com.kanbanvision.usecases.simulation.GetSimulationUseCase
import com.kanbanvision.usecases.simulation.ListSimulationsUseCase
import com.kanbanvision.usecases.simulation.RunDayUseCase
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.dsl.bind
import org.koin.dsl.module

object AppModule {
    val koinModule =
        module {
            // bind MeterRegistry: o Koin não resolve por subtipo — sem o binding da
            // interface, o wiring do publisher quebrava em produção com
            // NoDefinitionFoundException (testes não pegam: mockam o port).
            single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) } bind MeterRegistry::class
            single<EventPublisherPort> { MicrometerEventPublisher(get()) }

            single<OrganizationRepository> { JdbcOrganizationRepository() }
            single<SimulationRepository> { JdbcSimulationRepository() }
            single<SnapshotRepository> { JdbcSnapshotRepository() }
            single<SimulationEnginePort> { DefaultSimulationEngine() }

            single { CreateSimulationUseCase(get(), get(), get()) }
            single { GetSimulationUseCase(get()) }
            single { RunDayUseCase(get(), get(), get(), get()) }
            single { GetDailySnapshotUseCase(get()) }
            single { ListSimulationsUseCase(get()) }
            single { GetSimulationDaysUseCase(get()) }
            single { GetSimulationCfdUseCase(get()) }
        }
}
