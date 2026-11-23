package com.bydmate.app.fork.cells.data

import android.content.Context
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.fork.cells.collect.BMS_FLAG_ITEMS
import com.bydmate.app.fork.cells.collect.CellCollector
import com.bydmate.app.fork.cells.collect.bmsFlagsOf
import com.bydmate.app.fork.cells.coolant.CoolantReader
import com.bydmate.app.fork.cells.coolant.DemoCoolant
import com.bydmate.app.fork.cells.coolant.HelperCoolantReader
import com.bydmate.app.fork.cells.demo.DemoCellSource
import com.bydmate.app.fork.cells.motors.DemoMotorTempsReader
import com.bydmate.app.fork.cells.motors.HelperMotorTempsReader
import com.bydmate.app.fork.cells.motors.MotorTempsReader
import com.bydmate.app.fork.cells.verdict.CellHistoryLoader
import com.bydmate.app.service.TrackingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CellsModule {

    @Provides @Singleton
    fun provideCellsSettings(@ApplicationContext ctx: Context): CellsSettings =
        CellsSettings(
            ctx.getSharedPreferences(CellsSettings.prefsName(DemoMode.isOn(ctx)), Context.MODE_PRIVATE),
        )

    @Provides @Singleton
    fun provideCellsDatabase(@ApplicationContext ctx: Context): CellsDatabase =
        CellsDatabase.build(
            ctx,
            if (DemoMode.isOn(ctx)) CellsDatabase.DEMO_FILE_NAME else CellsDatabase.FILE_NAME,
        )

    @Provides @Singleton
    fun provideCellRepository(@ApplicationContext ctx: Context, db: CellsDatabase): CellRepository {
        val name = if (DemoMode.isOn(ctx)) CellsDatabase.DEMO_FILE_NAME else CellsDatabase.FILE_NAME
        return CellRepository(db.dao()) { ctx.getDatabasePath(name) }
    }

    /** The verdict's reads of one measurement's history; stateless, so a new one per window is fine. */
    @Provides
    fun provideCellHistoryLoader(repo: CellRepository): CellHistoryLoader = CellHistoryLoader(repo)

    @Provides @Named("cellsLive")
    fun provideCellsLive(): StateFlow<DiParsData?> = TrackingService.lastData

    @Provides @Singleton
    fun provideCellSource(@ApplicationContext ctx: Context): CellSource =
        if (DemoMode.isOn(ctx)) DemoCellSource(TrackingService.lastData) else HelperCellSource()

    /** The capacity the driver entered in the app's settings: the battery check's one adapter to that author field. */
    @Provides @Singleton
    fun providePackCapacity(settings: SettingsRepository): PackCapacity = PackCapacity(settings)

    /**
     * The flags are getInt reads (tx 5) through the author's batch read, once per stored snapshot (R3). 1C follows the
     * entered capacity, kept at hand as a state (one settings key observed).
     */
    @Suppress("LongParameterList") // why: a Hilt provider; every parameter is one dependency of the collector
    @Provides @Singleton
    fun provideCellCollector(
        settings: CellsSettings,
        source: CellSource,
        repo: CellRepository,
        @Named("cellsLive") live: StateFlow<DiParsData?>,
        helper: HelperClient,
        capacity: PackCapacity,
    ): CellCollector {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val kwh = capacity.observe().stateIn(scope, SharingStarted.Eagerly, null)
        return CellCollector(
            settings, source, repo, live,
            // the demo never touches the real helper: no BMS flags there
            flags = { if (source is DemoCellSource) null else bmsFlagsOf(helper.readBatch(BMS_FLAG_ITEMS)) },
            scope = scope,
            liveAtMs = { TrackingService.lastDataAtMs },
            capacityKwh = { kwh.value },
        )
    }

    /** Coolant °C of the Leopard 5; the demo never touches the real helper. */
    @Provides @Singleton
    fun provideCoolantReader(@ApplicationContext ctx: Context, helper: HelperClient): CoolantReader =
        if (DemoMode.isOn(ctx)) DemoCoolant(TrackingService.lastData) else HelperCoolantReader(helper)

    /** Motor and inverter °C of the Leopard 5; the demo keeps the demo car's own values. */
    @Provides @Singleton
    fun provideMotorTempsReader(@ApplicationContext ctx: Context, helper: HelperClient): MotorTempsReader =
        if (DemoMode.isOn(ctx)) DemoMotorTempsReader(TrackingService.lastData) else HelperMotorTempsReader(helper)
}
