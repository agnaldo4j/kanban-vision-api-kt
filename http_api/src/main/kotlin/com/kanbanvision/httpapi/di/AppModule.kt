package com.kanbanvision.httpapi.di

import com.kanbanvision.domain.port.BoardRepository
import com.kanbanvision.domain.port.CardRepository
import com.kanbanvision.persistence.repositories.ExposedBoardRepository
import com.kanbanvision.persistence.repositories.ExposedCardRepository
import com.kanbanvision.usecases.board.CreateBoardUseCase
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.card.CreateCardUseCase
import com.kanbanvision.usecases.card.MoveCardUseCase
import org.koin.dsl.module

object AppModule {
    val appModule = module {
        single<BoardRepository> { ExposedBoardRepository() }
        single<CardRepository> { ExposedCardRepository() }

        single { CreateBoardUseCase(get()) }
        single { GetBoardUseCase(get()) }
        single { CreateCardUseCase(get()) }
        single { MoveCardUseCase(get()) }
    }
}
