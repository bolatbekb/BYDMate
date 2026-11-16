package com.bydmate.app.fork.ledger.data

import android.content.Context
import com.bydmate.app.demo.DemoMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.nativestack.VehicleKindMonitor
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.ledger.live.LedgerLiveWatcher
import com.bydmate.app.service.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LedgerModule {

    @Provides @Singleton
    fun provideLedgerDatabase(@ApplicationContext ctx: Context): LedgerDatabase =
        LedgerDatabase.build(
            ctx,
            if (DemoMode.isOn(ctx)) LedgerDatabase.DEMO_FILE_NAME else LedgerDatabase.FILE_NAME,
        )

    @Provides @Named("ledgerCarData")
    fun provideLedgerCarData(): StateFlow<DiParsData?> = TrackingService.lastData

    @Provides @Named("ledgerVehicleKind")
    fun provideLedgerVehicleKind(monitor: VehicleKindMonitor): StateFlow<VehicleKind> = monitor.kind

    @Provides @Singleton
    fun provideLedgerLiveWatcher(
        repo: LedgerRepository,
        @Named("ledgerCarData") data: @JvmSuppressWildcards StateFlow<DiParsData?>,
        @Named("ledgerVehicleKind") kind: @JvmSuppressWildcards StateFlow<VehicleKind>,
    ): LedgerLiveWatcher =
        LedgerLiveWatcher(repo, data, kind, CoroutineScope(SupervisorJob() + Dispatchers.IO))
}
