package com.bydmate.app.fork.cells.collect

import android.app.Application
import android.util.Log
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellsSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CellsEntryPoint {
    fun collector(): CellCollector
    fun repository(): CellRepository
    fun settings(): CellsSettings
}

/** The app start hook of the battery check: the collector stays idle until a car profile is chosen and collection is on. */
object CellsStartup {
    private const val TAG = "CellsStartup"

    /** Never throws: a failure only leaves the battery check without its collector until the next start. */
    fun start(app: Application) {
        runCatching { EntryPointAccessors.fromApplication(app, CellsEntryPoint::class.java).collector().start() }
            .onFailure { t -> runCatching { Log.w(TAG, "cell collector did not start: ${t.javaClass.simpleName}: ${t.message}") } }
    }
}
