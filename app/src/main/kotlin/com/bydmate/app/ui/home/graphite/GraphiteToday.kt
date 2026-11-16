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

/** «Пробег сегодня» card: km today on the left, up to four stats in a 2x2 grid on the right. */
@Composable
fun TodayCard(todayKm: Double?, today: TodayStats?, hasFuelTank: Boolean, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val cells = todayCells(today, hasFuelTank, p.electric, p.textPrimary, lang)
    CardBody(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CardHeader(R.drawable.gr_ic_route, stringResource(R.string.home_gr_today_title))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FitText(
                            todayKm?.let { grDec(it, lang = lang) } ?: "—",
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

internal data class TodayCell(val value: String, val label: String, val color: Color)

/**
 * The four «Пробег сегодня» cells, always all four so the card never looks empty before the
 * first trip: no data reads «0» for time and energy and «—» for share and speed.
 */
internal fun todayCells(
    today: TodayStats?, hasFuelTank: Boolean, electric: Color, primary: Color, lang: GrLang = GrLang.RU,
): List<TodayCell> {
    val w = lang.words
    val kwh = grDec(today?.kwh ?: 0.0, lang = lang).let { if (today?.kwh == null) "0" else it }
    val litres = grDec(today?.litres ?: 0.0, lang = lang).let { if (today?.litres == null) "0" else it }
    return listOf(
        TodayCell(grDuration(today?.minutes ?: 0, lang), w.onTheRoad, primary),
        TodayCell(today?.electricShare?.let { "$it%" } ?: "—", w.electricDrive, electric),
        TodayCell(today?.avgKmh?.let { "$it ${w.kmh}" } ?: "—", w.avgSpeed, primary),
        if (hasFuelTank) TodayCell("$kwh · $litres", "${w.kwh} · ${w.litre}", primary) else TodayCell(kwh, w.kwh, primary),
    )
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
