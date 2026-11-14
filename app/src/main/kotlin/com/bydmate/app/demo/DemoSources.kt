package com.bydmate.app.demo

import com.bydmate.app.data.autoservice.AdbOnDeviceClient
import com.bydmate.app.data.autoservice.AutoserviceClient
import com.bydmate.app.data.autoservice.BatteryReading
import com.bydmate.app.data.autoservice.ChargingReading
import com.bydmate.app.data.nativestack.NativeParsReader
import com.bydmate.app.data.nativestack.ParsReader
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.data.vehicle.HelperClient
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The ParsReader binding: the simulated car in demo mode, the real reader otherwise. */
class DemoAwareParsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val native: NativeParsReader,
) : ParsReader {
    override suspend fun fetch(): DiParsData? =
        if (DemoMode.isOn(context)) DemoCar.snapshot() else native.fetch()
}

/** Battery reads come from the simulated car; single fids report "not available". */
class DemoAutoserviceClient : AutoserviceClient {
    override suspend fun isAvailable(): Boolean = true
    override suspend fun getInt(dev: Int, fid: Int): Int? = null
    override suspend fun getIntRaw(dev: Int, fid: Int): Int? = null
    override suspend fun getFloat(dev: Int, fid: Int): Float? = null
    override suspend fun readBatterySnapshot(): BatteryReading = DemoCar.battery()
    override suspend fun readChargingSnapshot(): ChargingReading? = null
}

/**
 * Pretends the on-device ADB channel is up, so no connection banner shows; anything that
 * would actually run a command gets a harmless "nothing happened".
 */
class DemoAdbOnDeviceClient(private val real: AdbOnDeviceClient) : AdbOnDeviceClient by real {
    override suspend fun connect(): Result<Unit> = Result.success(Unit)
    override suspend fun isConnected(): Boolean = true
    override suspend fun exec(cmd: String): String? = null
    override suspend fun spawnHelper(token: String): Boolean = true
    override suspend fun killHelper(): Boolean = true
}

/** Reports a live helper daemon; reads have no value, writes "succeed" without touching anything. */
class DemoHelperClient(private val real: HelperClient) : HelperClient by real {
    override suspend fun isAlive(): Boolean = true
    override suspend fun read(dev: Int, fid: Int, tx: Int): Long? = null
    override suspend fun readBatch(items: List<BatchReadItem>): List<Pair<Int, Int>>? = null
    override suspend fun write(dev: Int, fid: Int, value: Int): Boolean = true
}

/** Hilt provider helpers: wrap the real binding when demo mode is on. */
object DemoBindings {
    fun adb(context: Context, real: AdbOnDeviceClient): AdbOnDeviceClient =
        if (DemoMode.isOn(context)) DemoAdbOnDeviceClient(real) else real

    fun autoservice(context: Context, real: () -> AutoserviceClient): AutoserviceClient =
        if (DemoMode.isOn(context)) DemoAutoserviceClient() else real()

    fun helper(context: Context, real: HelperClient): HelperClient =
        if (DemoMode.isOn(context)) DemoHelperClient(real) else real
}
