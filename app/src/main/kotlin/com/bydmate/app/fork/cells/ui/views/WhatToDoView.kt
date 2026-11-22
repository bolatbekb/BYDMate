package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * «Что делать» (EVDiag variant 26): the advice large on top (a third of the height), and three cards under it: when to
 * check again, what counts as normal at which charge, when to go to the service. fork-local.
 */
@Composable
internal fun WhatToDoView(ui: BatteryCheckUi) {
    val e = ui.selected
    val model = remember(e) { whatToDoModel(e) }
    if (LocalCheckNarrow.current) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            AdviceCard(model.advice, e, Modifier.fillMaxWidth().heightIn(min = 150.dp))
            RecheckCard(model, Modifier.fillMaxWidth())
            NormsCard(model, e, Modifier.fillMaxWidth())
            ServiceCard(model, Modifier.fillMaxWidth())
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        AdviceCard(model.advice, e, Modifier.fillMaxWidth().weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().weight(2f)) {
            RecheckCard(model, Modifier.weight(1f).fillMaxHeight())
            NormsCard(model, e, Modifier.weight(1f).fillMaxHeight())
            ServiceCard(model, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

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

/** The advice on a wash of its colour fading into the card: the ringed glyph, the title, what to do and why. */
@Composable
private fun AdviceCard(advice: Advice, e: Evaluation?, modifier: Modifier) {
    val color = adviceColor(advice)
    val why = if (e == null || advice == Advice.NOTHING_TO_DO || advice == Advice.WAIT_RELAX) emptyList() else reasonTexts(e).take(2)
    ViewCard(modifier) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxSize()
                .background(Brush.horizontalGradient(0f to color.copy(alpha = ADVICE_TINT), 0.6f to Color.Transparent))
                .padding(horizontal = 36.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphBadge(adviceGlyph(advice), color, 96.dp, outlined = true)
                Spacer(Modifier.width(32.dp))
                Column {
                    Text(stringResource(adviceTitleRes(advice)), color = color, fontSize = 34.sp, fontWeight = FontWeight.Medium, lineHeight = 40.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(adviceBodyRes(advice)),
                        color = TextSecondary,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (why.isNotEmpty()) {
                        Text(
                            why.map { it.text() }.joinToString(" "),
                            color = TextMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = tnum(),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
