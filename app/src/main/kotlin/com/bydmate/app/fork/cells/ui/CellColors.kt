package com.bydmate.app.fork.cells.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.bydmate.app.fork.cells.collect.ManualResult
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextSecondary

// Colours of the battery check: colour only where it means something — green ok, orange watch,
// red service, everything else stays in the skin's text greys. The getters read the active skin,
// so a call in composition follows a skin switch. fork-local.

fun levelColor(l: Level?): Color = when (l) {
    Level.OK -> ConsumptionGood
    Level.WATCH -> AccentOrange
    Level.SERVICE -> SocRed
    Level.NONE, null -> TextMuted
}

/** Normal cells stay grey; noticeable ones orange, strong ones red. */
fun classColor(c: CellClass): Color = when (c) {
    CellClass.NORMAL -> TextMuted
    CellClass.NOTICEABLY_LOW, CellClass.NOTICEABLY_HIGH -> AccentOrange
    CellClass.STRONGLY_LOW, CellClass.STRONGLY_HIGH -> SocRed
}

/** The manual line: taking is neutral, good conditions green, rough ones orange, a failure red. */
fun manualColor(m: ManualUi): Color = when (m) {
    ManualUi.Idle, ManualUi.Taking -> TextSecondary
    is ManualUi.Done -> when (val r = m.r) {
        is ManualResult.Stored -> if (r.conditionsOk || r.problems.isEmpty()) ConsumptionGood else AccentOrange
        is ManualResult.Failed -> SocRed
    }
}

private val TABULAR = TextStyle(fontFeatureSettings = "tnum")

/** The current text style with tabular digits, so numbers in rows and chips stay in their columns. */
@Composable
@ReadOnlyComposable
fun tnum(): TextStyle = LocalTextStyle.current.merge(TABULAR)
