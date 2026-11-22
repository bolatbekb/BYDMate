package com.bydmate.app.fork.cells.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.tech.DASH
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.ScaledDialogContent
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** Row 2 of the window: the live chips, then «Замер: … ▾» with the measurement picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipsRow(ui: BatteryCheckUi, actions: BatteryCheckActions, narrow: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val h = ui.header
    val chips: @Composable () -> Unit = {
        InfoChip(R.string.fork_cells_chip_soc, h.soc?.let { TextRes(R.string.fork_cells_val_percent, it).text() } ?: DASH)
        InfoChip(R.string.fork_cells_chip_pack, h.packV?.let { TextRes(R.string.fork_cells_val_volt, it).text() } ?: DASH)
        InfoChip(R.string.fork_cells_chip_current, h.currentA?.let { TextRes(R.string.fork_cells_val_amp, decimal(it, 1, locale)).text() } ?: DASH)
        InfoChip(R.string.fork_cells_chip_soh, h.sohPercent?.let { TextRes(R.string.fork_cells_val_percent, it.roundToInt()).text() } ?: DASH)
    }
    val spacing = Arrangement.spacedBy(10.dp)
    if (narrow) {
        FlowRow(horizontalArrangement = spacing, verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        Spacer(Modifier.size(10.dp))
        PickerChip(ui, actions)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(horizontalArrangement = spacing, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) { chips() }
            Spacer(Modifier.width(16.dp))
            PickerChip(ui, actions)
        }
    }
}

/** «Заряд 56 %» in a thin pill; the value stands out. */
@Composable
private fun InfoChip(label: Int, value: String) {
    val full = stringResource(label, value)
    val at = full.lastIndexOf(value)
    val text = buildAnnotatedString {
        append(full)
        if (at >= 0) addStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.SemiBold), at, at + value.length)
    }
    Text(
        text,
        color = TextSecondary,
        fontSize = 14.sp,
        maxLines = 1,
        style = tnum(),
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun PickerChip(ui: BatteryCheckUi, actions: BatteryCheckActions) {
    // the chosen measurement always has its chip, also when it is not among the listed ones
    val items = pickerWithSelection(ui.picker, ui.selected)
    val item = items.firstOrNull { it.id == ui.selectedId } ?: ui.selected?.let { pickerItemOf(it) } ?: return
    var open by remember { mutableStateOf(false) }
    val kind = stringResource(kindRes(item.kind))
    val full = stringResource(R.string.fork_cells_pick, kind, dayMonthTime(item.ts))
    val at = full.indexOf(kind)
    val text = buildAnnotatedString {
        append(full)
        if (at >= 0) addStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.SemiBold), at, at + kind.length)
    }
    val pill = RoundedCornerShape(50)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(pill).border(1.dp, CardBorder, pill).clickable { open = true }
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        ) {
            LevelDot(item.level)
            Spacer(Modifier.width(10.dp))
            Text(text, color = TextSecondary, fontSize = 14.sp, maxLines = 1, style = tnum())
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = CardSurfaceElevated,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.heightIn(max = 560.dp),
        ) {
            ScaledDialogContent {
                Column {
                    PickerMenu(items, item.id) {
                        open = false
                        actions.select(it)
                    }
                }
            }
        }
    }
}

/** Up to 30 measurements in rest and the others, then «Под нагрузкой» with the latest loads. */
@Composable
private fun PickerMenu(items: List<PickerItem>, selectedId: Long, onPick: (Long) -> Unit) {
    val (loads, rest) = items.partition { it.kind == MeasureKind.LOAD }
    rest.forEach { PickerMenuItem(it, it.id == selectedId, onPick) }
    if (loads.isNotEmpty()) {
        Text(
            stringResource(R.string.fork_cells_pick_loads),
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
        )
        loads.forEach { PickerMenuItem(it, it.id == selectedId, onPick) }
    }
}

@Composable
private fun PickerMenuItem(item: PickerItem, selected: Boolean, onPick: (Long) -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelDot(item.level)
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(kindRes(item.kind)),
                    color = if (selected) TextPrimary else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.width(190.dp),
                )
                Text(dayMonthTime(item.ts), color = TextSecondary, fontSize = 15.sp, style = tnum(), modifier = Modifier.width(120.dp))
                Text(
                    TextRes(R.string.fork_cells_val_mv, item.spreadMv).text(),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.End,
                    style = tnum(),
                    modifier = Modifier.width(76.dp),
                )
                Spacer(Modifier.width(18.dp))
                LevelLabel(item.level, item.preliminary, 13.sp, Modifier.width(220.dp))
            }
        },
        onClick = { onPick(item.id) },
        modifier = if (selected) Modifier.background(CardSurface) else Modifier,
    )
}

/** A filled dot in the level's colour; a thin ring while the level is not known yet. */
@Composable
internal fun LevelDot(level: Level?, size: Dp = 10.dp) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.size(size).then(
            if (level == null) Modifier.border(1.dp, TextMuted, shape) else Modifier.background(levelColor(level), shape),
        ),
    )
}

/** «всё отлично», «стоит последить · предварительно», «без оценки»; nothing while unknown. */
@Composable
internal fun LevelLabel(level: Level?, preliminary: Boolean, size: TextUnit, modifier: Modifier = Modifier) {
    if (level == null) {
        Spacer(modifier)
        return
    }
    val base = stringResource(levelRes(level))
    val text = if (preliminary && level != Level.NONE) "$base · ${stringResource(R.string.fork_cells_level_prelim)}" else base
    Text(text, color = if (level == Level.NONE) TextMuted else levelColor(level), fontSize = size, maxLines = 1, modifier = modifier)
}
