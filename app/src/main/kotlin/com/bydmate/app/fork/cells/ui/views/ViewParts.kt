package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.data.CheckView
import com.bydmate.app.fork.cells.ui.BatteryCheckUi
import com.bydmate.app.fork.cells.ui.LocalCheckNarrow
import com.bydmate.app.fork.cells.ui.levelColor
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.titleRes
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

// What the four views share: the switch, the card, the glyph badge and the verdict card. fork-local.

/** The window's body: the view chosen in the settings («Вид проверки батареи»). */
@Composable
internal fun ChosenView(ui: BatteryCheckUi) {
    when (ui.config.view) {
        CheckView.WHAT_TO_DO -> WhatToDoView(ui)
        CheckView.NORM_BY_CHARGE -> NormByChargeView(ui)
        CheckView.RULER -> RulerView(ui)
        CheckView.EQUALIZATION -> EqualizationView(ui)
    }
}

/** A card of a view: the skin's card on the elevated surface, 14 dp corners. */
@Composable
internal fun ViewCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = modifier,
        content = content,
    )
}

/**
 * Two columns side by side, or one under the other in a narrow window (the window's column scrolls then, so nothing may
 * take a weight of the height). [left] and [right] get their modifier.
 */
@Composable
internal fun TwoColumns(leftWeight: Float, left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    if (LocalCheckNarrow.current) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            left(Modifier.fillMaxWidth())
            right(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            left(Modifier.weight(leftWeight).fillMaxHeight())
            right(Modifier.weight(1f - leftWeight).fillMaxHeight())
        }
    }
}

/** Inside a card of a wide window: scroll when the text does not fit. A narrow window scrolls as a whole already. */
@Composable
internal fun cardScroll(): Modifier = if (LocalCheckNarrow.current) Modifier else Modifier.verticalScroll(rememberScrollState())

enum class Glyph { CHECK, CLOCK, BANG, DASH }

fun glyphOf(l: Level): Glyph = when (l) {
    Level.OK -> Glyph.CHECK
    Level.WATCH, Level.SERVICE -> Glyph.BANG
    Level.NONE -> Glyph.DASH
}

/** A round badge drawn in one Canvas: filled with a dark glyph, or [outlined] (a ring and a glyph in [color]). */
@Composable
internal fun GlyphBadge(glyph: Glyph, color: Color, size: Dp, outlined: Boolean = false) {
    val ink = NavyDark
    Canvas(Modifier.size(size)) { drawGlyphBadge(glyph, color, ink, outlined) }
}

private const val RING_TINT = 0.12f

private fun DrawScope.drawGlyphBadge(glyph: Glyph, color: Color, ink: Color, outlined: Boolean) {
    val r = size.minDimension / 2f
    val c = center
    if (outlined) {
        drawCircle(color.copy(alpha = RING_TINT), r, c)
        drawCircle(color, r - r * 0.03f, c, style = Stroke(r * 0.06f))
    } else {
        drawCircle(color, r, c)
    }
    val tint = if (outlined) color else ink
    val w = r * (if (outlined) 0.13f else 0.17f)
    val p = Path()
    fun pt(x: Float, y: Float) = Offset(c.x + x * r, c.y + y * r)
    when (glyph) {
        Glyph.CHECK -> listOf(pt(-0.42f, 0.02f), pt(-0.12f, 0.32f), pt(0.44f, -0.3f))
        Glyph.CLOCK -> listOf(pt(0f, -0.52f), pt(0f, 0f), pt(0.36f, 0.24f))
        Glyph.BANG -> listOf(pt(0f, -0.48f), pt(0f, 0.1f))
        Glyph.DASH -> listOf(pt(-0.34f, 0f), pt(0.34f, 0f))
    }.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
    drawPath(p, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (glyph == Glyph.BANG) drawCircle(tint, w * 0.62f, pt(0f, 0.42f))
}

/**
 * The verdict of a view: badge, title in the level's colour (plain without a verdict), «предварительно», then what the
 * measurement says in words (the norm against the spread when all is well, else the first reasons).
 */
@Composable
internal fun VerdictSummary(e: Evaluation, modifier: Modifier, titleSize: TextUnit = 22.sp) {
    ViewCard(modifier) {
        Row(Modifier.then(cardScroll()).padding(horizontal = 20.dp, vertical = 18.dp)) {
            GlyphBadge(glyphOf(e.level), levelColor(e.level), 44.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(titleRes(e)),
                    color = if (e.level == Level.NONE) TextPrimary else levelColor(e.level),
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = titleSize * 1.22f,
                )
                if (e.preliminary && e.level != Level.NONE) {
                    Text(stringResource(R.string.fork_cells_level_prelim), color = TextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    summaryTexts(e).forEach { Text(it.text(), color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, style = tnum()) }
                }
            }
        }
    }
}

/** The title of a card and its muted line under it. */
@Composable
internal fun CardHead(title: String, caption: String? = null) {
    Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    if (caption != null) Text(caption, color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
}
