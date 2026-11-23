package com.bydmate.app.fork.cells.collect

import android.app.Application
import android.util.Log
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.demo.DemoCellsSeeder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        runCatching {
            val entry = EntryPointAccessors.fromApplication(app, CellsEntryPoint::class.java)
            if (DemoMode.isOn(app)) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching { DemoCellsSeeder.seedIfEmpty(entry.repository(), entry.settings(), System.currentTimeMillis()) }
                    runCatching { entry.collector().start() }
                        .onFailure { t -> runCatching { Log.w(TAG, "demo cell collector did not start: ${t.javaClass.simpleName}") } }
                }
            } else {
                entry.collector().start()
            }
        }
            .onFailure { t -> runCatching { Log.w(TAG, "cell collector did not start: ${t.javaClass.simpleName}: ${t.message}") } }
    }
}
