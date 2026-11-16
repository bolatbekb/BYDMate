package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.bydmate.app.R
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.hybrid.px
import java.util.Locale
import kotlin.math.roundToInt

private val Side = 264f

/** «Медиа» card: source pill, cover and title, progress, transport row and media volume. */
@Composable
fun MediaCard(music: NowPlaying, controls: MusicControls, modifier: Modifier) {
    val p = LocalGr.current
    val volume = rememberVolume()
    val ctx = LocalContext.current
    CardBody(modifier) {
        val label = music.sourceLabel
        if (label != null) SourcePill(label) else CardHeader(R.drawable.gr_ic_note, "Медиа")
        Spacer(Modifier.height(px(22)))
        Row(Modifier.fillMaxWidth().height(px(Side)), horizontalArrangement = Arrangement.spacedBy(px(30))) {
            Cover(music)
            Meta(music, Modifier.weight(1f).fillMaxHeight().padding(bottom = px(6)))
        }
        Spacer(Modifier.height(px(28)))
        Progress(music)
        Spacer(Modifier.height(px(6)))
        Controls(music, controls)
        Spacer(Modifier.weight(1f).heightIn(min = px(14)))
        // The whole sunken block takes the touch, not just the 28 px bar.
        InsetBox(26f, Modifier.fillMaxWidth().volumeInput(volume) { v -> GraphiteVolume.set(ctx, v) }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = px(VolPad), vertical = px(18)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(px(VolGap)),
            ) {
                GrIcon(R.drawable.gr_ic_volume, VolIcon, p.textSecondary)
                VolumeBar(volume.value, Modifier.weight(1f))
                Text(
                    volume.value.toString(), maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                    style = grStyle(26f, 500, color = p.textPrimary), modifier = Modifier.width(px(VolNumber)),
                )
            }
        }
    }
}

@Composable
private fun SourcePill(label: String) {
    val p = LocalGr.current
    Row(Modifier.height(px(34)), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true)
                .grPill(p.chip, p.chipEdge, p.edgeStyle, 18f)
                .padding(start = px(12), end = px(16), top = px(8), bottom = px(8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(px(10)),
        ) {
            GrIcon(R.drawable.gr_ic_note, 22f, p.spotify)
            Text(
                label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = grStyle(21f, 500, color = p.textPrimary, tnum = false),
            )
        }
    }
}

@Composable
private fun Cover(music: NowPlaying) {
    val p = LocalGr.current
    val art = music.art
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(
            image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(px(Side)).clip(RoundedCornerShape(px(24)))
                .border(px(1), Color.White.copy(alpha = 0.08f), RoundedCornerShape(px(24))),
        )
    } else {
        InsetBox(24f, Modifier.size(px(Side))) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GrIcon(R.drawable.gr_ic_note, 72f, p.textTertiary)
            }
        }
    }
}

@Composable
private fun Meta(music: NowPlaying, modifier: Modifier) {
    val p = LocalGr.current
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        if (!music.hasTrack) {
            FitText("Нет музыки", grStyle(50f, 500, -1f, p.textSecondary, tnum = false), 50f, 36f, Modifier)
            return@Column
        }
        Text(
            (if (music.isPlaying) "Сейчас играет" else "Пауза").uppercase(Locale.getDefault()),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            style = grStyle(20f, 400, 2f, p.textTertiary, tnum = false),
        )
        Spacer(Modifier.height(px(12)))
        FitText(music.title.orEmpty(), grStyle(50f, 500, -1f, p.textPrimary, tnum = false), 50f, 36f, Modifier)
        if (!music.artist.isNullOrBlank()) {
            Spacer(Modifier.height(px(12)))
            FitText(music.artist, grStyle(28f, 400, color = p.textSecondary, tnum = false), 28f, 22f, Modifier)
        }
        val sub = listOfNotNull(music.album?.takeIf { it.isNotBlank() }, music.year?.toString()).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(px(8)))
            FitText(sub, grStyle(21f, 400, color = p.textTertiary, tnum = false), 21f, 17f, Modifier)
        }
    }
}

@Composable
private fun Progress(music: NowPlaying) {
    val p = LocalGr.current
    val fraction = mediaFraction(music.positionMs, music.durationMs)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(px(20)).drawBehind {
                val h = px(6).toPx()
                val top = (size.height - h) / 2
                val r = CornerRadius(h / 2)
                drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(0f, top), Size(size.width, h), r)
                val w = size.width * fraction
                if (w > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(p.progressStart, p.progressEnd), 0f, size.width),
                        Offset(0f, top), Size(w, h), r,
                    )
                }
                val cx = (w).coerceIn(px(10).toPx(), size.width - px(10).toPx())
                drawCircle(Color.White.copy(alpha = 0.12f), px(15).toPx(), Offset(cx, size.height / 2))
                drawCircle(Color.White, px(10).toPx(), Offset(cx, size.height / 2))
            },
        )
        Spacer(Modifier.height(px(12)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val st = grStyle(20f, 400, color = p.textTertiary)
            Text(grTrackTime(music.positionMs), maxLines = 1, softWrap = false, style = st)
            Text(grTrackTime(music.durationMs), maxLines = 1, softWrap = false, style = st)
        }
    }
}

@Composable
private fun Controls(music: NowPlaying, controls: MusicControls) {
    val p = LocalGr.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = px(6)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val shuffle = music.shuffleAction
        val repeat = music.repeatAction
        CtrlButton(R.drawable.gr_ic_shuffle, 34f, p.textTertiary, shuffle != null) { shuffle?.let(controls::custom) }
        CtrlButton(R.drawable.gr_ic_prev, 44f, p.textPrimary, true) { controls.previous() }
        Box(
            Modifier.size(px(112)).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(p.playTop, p.playBottom)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls.playPause() },
            contentAlignment = Alignment.Center,
        ) {
            GrIcon(if (music.isPlaying) R.drawable.gr_ic_pause else R.drawable.gr_ic_play, 50f, p.bg)
        }
        CtrlButton(R.drawable.gr_ic_next, 44f, p.textPrimary, true) { controls.next() }
        CtrlButton(R.drawable.gr_ic_repeat, 34f, p.textTertiary, repeat != null) { repeat?.let(controls::custom) }
    }
}

@Composable
private fun CtrlButton(icon: Int, size: Float, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(px(88)).clip(CircleShape).alpha(if (enabled) 1f else 0.4f)
            .then(
                if (enabled) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) { GrIcon(icon, size, tint) }
}

// Volume row geometry (mockup px): padding, icon, gap, bar, gap, number, padding.
private const val VolPad = 24f
private const val VolIcon = 32f
private const val VolGap = 20f
private const val VolNumber = 46f
private const val VolKnob = 14f

/** 0..100 at [x] along a bar starting at [start], [width] long; clamped at both ends. */
internal fun volumeOnTrack(x: Float, start: Float, width: Float): Int =
    if (width <= 0f) 0 else ((x - start) / width * 100f).roundToInt().coerceIn(0, 100)

/** Whether [x] is on the bar or within [slop] of its ends (a tap on the icon or the number does nothing). */
internal fun nearTrack(x: Float, start: Float, width: Float, slop: Float): Boolean =
    x >= start - slop && x <= start + width + slop

/** The bar inside the volume block, in this pointer scope's px: start, length, knob slop. */
private class VolTrack(val start: Float, val width: Float, val slop: Float) {
    fun near(x: Float) = nearTrack(x, start, width, slop)
    fun at(x: Float) = volumeOnTrack(x, start, width)
}

private fun PointerInputScope.volTrack(): VolTrack {
    val start = px(VolPad + VolIcon + VolGap).toPx()
    val width = size.width - start - px(VolGap + VolNumber + VolPad).toPx()
    return VolTrack(start, width, px(VolKnob).toPx())
}

/**
 * Volume input for the whole block: a tap on the bar or a drag that starts on it sets the value
 * at once; a drag then follows the finger past the ends. [GrVolume.dragging] holds the poll off.
 */
private fun Modifier.volumeInput(volume: GrVolume, set: (Int) -> Unit): Modifier {
    fun setTo(v: Int) {
        if (v != volume.value) {
            volume.value = v
            set(v)
        }
    }
    return pointerInput(volume) {
        detectTapGestures { volTrack().let { t -> if (t.near(it.x)) setTo(t.at(it.x)) } }
    }.pointerInput(volume) {
        var active = false
        val stop = { active = false; volume.dragging = false }
        detectHorizontalDragGestures(
            onDragStart = {
                val t = volTrack()
                active = t.near(it.x)
                if (active) {
                    volume.dragging = true
                    setTo(t.at(it.x))
                }
            },
            onDragEnd = stop,
            onDragCancel = stop,
            onHorizontalDrag = { change, _ -> if (active) setTo(volTrack().at(change.position.x)) },
        )
    }
}

/** The volume bar, drawing only; [volumeInput] on the block handles the touch. */
@Composable
private fun VolumeBar(value: Int, modifier: Modifier) {
    val p = LocalGr.current
    Box(
        modifier.height(px(28))
            .drawBehind {
                val h = px(8).toPx()
                val top = (size.height - h) / 2
                val r = CornerRadius(h / 2)
                drawRoundRect(p.track, Offset(0f, top), Size(size.width, h), r)
                val w = size.width * value / 100f
                if (w > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(p.textPrimary.copy(alpha = 0.25f), p.textPrimary), 0f, size.width),
                        Offset(0f, top), Size(w, h), r,
                    )
                }
                drawCircle(Color.Black.copy(alpha = 0.25f), px(VolKnob).toPx(), Offset(w, size.height / 2 + px(2).toPx()))
                drawCircle(Color.White, px(VolKnob).toPx(), Offset(w, size.height / 2))
            },
    )
}

/** «Пробег сегодня» card: km today on the left, up to four stats in a 2x2 grid on the right. */
@Composable
fun TodayCard(todayKm: Double?, today: TodayStats?, modifier: Modifier) {
    val p = LocalGr.current
    val cells = todayCells(today, p.electric, p.textPrimary)
    CardBody(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CardHeader(R.drawable.gr_ic_route, "Пробег сегодня")
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FitText(
                            todayKm?.let { grDec(it) } ?: "—",
                            grStyle(96f, 250, -4f, p.textPrimary), 96f, 56f, Modifier.weight(1f, fill = false),
                        )
                        Text(
                            "км", maxLines = 1, softWrap = false,
                            style = grStyle(34f, 400, color = p.textSecondary, tnum = false),
                            modifier = Modifier.padding(start = px(6), bottom = px(12)),
                        )
                    }
                }
            }
            Spacer(Modifier.width(px(28)))
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(18), Alignment.CenterVertically)) {
                for (row in cells.chunked(2)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(26))) {
                        // an odd last cell takes the whole row: a hidden stat leaves no hole
                        for (c in row) StatCell(c, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal data class TodayCell(val value: String, val label: String, val color: Color)

internal fun todayCells(today: TodayStats?, electric: Color, primary: Color): List<TodayCell> {
    if (today == null) return emptyList()
    val out = ArrayList<TodayCell>(4)
    if (today.minutes > 0) out += TodayCell(grDuration(today.minutes), "в пути", primary)
    today.electricShare?.let { out += TodayCell("$it%", "электротяга", electric) }
    today.avgKmh?.let { out += TodayCell("$it км/ч", "средняя скорость", primary) }
    val kwh = today.kwh
    val l = today.litres
    when {
        kwh != null && l != null -> out += TodayCell("${grDec(kwh)} · ${grDec(l)}", "кВт·ч · л", primary)
        kwh != null -> out += TodayCell(grDec(kwh), "кВт·ч", primary)
        l != null -> out += TodayCell(grDec(l), "л", primary)
    }
    return out
}

@Composable
private fun StatCell(c: TodayCell, modifier: Modifier) {
    val p = LocalGr.current
    val line = p.divider
    Column(
        modifier.drawBehind { drawRect(line, Offset.Zero, Size(px(1).toPx(), size.height)) }.padding(start = px(18)),
    ) {
        FitText(c.value, grStyle(30f, 500, color = c.color), 30f, 22f, Modifier)
        FitText(c.label, grStyle(19f, 400, color = p.textTertiary, tnum = false), 19f, 15f, Modifier)
    }
}

/** 0..1 through the track; 0 when position or duration is unknown (the bar stays empty). */
internal fun mediaFraction(positionMs: Long?, durationMs: Long?): Float {
    if (positionMs == null || durationMs == null || durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
