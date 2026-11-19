package com.bydmate.app.ui.home.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * fork (perf): the first value at once, then at most one value per [periodMs], always the latest one.
 * Unlike `sample()` it keeps no ticker: while upstream is idle nothing wakes up, and a value that arrives
 * after a quiet spell goes out at once.
 */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
    conflate().transform { value ->
        emit(value)
        delay(periodMs)
    }
