package com.bydmate.app.ui.home.graphite

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.cdrive.ReadyLamp
import com.bydmate.app.ui.home.cdrive.ReadyShown
import com.bydmate.app.ui.home.common.px
import kotlinx.coroutines.delay
import java.time.LocalDate

/* ---------------------------------------------------------------- top bar */

private const val DATE_TICK_MS = 60_000L
private const val LAMP_TICK_MS = 250L

@Composable
internal fun TopBar(data: HomeData, online: Boolean, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val today = rememberToday()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(14))) {
        // No clock: the car's own status bar shows the time.
        Text(grDate(today, lang), maxLines = 1, softWrap = false, style = grStyle(24f, 400, color = p.textSecondary), modifier = Modifier.padding(end = px(20)))
        engineChip(data.engineRpm, lang)?.let { text ->
            Chip(text, lead = { GrIcon(R.drawable.gr_ic_engine, 26f, p.fuel) })
        }
        modeChip(data.driveMode, data.driveProfile)?.let { text ->
            Chip(text, lead = { GrIcon(R.drawable.gr_ic_leaf, 24f, p.electric) })
        }
        Spacer(Modifier.weight(1f))
        // The car's power state is the link cue too: no link, no state to trust.
        if (online) PowerChip(data.powerState) else OfflineChip()
    }
}

/** «Включен · Ready» → «Включен» + «· Ready» (the quieter part). */
private fun splitNote(text: String): Pair<String, String?> {
    val i = text.indexOf(" · ")
    return if (i < 0) text to null else text.substring(0, i) to text.substring(i + 1)
}

/** Power chip: a glowing Electric dot while on, a TextTertiary dot when off; hidden when unknown. */
@Composable
private fun PowerChip(powerState: Int?) {
    val p = LocalGr.current
    val shown = rememberReadyLamp(powerState)
    if (powerState == null) return
    val text = powerChip(
        when (shown) {
            ReadyShown.READY -> 2
            ReadyShown.ON -> 1
            ReadyShown.OFF -> 0
        },
        LocalGrLang.current,
    ) ?: return
    val (main, note) = splitNote(text)
    val on = shown != ReadyShown.OFF
    Chip(main, note = note, lead = { StatusDot(if (on) p.electric else p.textTertiary, glow = on) })
}

/** A 10 × 10 status dot, with a 10 px glow of its own colour when [glow]. */
@Composable
private fun StatusDot(color: Color, glow: Boolean) {
    Box(
        Modifier.size(px(10)).drawWithCache {
            val u = px(1).toPx()
            val r = size.minDimension / 2
            val halo = if (glow) blurPaint(color, 10 * u) else null
            onDrawBehind {
                if (halo != null) drawIntoCanvas { it.nativeCanvas.drawCircle(center.x, center.y, r, halo) }
                drawCircle(color, r)
            }
        },
    )
}

/** In place of the power chip while the car does not answer: a dim dot and «Нет связи». */
@Composable
private fun OfflineChip() {
    val p = LocalGr.current
    Chip(stringResource(R.string.home_gr_offline), lead = { StatusDot(p.textTertiary, glow = false) })
}

/** Local date, re-read once a minute while the screen is started. */
@Composable
private fun rememberToday(): LocalDate {
    var now by remember { mutableStateOf(LocalDate.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDate.now()
                delay(DATE_TICK_MS)
            }
        }
    }
    return now
}

/**
 * The lamp for [powerState] (as CdHeader): a rise shows at once, a fall only after 2 s, so the
 * ~0.6 s dip of pressing start on a running car does not blink «Выключен».
 */
@Composable
private fun rememberReadyLamp(powerState: Int?): ReadyShown {
    val lamp = remember { ReadyLamp(holdMs = 2_000) }
    val power by rememberUpdatedState(powerState)
    var shown by remember { mutableStateOf(lamp.update(powerState, SystemClock.elapsedRealtime())) }
    LaunchedEffect(powerState) { shown = lamp.update(powerState, SystemClock.elapsedRealtime()) }
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
