package com.bydmate.app.ui.home.graphite

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val LINKS_POLL_MS = 5_000L
private const val VOLUME_POLL_MS = 1_000L

/**
 * The top bar's link icons. No new permissions: a read the app is not allowed to make
 * throws, is caught and the icon is hidden.
 */
object GraphiteLinks {
    data class Links(val bt: Boolean, val wifi: Boolean, val cell: Boolean, val cellType: String?)

    fun read(ctx: Context): Links {
        val caps = runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.getNetworkCapabilities(cm.activeNetwork)
        }.getOrNull()
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        @Suppress("DEPRECATION") // BluetoothManager needs the same permission; this is the short form
        val bt = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)
        val type = if (!cell) null else runCatching {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.dataNetworkType?.let(::cellLabel)
        }.getOrNull()
        return Links(bt, wifi, cell, type)
    }

    /** TelephonyManager.NETWORK_TYPE_* to the label next to the signal icon. */
    internal fun cellLabel(networkType: Int): String? = when (networkType) {
        13 -> "LTE" // NETWORK_TYPE_LTE
        20 -> "5G" // NETWORK_TYPE_NR
        3, 8, 9, 10, 15 -> "3G" // UMTS, HSDPA, HSUPA, HSPA, HSPA+
        else -> null
    }
}

/** Media volume (STREAM_MUSIC) as 0..100. */
object GraphiteVolume {
    fun read(ctx: Context): Int = runCatching {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt() else 0
    }.getOrDefault(0)

    fun set(ctx: Context, percent: Int) {
        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val index = (percent.coerceIn(0, 100) * max / 100f).roundToInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        }
    }
}

/** [GraphiteLinks.read], re-read every 5 s while the screen is started. */
@Composable
fun rememberLinks(): GraphiteLinks.Links {
    val ctx = LocalContext.current
    val links = remember { mutableStateOf(GraphiteLinks.Links(bt = false, wifi = false, cell = false, cellType = null)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                links.value = withContext(Dispatchers.Default) { GraphiteLinks.read(ctx) }
                delay(LINKS_POLL_MS)
            }
        }
    }
    return links.value
}

/**
 * [GraphiteVolume.read], re-read every second while the screen is started. The slider writes
 * the state at once; the next read is the source of truth.
 */
@Composable
fun rememberVolume(): MutableState<Int> {
    val ctx = LocalContext.current
    val volume = remember { mutableStateOf(GraphiteVolume.read(ctx)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                volume.value = GraphiteVolume.read(ctx)
                delay(VOLUME_POLL_MS)
            }
        }
    }
    return volume
}
