package com.bydmate.app.fork.cells.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.ui.skin.HorizontalDivider
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.tech.DASH
import com.bydmate.app.ui.theme.AccentBlue
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

private val PANEL_WIDTH = 520.dp
private const val SCRIM_ALPHA = 0.5f

/** «Подробно»: a full-height panel on the right over a scrim (full width when narrow); a tap on the scrim closes it. */
@Composable
internal fun DetailsPanel(ui: BatteryCheckUi, actions: BatteryCheckActions, narrow: Boolean) {
    val tab = ui.details ?: return
    val e = ui.selected
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.details(null) },
        )
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                .then(if (narrow) Modifier.fillMaxWidth() else Modifier.width(PANEL_WIDTH))
                .background(CardSurfaceElevated)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            TabsHeader(tab, actions)
            HorizontalDivider(color = CardBorder)
            Box(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 16.dp)) {
                when {
                    tab == DetailsTab.HISTORY -> HistoryTab(ui)
                    tab == DetailsTab.MEASURES -> MeasuresTab(ui, actions)
                    e == null -> Text(stringResource(R.string.fork_cells_empty), color = TextSecondary, fontSize = 15.sp)
                    tab == DetailsTab.DEVIATIONS -> DeviationsTab(e)
                    else -> ProbesTab(e)
                }
            }
        }
    }
}

@Composable
private fun TabsHeader(tab: DetailsTab, actions: BatteryCheckActions) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp)) {
        DetailsTab.entries.forEach { t -> TabLabel(stringResource(tabRes(t)), t == tab) { actions.details(t) } }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { actions.details(null) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.fork_cells_btn_close), tint = TextSecondary)
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = if (selected) TextPrimary else TextMuted,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) AccentGreen else Color.Transparent, RoundedCornerShape(1.dp)))
    }
}

/** Groups of off-normal cells, worst first, each with its count; «№N · 3,273 В · −24 мВ». */
@Composable
private fun DeviationsTab(e: Evaluation) {
    val groups = remember(e) { deviationGroups(e) }
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Legend()
        Spacer(Modifier.height(12.dp))
        if (groups.isEmpty()) {
            Text(stringResource(R.string.fork_cells_dev_none), color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
            groups.forEach { g ->
                item(key = g.cls.name) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Swatch(cellColor(g.cls, e.level, e.limits.cold))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(groupRes(g.cls)), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("  ·  ${g.cells.size}", color = TextMuted, fontSize = 15.sp, style = tnum())
                    }
                }
                items(g.cells, key = { "c$it" }) { cell ->
                    DeviationRow(cell, e.input.cellsMv.getOrElse(cell - 1) { 0 }, e.deviationsMv.getOrElse(cell - 1) { 0.0 }, cellColor(g.cls, e.level, e.limits.cold), locale)
                }
            }
        }
    }
}

@Composable
private fun DeviationRow(cell: Int, mv: Int, dev: Double, color: Color, locale: java.util.Locale) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(CardSurface, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(TextRes(R.string.fork_cells_cell_no, cell).text(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = tnum(), modifier = Modifier.width(72.dp))
        Text(TextRes(R.string.fork_cells_val_cell_v, cellVolts(mv, locale)).text(), color = TextSecondary, fontSize = 15.sp, style = tnum(), modifier = Modifier.weight(1f))
        Text(TextRes(R.string.fork_cells_val_signed_mv, signedMv(dev)).text(), color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = tnum())
    }
}

/** 6 × 4 tiles Д1…Д24: the hottest framed orange, the coldest blue, then what the probes are. */
@Composable
private fun ProbesTab(e: Evaluation) {
    val tiles = remember(e) { probeTiles(e.input.probesC) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(PROBE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ProbeTileBox(it, Modifier.weight(1f)) }
                repeat(PROBE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            tiles.firstOrNull { it.hottest }?.let { ExtremeNote(it, AccentOrange, R.string.fork_cells_probe_hottest) }
            tiles.firstOrNull { it.coldest }?.let { ExtremeNote(it, AccentBlue, R.string.fork_cells_probe_coldest) }
        }
        Text(stringResource(R.string.fork_cells_probes_caption), color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

private const val PROBE_COLUMNS = 6

@Composable
private fun ProbeTileBox(t: ProbeTile, modifier: Modifier) {
    val frame = when {
        t.hottest -> AccentOrange
        t.coldest -> AccentBlue
        else -> null
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.height(68.dp).background(CardSurface, shape)
            .then(if (frame != null) Modifier.border(2.dp, frame, shape) else Modifier),
    ) {
        Text(TextRes(R.string.fork_cells_probe_label, t.index).text(), color = TextMuted, fontSize = 12.sp, style = tnum())
        Text(t.tempC?.let { TextRes(R.string.fork_cells_val_temp, it).text() } ?: DASH, color = TextPrimary, fontSize = 17.sp, style = tnum())
    }
}

/** «▢ самый тёплый · Д1 · 20 °C». */
@Composable
private fun ExtremeNote(t: ProbeTile, color: Color, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).border(2.dp, color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        val temp = t.tempC?.let { TextRes(R.string.fork_cells_val_temp, it).text() } ?: DASH
        Text("${stringResource(label)} · ${TextRes(R.string.fork_cells_probe_label, t.index).text()} · $temp", color = TextSecondary, fontSize = 13.sp, style = tnum())
    }
}

/** The three classes of a cell: grey normal, orange noticeable, red strong. */
@Composable
internal fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            TextMuted to R.string.fork_cells_legend_normal,
            AccentOrange to R.string.fork_cells_legend_noticeable,
            SocRed to R.string.fork_cells_legend_strong,
        ).forEach { (c, res) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(c)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(res), color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Swatch(c: Color) {
    Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
}
