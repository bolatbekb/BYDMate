@file:Suppress("MagicNumber") // overlays placed on the backplate's pixel grid (tools/cdrive/LAYOUT.md)

package com.bydmate.app.ui.home.cdrive

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.hybrid.Txt
import com.bydmate.app.ui.home.hybrid.at
import kotlinx.coroutines.delay

private val ModeWhite = Color(0xFFF2FFF4)
private val GearWhite = Color(0xFFEBFFFF)
private val ReadyGreen = Color(0xFF56FFA4)
private val HeaderDim = Color(0xFF6E7673)

/** How often the READY lamp looks at the clock, so its 1 s hold can run out between data samples. */
private const val LAMP_TICK_MS = 250L

/**
 * The black panel at the top right: drive mode (with the leaf for ECO), gear letter and the
 * READY lamp. The printed dividers stay; everything else is drawn here.
 */
@Composable
internal fun CdHeader(data: HomeData) {
    ModeWord(data.driveProfile)
    val gear = gearLetter(data.gear)
    Txt(gear, 2344, 64, 46f, if (gear == "—") HeaderDim else GearWhite, glow = gear != "—", centre = true, width = 80)
    when (rememberReadyLamp(data.powerState)) {
        ReadyShown.READY -> Txt("READY", 2459, 64, 29.5f, ReadyGreen, glow = true, centre = true, width = 110)
        ReadyShown.ON -> Txt("ON", 2459, 64, 29.5f, HeaderDim, centre = true, width = 110)
        ReadyShown.OFF -> Txt("—", 2459, 64, 29.5f, HeaderDim, centre = true, width = 110)
    }
}

/** ECO sits right of the leaf as on the mockup; other modes are centred in the zone, smaller when long. */
@Composable
private fun ModeWord(mode: String?) {
    when {
        mode == null -> Txt("—", 2216, 64, 29.5f, HeaderDim, centre = true, width = 128)
        mode == "ECO" -> {
            Image(painterResource(R.drawable.cd_leaf), null, Modifier.at(2158, 44, 45, 41), contentScale = ContentScale.FillBounds)
            Txt(mode, 2212, 64, 29.5f, ModeWhite, glow = true, width = 70)
        }
        else -> {
            val size = when {
                mode.length <= 4 -> 29.5f
                mode.length <= 6 -> 26f
                else -> 21f
            }
            Txt(mode, 2216, 64, size, ModeWhite, glow = true, centre = true, width = 128)
        }
    }
}

/**
 * The lamp state for [powerState], re-read from the system clock every [LAMP_TICK_MS] while the
 * screen is started, so a short dip is held and a real fall still shows after the hold.
 */
@Composable
private fun rememberReadyLamp(powerState: Int?): ReadyShown {
    val lamp = remember { ReadyLamp() }
    val power by rememberUpdatedState(powerState)
    var shown by remember { mutableStateOf(lamp.update(powerState, SystemClock.elapsedRealtime())) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                shown = lamp.update(power, SystemClock.elapsedRealtime())
                delay(LAMP_TICK_MS)
            }
        }
    }
    return shown
}
