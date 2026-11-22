package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.ConditionPart
import com.bydmate.app.fork.cells.ui.Joined
import com.bydmate.app.fork.cells.ui.LocalCheckNarrow
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.conditionsLine
import com.bydmate.app.fork.cells.ui.kindRes
import com.bydmate.app.fork.cells.ui.levelColor
import com.bydmate.app.fork.cells.ui.partText
import com.bydmate.app.fork.cells.ui.rangeText
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.ui.zoneRes
import com.bydmate.app.fork.cells.stream.NO_TEMP
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.tech.DASH
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.roundToInt

// The four checks of «Линейка»: spread, weak cells, probes (R17: only the cold rule), conditions. fork-local.

/** What one check card says: title and what it means, the value and what it is held against; [desc] and [value] are a [TextRes], a [Joined] or a plain string. */
private class CheckTexts(val title: TextRes, val desc: Any, val value: Any, val sub: TextRes)

private fun checkTexts(k: RulerCheckKind, e: Evaluation, locale: Locale): CheckTexts = when (k) {
    RulerCheckKind.SPREAD -> CheckTexts(
        TextRes(R.string.fork_cells_ruler_spread),
        TextRes(R.string.fork_cells_ruler_spread_desc),
        TextRes(R.string.fork_cells_val_mv, e.spreadMv),
        TextRes(R.string.fork_cells_ruler_spread_norm, e.limits.normMv, TextRes(zoneRes(e.row))),
    )
    RulerCheckKind.WEAK_CELLS -> weakTexts(e)
    RulerCheckKind.PROBES -> probeTexts(e)
    RulerCheckKind.CONDITIONS -> CheckTexts(
        TextRes(R.string.fork_cells_ruler_cond),
        Joined(conditionsLine(e).filter { it !is ConditionPart.Kind && it !is ConditionPart.Probes }.map { partText(it, locale) }),
        TextRes(kindRes(e.input.kind)),
        TextRes(
            when {
                e.level == Level.NONE -> R.string.fork_cells_ruler_cond_none
                e.preliminary -> R.string.fork_cells_ruler_cond_prelim
                else -> R.string.fork_cells_ruler_cond_honest
            },
        ),
    )
}

/** Cells below the median: the deepest one and how many are off, a repeated laggard first. */
private fun weakTexts(e: Evaluation): CheckTexts {
    val strong = e.classes.count { it == CellClass.STRONGLY_LOW }
    val noticeable = e.classes.count { it == CellClass.NOTICEABLY_LOW }
    val weak = strong + noticeable
    val lag = e.laggards.firstOrNull()
    val desc = when {
        lag != null -> TextRes(R.string.fork_cells_reason_laggard, lag.cell, lag.times, lag.of)
        weak > 0 -> TextRes(R.string.fork_cells_ruler_weak_lowest, e.lowestCell, (e.medianMv - e.vminMv).roundToInt())
        else -> TextRes(R.string.fork_cells_ruler_weak_none)
    }
    return CheckTexts(
        TextRes(R.string.fork_cells_ruler_weak),
        desc,
        if (weak > 0) "$weak" else TextRes(R.string.fork_cells_ruler_weak_no),
        if (weak > 0) TextRes(R.string.fork_cells_ruler_weak_counts, strong, noticeable) else TextRes(R.string.fork_cells_ruler_weak_norm),
    )
}

/** R17: the range and the difference; no sourced norm for it, only the cold rule (thresholds × 2 below 10 °C). */
private fun probeTexts(e: Evaluation): CheckTexts {
    val probes = e.input.probesC.filter { it != NO_TEMP }
    if (probes.isEmpty()) {
        return CheckTexts(TextRes(R.string.fork_cells_ruler_temp), TextRes(R.string.fork_cells_ruler_temp_none), DASH, TextRes(R.string.fork_cells_ruler_temp_none))
    }
    val min = probes.min()
    val max = probes.max()
    return CheckTexts(
        TextRes(R.string.fork_cells_ruler_temp),
        TextRes(R.string.fork_cells_ruler_temp_desc, max - min),
        TextRes(R.string.fork_cells_ruler_temp_range, rangeText(min, max)),
        TextRes(if (min < COLD_LIMIT_C) R.string.fork_cells_ruler_temp_cold else R.string.fork_cells_ruler_temp_warm),
    )
}

private const val COLD_LIMIT_C = 10

/** The four checks two by two, or one under another in a narrow window. */
@Composable
internal fun ChecksGrid(model: RulerModel, e: Evaluation, modifier: Modifier) {
    val perRow = if (LocalCheckNarrow.current) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        model.checks.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { CheckCard(it, e, Modifier.weight(1f)) }
            }
        }
    }
}

/** Badge in the check's level, title and meaning; the value large on the right with what it is held against. */
@Composable
private fun CheckCard(c: RulerCheck, e: Evaluation, modifier: Modifier) {
    val t = checkTexts(c.kind, e, LocalConfiguration.current.locales[0])
    ViewCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            GlyphBadge(glyphOf(c.level), levelColor(c.level), 34.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title.text(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(anyText(t.desc), color = TextMuted, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = tnum())
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(anyText(t.value), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = tnum())
                Text(t.sub.text(), color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.End, maxLines = 2, style = tnum())
            }
        }
    }
}

/** A [TextRes], a [Joined] list («, ») or anything else as it prints. */
@Composable
private fun anyText(a: Any): String = when (a) {
    is TextRes -> a.text()
    is Joined -> a.parts.map { it.text() }.joinToString(", ")
    else -> a.toString()
}
