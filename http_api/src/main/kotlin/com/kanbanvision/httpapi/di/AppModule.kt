package com.kanbanvision.httpapi.di

import com.kanbanvision.httpapi.events.MicrometerEventPublisher
import com.kanbanvision.persistence.internal.repositories.JdbcOrganizationRepository
import com.kanbanvision.persistence.internal.repositories.JdbcSimulationRepository
import com.kanbanvision.persistence.internal.repositories.JdbcSnapshotRepository
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
import java.time.Clock

object AppModule {
    val koinModule =
        module {
            // O `bind` é obrigatório: o Koin não resolve por subtipo, e sem ele o publisher quebra só em
            // produção com NoDefinitionFoundException — os testes mockam o port e não alcançam esse caminho.
            single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) } bind MeterRegistry::class
            single<EventPublisherPort> { MicrometerEventPublisher(get()) }

            single<OrganizationRepository> { JdbcOrganizationRepository() }
            single<SimulationRepository> { JdbcSimulationRepository() }
            single<SnapshotRepository> { JdbcSnapshotRepository() }
            single<SimulationEnginePort> { DefaultSimulationEngine() }

            single<Clock> { Clock.systemUTC() }

            single { CreateSimulationUseCase(get(), get(), get(), get()) }
            single { GetSimulationUseCase(get()) }
            single { RunDayUseCase(get(), get(), get(), get(), get()) }
            single { GetDailySnapshotUseCase(get(), get()) }
            single { ListSimulationsUseCase(get()) }
            single { GetSimulationDaysUseCase(get(), get()) }
            single { GetSimulationCfdUseCase(get(), get()) }
        }
}
