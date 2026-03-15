package com.kanbanvision.usecases

import arrow.core.Either
import arrow.core.raise.Raise
import kotlin.time.Duration
import kotlin.time.measureTimedValue

suspend fun <E, A> Raise<E>.timed(block: suspend () -> Either<E, A>): Pair<A, Duration> {
    val (result, duration) = measureTimedValue { block() }
    return result.bind() to duration
}
