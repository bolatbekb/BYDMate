package com.bydmate.app.fork.cells.data

import android.content.Context
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.fork.cells.collect.BMS_FLAG_ITEMS
import com.bydmate.app.fork.cells.collect.CellCollector
import com.bydmate.app.fork.cells.collect.bmsFlagsOf
import com.bydmate.app.service.TrackingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
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

    @Provides @Named("cellsLive")
    fun provideCellsLive(): StateFlow<DiParsData?> = TrackingService.lastData

    @Provides @Singleton
    fun provideCellSource(): CellSource = HelperCellSource()

    /** The flags are getInt reads (tx 5) through the author's batch read, once per stored snapshot (R3). */
    @Provides @Singleton
    fun provideCellCollector(
        settings: CellsSettings,
        source: CellSource,
        repo: CellRepository,
        @Named("cellsLive") live: StateFlow<DiParsData?>,
        helper: HelperClient,
    ): CellCollector = CellCollector(
        settings, source, repo, live,
        flags = { bmsFlagsOf(helper.readBatch(BMS_FLAG_ITEMS)) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
}
