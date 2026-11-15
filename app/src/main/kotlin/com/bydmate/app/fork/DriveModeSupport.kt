package com.bydmate.app.fork

import android.content.Context
import android.util.Log
import com.bydmate.app.data.autoservice.SentinelDecoder
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.DriveMode
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.data.vehicle.WriteAllowlist
import com.bydmate.app.demo.DemoMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Which of the everyday drive modes (ECO / NORMAL / SPORT) this car has, for the retro home
 * screens. Read once per process from the car's own dev=1023 support flags — the same flags
 * the author's DriveModeChannel checks before a switch: 0 means the car has the mode.
 * Retried a few times while the helper daemon is still starting; stays null if it never answers.
 */
object DriveModeSupport {
    private const val TAG = "DriveModeSupport"
    private const val TRIES = 6
    private const val RETRY_MS = 10_000L

    /** Display order on the stacks. */
    val EVERYDAY = listOf(DriveMode.ECO, DriveMode.NORMAL, DriveMode.SPORT)

    private val _supported = MutableStateFlow<List<DriveMode>?>(null)

    /** Supported everyday modes in display order; null until the car has answered. */
    val supported: StateFlow<List<DriveMode>?> = _supported.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun helper(): HelperClient
    }

    /** Starts the one-off probe if it has not run yet. Cheap to call on every composition. */
    fun ensureProbed(context: Context) {
        if (_supported.value != null || job?.isActive == true) return
        val app = context.applicationContext
        if (DemoMode.isOn(app)) {
            _supported.value = EVERYDAY
            return
        }
        val helper = EntryPointAccessors.fromApplication(app, Deps::class.java).helper()
        job = scope.launch {
            repeat(TRIES) {
                val flags = EVERYDAY.associateWith { readFlag(helper, it.supportFid) }
                if (flags.values.any { it != null }) {
                    _supported.value = EVERYDAY.filter { flags[it] == 0 }
                    Log.i(TAG, "support flags: $flags")
                    return@launch
                }
                delay(RETRY_MS)
            }
            Log.i(TAG, "support flags unreadable")
        }
    }

    @Suppress("TooGenericExceptionCaught") // a failed read is just "unknown"; the binder can throw anything
    private suspend fun readFlag(helper: HelperClient, fid: Int): Int? = try {
        helper.read(WriteAllowlist.DRIVE_MODE_DEV, fid)?.toInt()?.let { SentinelDecoder.decodeInt(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "flag read failed: ${e.message}")
        null
    }

    /**
     * The mode the car runs now: SETTING_TARGET_DRIVING_MODE when reported (it tells every mode
     * apart), else the older dev=1006 fid with the author's mapping (1 ECO, 2 SPORT, 3 NORMAL).
     */
    fun current(live: DiParsData?): DriveMode? {
        live ?: return null
        live.driveModeTarget?.let { t -> DriveMode.entries.firstOrNull { it.value == t }?.let { return it } }
        return when (live.driveMode) {
            1 -> DriveMode.ECO
            2 -> DriveMode.SPORT
            3 -> DriveMode.NORMAL
            else -> null
        }
    }
}
