package com.kanbanvision.httpapi.di

import com.kanbanvision.persistence.repositories.JdbcBoardRepository
import com.kanbanvision.persistence.repositories.JdbcCardRepository
import com.kanbanvision.persistence.repositories.JdbcColumnRepository
import com.kanbanvision.persistence.repositories.JdbcScenarioRepository
import com.kanbanvision.persistence.repositories.JdbcSnapshotRepository
import com.kanbanvision.persistence.repositories.JdbcTenantRepository
import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.GetCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import com.kanbanvision.usecases.column.CreateColumnUseCase
import com.kanbanvision.usecases.column.GetColumnUseCase
import com.kanbanvision.usecases.column.ListColumnsByBoardUseCase
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.repositories.ColumnRepository
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.repositories.TenantRepository
import com.kanbanvision.usecases.scenario.CreateScenarioUseCase
import com.kanbanvision.usecases.scenario.GetDailySnapshotUseCase
import com.kanbanvision.usecases.scenario.GetFlowMetricsRangeUseCase
import com.kanbanvision.usecases.scenario.GetMovementsByDayUseCase
import com.kanbanvision.usecases.scenario.GetScenarioUseCase
import com.kanbanvision.usecases.scenario.RunDayUseCase
import org.koin.dsl.module

object AppModule {
    val koinModule =
        module {
            single<BoardRepository> { JdbcBoardRepository() }
            single<CardRepository> { JdbcCardRepository() }
            single<ColumnRepository> { JdbcColumnRepository() }
            single<TenantRepository> { JdbcTenantRepository() }
            single<ScenarioRepository> { JdbcScenarioRepository() }
            single<SnapshotRepository> { JdbcSnapshotRepository() }

            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateColumnUseCase(get()) }
            single { GetColumnUseCase(get()) }
            single { ListColumnsByBoardUseCase(get()) }
            single { CreateScenarioUseCase(get(), get()) }
            single { GetScenarioUseCase(get()) }
            single { RunDayUseCase(get(), get()) }
            single { GetDailySnapshotUseCase(get()) }
            single { GetMovementsByDayUseCase(get()) }
            single { GetFlowMetricsRangeUseCase(get()) }
        }
}
