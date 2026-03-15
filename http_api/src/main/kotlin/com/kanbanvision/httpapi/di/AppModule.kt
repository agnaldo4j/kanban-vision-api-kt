package com.kanbanvision.httpapi.di

import com.kanbanvision.persistence.repositories.JdbcBoardRepository
import com.kanbanvision.persistence.repositories.JdbcCardRepository
import com.kanbanvision.persistence.repositories.JdbcColumnRepository
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
import org.koin.dsl.module

object AppModule {
    val koinModule =
        module {
            single<BoardRepository> { JdbcBoardRepository() }
            single<CardRepository> { JdbcCardRepository() }
            single<ColumnRepository> { JdbcColumnRepository() }

            single { CreateBoardUseCase(get()) }
            single { GetBoardUseCase(get()) }
            single { CreateCardUseCase(get()) }
            single { GetCardUseCase(get()) }
            single { MoveCardUseCase(get()) }
            single { CreateColumnUseCase(get()) }
            single { GetColumnUseCase(get()) }
            single { ListColumnsByBoardUseCase(get()) }
        }
}
