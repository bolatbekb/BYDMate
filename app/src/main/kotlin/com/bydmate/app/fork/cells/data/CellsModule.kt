package com.bydmate.app.fork.cells.data

import android.content.Context
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.service.TrackingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
}
