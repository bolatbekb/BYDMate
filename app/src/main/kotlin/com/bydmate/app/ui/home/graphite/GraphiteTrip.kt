package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bydmate.app.R
import com.bydmate.app.ui.home.common.px
import kotlin.math.roundToInt

enum class TripCardMode { CURRENT, LAST, EMPTY }

data class TripCardModel(
    val mode: TripCardMode,
    val km: Double?,
    val minutes: Int?,
    val avgKmh: Int?,
    val electricShare: Int?,
    val kwh: Double?,
    val litres: Double?,
) {
    companion object {
        val EMPTY = TripCardModel(TripCardMode.EMPTY, null, null, null, null, null, null)
    }
}

object TripCardCalc {
    /** [today] — recorded trips of today (rows); [dayStartMs] — local midnight. */
    fun from(live: LiveTripState, today: List<TripRow>, dayStartMs: Long): TripCardModel {
        val open = live.open?.estimate()
        if (open != null && movedKm(open.km)) {
            return model(TripCardMode.CURRENT, open.toRow())
        }
        val recorded = today.filter { (it.km ?: 0.0) > 0.0 }
        val finished = live.finished.filter {
            it.startMs >= dayStartMs && it.km > 0.0 && !TripMatch.confirmed(it, today)
        }.map { it.toRow() }
        val last = (recorded + finished).maxByOrNull { it.startMs }
        return if (last == null) TripCardModel.EMPTY else model(TripCardMode.LAST, last)
    }

    private fun model(mode: TripCardMode, r: TripRow): TripCardModel {
        val km = r.km
        val minutes = r.endMs?.let { ((it - r.startMs) / 60_000L).toInt() }?.takeIf { it >= 0 }
        val avg = if (km != null && km > 0.0) minutes?.takeIf { it > 0 }?.let { (km / (it / 60.0)).roundToInt() } else null
        val share = if (r.hevKm != null && km != null && km > 0.0) {
            (100.0 * (1.0 - r.hevKm / km)).roundToInt().coerceIn(0, 100)
        } else null
        return TripCardModel(mode, km, minutes, avg, share, r.kwh?.coerceAtLeast(0.0), r.litres?.coerceAtLeast(0.0))
    }
}

/** «Текущая поездка» / «Последняя поездка» card: km on the left, up to four stats in a 2x2 grid on the right. */
@Composable
fun TripCard(model: TripCardModel, hasFuelTank: Boolean, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val cells = tripCells(model, hasFuelTank, p.electric, p.textPrimary, lang)
    CardBody(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CardHeader(R.drawable.gr_ic_route, stringResource(if (model.mode == TripCardMode.LAST) R.string.home_gr_trip_last else R.string.home_gr_trip_current))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FitText(
                            model.km?.let { grDec(it, lang = lang) } ?: "—",
                            grStyle(96f, 250, -4f, p.textPrimary), 96f, 56f, Modifier.weight(1f, fill = false),
                        )
                        Text(
                            lang.words.km, maxLines = 1, softWrap = false,
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

internal data class TripCell(val value: String, val label: String, val color: Color)

/**
 * The four trip cells, always all four so the card never looks empty before the first trip:
 * no data reads «0» for time and energy and «—» for share and speed; a missing value of a
 * real trip reads «—».
 */
internal fun tripCells(
    m: TripCardModel, hasFuelTank: Boolean, electric: Color, primary: Color, lang: GrLang = GrLang.RU,
): List<TripCell> {
    val w = lang.words
    val empty = m.mode == TripCardMode.EMPTY
    val none = if (empty) "0" else "—"
    val kwh = m.kwh?.let { grDec(it, lang = lang) } ?: none
    val litres = m.litres?.let { grDec(it, lang = lang) } ?: none
    val minutes = grDuration(m.minutes ?: 0, lang).takeIf { m.minutes != null || empty } ?: "—"
    return listOf(
        TripCell(minutes, w.onTheRoad, primary),
        TripCell(m.electricShare?.let { "$it%" } ?: "—", w.electricDrive, electric),
        TripCell(m.avgKmh?.let { "$it ${w.kmh}" } ?: "—", w.avgSpeed, primary),
        if (hasFuelTank) TripCell("$kwh · $litres", "${w.kwh} · ${w.litre}", primary) else TripCell(kwh, w.kwh, primary),
    )
}

@Composable
private fun StatCell(c: TripCell, modifier: Modifier) {
    val p = LocalGr.current
    val line = p.divider
    Column(
        modifier.drawBehind { drawRect(line, Offset.Zero, Size(px(1).toPx(), size.height)) }.padding(start = px(18)),
    ) {
        FitText(c.value, grStyle(30f, 500, color = c.color), 30f, 22f, Modifier)
        FitText(c.label, grStyle(19f, 400, color = p.textTertiary, tnum = false), 19f, 15f, Modifier)
    }
}
