package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.ui.skin.HorizontalDivider
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

// The three cards of «Что делать»: six recheck rows, five norm rows and three conditions show without scrolling also on
// the car's smallest screen (see WtdSize). Lit rows take the skin's accent; colour otherwise only for the norm and the service. fork-local.

private const val LIT_FILL = 0.16f
private const val LIT_ROW = 0.12f
private const val LIT_BORDER = 0.45f
private const val DIVIDER_ALPHA = 0.5f
private const val ICON_SHARE = 0.62f

/** The padding of the three cards: tight at the top and bottom, so the rows keep their height. */
private fun inside(size: WtdSize): Modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = size.cardTop, bottom = size.cardBottom)

private fun recheckIcon(r: Recheck): ImageVector = when (r) {
    Recheck.AFTER_CHARGE_HOUR -> Icons.Outlined.Timer
    Recheck.AFTER_PARKING_HOUR -> Icons.Outlined.LocalParking
    Recheck.ON_PLATEAU -> Icons.Outlined.Battery5Bar
    Recheck.AFTER_FULL_AC -> Icons.Outlined.BatteryChargingFull
    Recheck.WARM -> Icons.Outlined.Thermostat
    Recheck.MONTHLY -> Icons.Outlined.CalendarMonth
}

/** «Когда перепроверить»: six rows, the ones the measurement points at lit. */
@Composable
internal fun RecheckCard(model: WhatToDoModel, size: WtdSize, modifier: Modifier) {
    ViewCard(modifier) {
        Column(cardScroll().then(inside(size))) {
            CardHead(stringResource(R.string.fork_cells_wtd_when))
            Spacer(Modifier.height(4.dp))
            model.recheck.forEach { (r, lit) -> RecheckRow(r, lit, size) }
        }
    }
}

/** An icon square, then «title — hint» in one line (the hint muted); a long one wraps to a second line. */
@Composable
private fun RecheckRow(r: Recheck, lit: Boolean, size: WtdSize) {
    val accent = AccentGreen
    val muted = TextMuted
    val (title, hint) = recheckRes(r)
    val shape = RoundedCornerShape(7.dp)
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = if (lit) accent else TextPrimary, fontWeight = if (lit) FontWeight.SemiBold else null)) { append(TextRes(title).text()) }
        withStyle(SpanStyle(color = muted, fontSize = 12.sp)) { append(" — " + TextRes(hint).text()) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = size.rowPad)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size.icon)
                .background(if (lit) accent.copy(alpha = LIT_FILL) else CardSurface, shape)
                .border(1.dp, if (lit) accent else CardBorder, shape),
        ) {
            Icon(recheckIcon(r), contentDescription = null, tint = if (lit) accent else TextSecondary, modifier = Modifier.size(size.icon * ICON_SHARE))
        }
        Spacer(Modifier.width(9.dp))
        Text(line, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * «Что считается нормой · заряд 76 %» on one line, the five charge bands with their norm and service number, the current one
 * lit, then yours (with «холод: пороги × 2» when the current row carries the doubled numbers).
 */
@Composable
internal fun NormsCard(model: WhatToDoModel, e: Evaluation?, size: WtdSize, modifier: Modifier) {
    val muted = TextMuted
    val head = buildAnnotatedString {
        append(stringResource(R.string.fork_cells_wtd_norm))
        withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal, fontSize = 12.5.sp)) {
            append(" · " + TextRes(R.string.fork_cells_wtd_norm_charge, socArg(e?.input?.soc)).text())
        }
    }
    val cold = if (model.cold) " · " + stringResource(R.string.fork_cells_norm_cold_note) else ""
    ViewCard(modifier) {
        Column(cardScroll().then(inside(size))) {
            Text(head, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = tnum())
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) { model.norms.forEach { NormLine(it, size) } }
            model.yoursMv?.let { mv ->
                Text(
                    TextRes(R.string.fork_cells_wtd_yours, mv, TextRes(yoursRes(model.advice, e))).text() + cold,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    style = tnum(),
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

/** The band on the left («← сейчас» after it when current), «≤ N мВ · в сервис от M» on one line on the right. */
@Composable
private fun NormLine(n: NormRow, size: WtdSize) {
    val accent = AccentGreen
    val shape = RoundedCornerShape(8.dp)
    val label = buildAnnotatedString {
        append(TextRes(normLabelRes(n.labelKey)).text())
        if (n.current) withStyle(SpanStyle(color = accent, fontSize = 12.sp)) { append("  " + stringResource(R.string.fork_cells_wtd_now)) }
    }
    val numbers = buildAnnotatedString {
        withStyle(SpanStyle(color = ConsumptionGood, fontWeight = FontWeight.SemiBold)) { append(TextRes(R.string.fork_cells_wtd_upto, n.normMv).text()) }
        n.serviceMv?.let { svc ->
            withStyle(SpanStyle(color = TextMuted)) { append(" · ") }
            withStyle(SpanStyle(color = SocRed)) { append(TextRes(R.string.fork_cells_wtd_service_from, svc).text()) }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(if (n.current) accent.copy(alpha = LIT_ROW) else Color.Transparent, shape)
            .border(1.dp, if (n.current) accent.copy(alpha = LIT_BORDER) else Color.Transparent, shape)
            .padding(horizontal = 10.dp, vertical = size.normPad),
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, lineHeight = 16.sp, style = tnum(), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            numbers,
            color = TextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            softWrap = false,
            style = tnum(),
        )
    }
}

/**
 * «Когда ехать в сервис»: the three conditions of §6 with «сейчас — да/нет» after each; a met condition is red only under a
 * «в сервис» verdict (orange otherwise: one condition is not a service), neutral without a verdict; then, when there is room,
 * why one is not enough (always in «?»).
 */
@Composable
internal fun ServiceCard(model: WhatToDoModel, size: WtdSize, modifier: Modifier) {
    val texts = listOf(R.string.fork_cells_wtd_svc_twice, R.string.fork_cells_wtd_svc_laggard, R.string.fork_cells_wtd_svc_half)
    ViewCard(modifier) {
        Column(cardScroll().then(inside(size))) {
            CardHead(stringResource(R.string.fork_cells_wtd_service))
            Spacer(Modifier.height(2.dp))
            texts.forEachIndexed { i, res ->
                if (i > 0) HorizontalDivider(color = CardBorder.copy(alpha = DIVIDER_ALPHA))
                ConditionLine(stringResource(res), model.marks.getOrElse(i) { ConditionMark.UNKNOWN }, size)
            }
            if (size.footnote) {
                Text(stringResource(R.string.fork_cells_wtd_footnote), color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun markColor(m: ConditionMark): Color = when (m) {
    ConditionMark.MET_SERVICE -> SocRed
    ConditionMark.MET -> AccentOrange
    ConditionMark.NO, ConditionMark.UNKNOWN -> CardBorder
}

@Composable
private fun ConditionLine(text: String, mark: ConditionMark, size: WtdSize) {
    val status = when (mark) {
        ConditionMark.UNKNOWN -> R.string.fork_cells_wtd_unknown
        ConditionMark.NO -> R.string.fork_cells_wtd_no
        ConditionMark.MET, ConditionMark.MET_SERVICE -> R.string.fork_cells_wtd_yes
    }
    val met = mark == ConditionMark.MET || mark == ConditionMark.MET_SERVICE
    val line = buildAnnotatedString {
        append(text)
        withStyle(SpanStyle(color = if (met) markColor(mark) else TextMuted, fontWeight = if (met) FontWeight.SemiBold else null, fontSize = 12.sp)) {
            append("  " + stringResource(status))
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = size.conditionPad)) {
        Box(Modifier.padding(top = 4.dp).size(9.dp).background(markColor(mark), RoundedCornerShape(50)))
        Spacer(Modifier.width(10.dp))
        Text(line, color = TextPrimary, fontSize = 12.sp, lineHeight = 15.sp)
    }
}
