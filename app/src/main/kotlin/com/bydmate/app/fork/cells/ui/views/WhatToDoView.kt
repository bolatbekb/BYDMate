package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.fork.cells.ui.BatteryCheckUi
import com.bydmate.app.fork.cells.ui.LocalCheckNarrow
import com.bydmate.app.fork.cells.ui.reasonTexts
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentBlue
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextSecondary

private const val ADVICE_TINT = 0.10f

private val ROOMY_HEIGHT = 390.dp

/**
 * «Что делать» (EVDiag variant 26): the advice on top, as tall as its text, and three cards under it: when to check again,
 * what counts as normal at which charge, when to go to the service. Every row shows at the car's smallest screen. fork-local.
 */
@Composable
internal fun WhatToDoView(ui: BatteryCheckUi) {
    val e = ui.selected
    val model = remember(e) { whatToDoModel(e) }
    if (LocalCheckNarrow.current) {
        val size = WtdSize(compact = false)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AdviceCard(model.advice, e, size, Modifier.fillMaxWidth())
            RecheckCard(model, size, Modifier.fillMaxWidth())
            NormsCard(model, e, size, Modifier.fillMaxWidth())
            ServiceCard(model, size, Modifier.fillMaxWidth())
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = WtdSize(compact = maxHeight < ROOMY_HEIGHT)
        Column(verticalArrangement = Arrangement.spacedBy(size.gap), modifier = Modifier.fillMaxSize()) {
            AdviceCard(model.advice, e, size, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                RecheckCard(model, size, Modifier.weight(RECHECK_WEIGHT).fillMaxHeight())
                NormsCard(model, e, size, Modifier.weight(1f).fillMaxHeight())
                ServiceCard(model, size, Modifier.weight(SERVICE_WEIGHT).fillMaxHeight())
            }
        }
    }
}

/** The recheck rows are the longest lines, the service card the shortest. */
private const val RECHECK_WEIGHT = 1.2f
private const val SERVICE_WEIGHT = 0.9f

/** Green when nothing is to be done, orange / red with the level, blue while the pack settles, grey without a verdict. */
internal fun adviceColor(a: Advice): Color = when (a) {
    Advice.NOTHING_TO_DO, Advice.PRELIMINARY -> ConsumptionGood
    Advice.WATCH -> AccentOrange
    Advice.SERVICE -> SocRed
    Advice.WAIT_RELAX -> AccentBlue
    Advice.NO_VERDICT -> TextMuted
}

private fun adviceGlyph(a: Advice): Glyph = when (a) {
    Advice.NOTHING_TO_DO, Advice.PRELIMINARY -> Glyph.CHECK
    Advice.WAIT_RELAX -> Glyph.CLOCK
    else -> Glyph.BANG
}

/** The advice on a wash of its colour fading into the card: the ringed glyph, the title, then what to do and why (muted). */
@Composable
private fun AdviceCard(advice: Advice, e: Evaluation?, size: WtdSize, modifier: Modifier) {
    val color = adviceColor(advice)
    val why = if (e == null || advice == Advice.NOTHING_TO_DO || advice == Advice.WAIT_RELAX) null else reasonTexts(e).firstOrNull()?.text()
    val muted = TextMuted
    val body = buildAnnotatedString {
        append(stringResource(adviceBodyRes(advice)))
        if (why != null) withStyle(SpanStyle(color = muted)) { append("  $why") }
    }
    ViewCard(modifier) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(0f to color.copy(alpha = ADVICE_TINT), 0.6f to Color.Transparent))
                .padding(horizontal = 22.dp, vertical = size.heroPad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphBadge(adviceGlyph(advice), color, size.heroIcon, outlined = true)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(stringResource(adviceTitleRes(advice)), color = color, fontSize = size.heroTitle, fontWeight = FontWeight.Medium, lineHeight = size.heroTitleLine)
                    Text(body, color = TextSecondary, fontSize = size.heroBody, lineHeight = size.heroBodyLine, style = tnum(), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
