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

enum class TripCardMode { CURRENT, LAST, EMPTY }

data class TripCardModel(
    val mode: TripCardMode,
    val km: Double?,
    val driveMinutes: Int?,
    val parkMinutes: Int?,
    val driveKwh: Double?,
    val driveL: Double?,
    val parkKwh: Double?,
    val parkL: Double?,
) {
    companion object {
        val EMPTY = TripCardModel(TripCardMode.EMPTY, null, null, null, null, null, null, null)
    }
}

object TripCardCalc {
    /** [today] — recorded trips of today (rows); [dayStartMs] — local midnight. */
    fun from(live: LiveTripState, today: List<TripRow>, dayStartMs: Long): TripCardModel {
        val open = live.open?.estimate()
        if (open != null && movedKm(open.km)) {
            return fromEstimate(TripCardMode.CURRENT, open)
        }
        val recorded = today.filter { (it.km ?: 0.0) > 0.0 }.map { it.startMs to fromRow(TripCardMode.LAST, it) }
        val finished = live.finished.filter {
            it.startMs >= dayStartMs && it.km > 0.0 && !TripMatch.confirmed(it, today)
        }.map { it.startMs to fromEstimate(TripCardMode.LAST, it) }
        return (recorded + finished).maxByOrNull { it.first }?.second ?: TripCardModel.EMPTY
    }

    private fun minutes(ms: Long): Int = (ms / 60_000L).toInt()

    /** A live trip with its drive / park split; an estimate without one (old data) reads as a recorded trip. */
    private fun fromEstimate(mode: TripCardMode, e: TripEstimate): TripCardModel {
        val s = e.split ?: return fromRow(mode, e.toRow())
        return TripCardModel(
            mode, e.km, minutes(s.driveMs), minutes(s.parkMs),
            s.driveKwh.coerceAtLeast(0.0), s.driveL.coerceAtLeast(0.0),
            s.parkKwh.coerceAtLeast(0.0), s.parkL.coerceAtLeast(0.0),
        )
    }

    /** A recorded trip has no split: its duration and energy are «в пути», «стоянка» stays unknown. */
    private fun fromRow(mode: TripCardMode, r: TripRow): TripCardModel {
        val minutes = r.endMs?.let { minutes(it - r.startMs) }?.takeIf { it >= 0 }
        return TripCardModel(
            mode, r.km, minutes, null, r.kwh?.coerceAtLeast(0.0), r.litres?.coerceAtLeast(0.0), null, null,
        )
    }
}

/**
 * «Текущая поездка» / «Последняя поездка» card: km on the left, up to four stats in a 2x2 grid on the right.
 * [tall]: the 366 px card (PM2.5 shown) opens the stat rows 28 apart instead of 18.
 */
@Composable
fun TripCard(model: TripCardModel, hasFuelTank: Boolean, tall: Boolean, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val cells = tripCells(model, hasFuelTank, p.textPrimary, lang)
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
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(if (tall) 28 else 18), Alignment.CenterVertically)) {
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
 * The four trip cells, always all four so the card never looks empty before the first trip: time and
 * energy «в пути» and «стоянка». No data reads «0»; a value a real trip does not have (the stop of a
 * recorded trip) reads «—».
 */
internal fun tripCells(
    m: TripCardModel, hasFuelTank: Boolean, primary: Color, lang: GrLang = GrLang.RU,
): List<TripCell> {
    val w = lang.words
    val empty = m.mode == TripCardMode.EMPTY
    val none = if (empty) "0" else "—"
    fun time(min: Int?) = if (min != null || empty) grDuration(min ?: 0, lang) else "—"
    fun energy(kwh: Double?, litres: Double?, suffix: String): TripCell {
        val k = kwh?.let { grDec(it.coerceAtLeast(0.0), lang = lang) } ?: none
        val l = litres?.let { grDec(it.coerceAtLeast(0.0), lang = lang) } ?: none
        return if (hasFuelTank) TripCell("$k · $l", "${w.kwh} · ${w.litre} $suffix", primary)
        else TripCell(k, "${w.kwh} $suffix", primary)
    }
    return listOf(
        TripCell(time(m.driveMinutes), w.onTheRoad, primary),
        TripCell(time(m.parkMinutes), w.parked, primary),
        energy(m.driveKwh, m.driveL, w.onDriveSuffix),
        energy(m.parkKwh, m.parkL, w.onParkSuffix),
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
