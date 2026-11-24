package com.bydmate.app.fork.alerts.di

import android.content.Context
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.fork.alerts.center.AlertCenter
import com.bydmate.app.fork.alerts.center.AlertSettings
import com.bydmate.app.fork.alerts.demo.DemoAlertSource
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.ScalarUnit
import com.bydmate.app.fork.alerts.source.AlertSource
import com.bydmate.app.fork.alerts.source.AndroidStorageProbe
import com.bydmate.app.fork.alerts.source.Direction
import com.bydmate.app.fork.alerts.source.ScalarAlertSource
import com.bydmate.app.fork.alerts.source.StorageAlertSource
import com.bydmate.app.fork.alerts.source.StubAlertSource
import com.bydmate.app.fork.alerts.source.ThresholdSpec
import com.bydmate.app.fork.alerts.source.VolumeInfo
import com.bydmate.app.fork.alerts.source.WheelAlertSource
import com.bydmate.app.service.TrackingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import javax.inject.Singleton

/**
 * Wires the alert sources, the settings and the center. Demo-aware like the rest of the fork: in
 * demo mode only the seed source is active, so the emulator shows the same picture every launch and
 * the live sources (which read null in demo) do not interfere. fork-local.
 */
@Module
@InstallIn(SingletonComponent::class)
object AlertsModule {

    @Provides @Singleton
    fun provideAlertSettings(@ApplicationContext ctx: Context): AlertSettings =
        AlertSettings(
            ctx.getSharedPreferences(AlertSettings.prefsName(DemoMode.isOn(ctx)), Context.MODE_PRIVATE),
        )

    @Provides @Singleton
    fun provideAlertSources(
        @ApplicationContext ctx: Context,
        settings: AlertSettings,
    ): Set<@JvmSuppressWildcards AlertSource> =
        if (DemoMode.isOn(ctx)) setOf(DemoAlertSource()) else realSources(ctx, settings)

    @Provides @Singleton
    fun provideAlertCenter(
        sources: Set<@JvmSuppressWildcards AlertSource>,
        settings: AlertSettings,
    ): AlertCenter = AlertCenter(sources, settings, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    /** The live stream, gated on freshness: a reading older than [MAX_AGE_MS] (car gone) reads null. */
    private fun liveFresh(): Flow<DiParsData?> {
        val live: StateFlow<DiParsData?> = TrackingService.lastData
        return combine(live, ticker(FRESH_TICK_MS)) { data, _ ->
            if (TrackingService.lastDataAtMs != 0L &&
                System.currentTimeMillis() - TrackingService.lastDataAtMs < MAX_AGE_MS
            ) {
                data
            } else {
                null
            }
        }.distinctUntilChanged()
    }

    private fun realSources(ctx: Context, settings: AlertSettings): Set<AlertSource> {
        val now = System::currentTimeMillis
        return storageSources(ctx, settings, now) + liveSignalSources(liveFresh(), now) + stubSources()
    }

    private fun storageSources(ctx: Context, settings: AlertSettings, now: () -> Long): Set<AlertSource> {
        val probe = AndroidStorageProbe(ctx)
        val input: Flow<List<VolumeInfo>> = ticker(STORAGE_POLL_MS).map { probe.volumes() }
        return setOf(
            StorageAlertSource(
                input,
                specOf = {
                    ThresholdSpec(
                        Direction.LOW,
                        warn = settings.config.value.storageWarnPercent.toDouble(),
                        crit = STORAGE_CRIT_PERCENT,
                        clearMargin = STORAGE_CLEAR_MARGIN,
                    )
                },
                now = now,
            ),
        )
    }

    private fun liveSignalSources(live: Flow<DiParsData?>, now: () -> Long): Set<AlertSource> = setOf(
        ScalarAlertSource(
            AlertSourceId.VOLT_12V,
            ThresholdSpec(Direction.LOW, warn = 11.8, crit = 11.4, clearMargin = 0.2),
            ScalarUnit.VOLT,
            live.map { it?.voltage12v },
            now,
        ),
        WheelAlertSource(
            AlertSourceId.TYRE_PRESSURE,
            ThresholdSpec(Direction.LOW, warn = 180.0, crit = 150.0, clearMargin = 20.0),
            ScalarUnit.KPA,
            live.map { listOf(it?.tirePressFL, it?.tirePressFR, it?.tirePressRL, it?.tirePressRR).map { v -> v?.toDouble() } },
            now,
        ),
        WheelAlertSource(
            AlertSourceId.TYRE_TEMP,
            ThresholdSpec(Direction.HIGH, warn = 80.0, crit = 95.0, clearMargin = 5.0),
            ScalarUnit.CELSIUS,
            live.map { listOf(it?.tyreTempFL, it?.tyreTempFR, it?.tyreTempRL, it?.tyreTempRR).map { v -> v?.toDouble() } },
            now,
        ),
        ScalarAlertSource(
            AlertSourceId.BATTERY_TEMP,
            ThresholdSpec(Direction.HIGH, warn = 50.0, crit = 55.0, clearMargin = 3.0),
            ScalarUnit.CELSIUS,
            live.map { it?.maxBatTemp?.toDouble() },
            now,
        ),
        ScalarAlertSource(
            AlertSourceId.INSULATION,
            ThresholdSpec(Direction.LOW, warn = 500.0, crit = 100.0, clearMargin = 200.0),
            ScalarUnit.KOHM,
            live.map { it?.insulationKohm?.toDouble() },
            now,
        ),
    )

    // Registered so their block and settings row show; silent until wired (see StubAlertSource).
    private fun stubSources(): Set<AlertSource> = setOf(
        StubAlertSource(AlertSourceId.CELL_IMBALANCE),
        StubAlertSource(AlertSourceId.COOLANT_TEMP),
        StubAlertSource(AlertSourceId.MOTOR_TEMP),
    )

    private fun ticker(periodMs: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMs)
        }
    }

    private const val FRESH_TICK_MS = 3_000L
    private const val MAX_AGE_MS = 10_000L
    private const val STORAGE_POLL_MS = 120_000L
    private const val STORAGE_CRIT_PERCENT = 3.0
    private const val STORAGE_CLEAR_MARGIN = 2.0
}
