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
import androidx.compose.ui.text.font.FontWeight
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
import com.bydmate.app.ui.tech.DASH
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

// The three cards of «Что делать». Lit rows take the skin's accent; colour otherwise only for the norm and the service. fork-local.

private const val LIT_FILL = 0.16f
private const val LIT_ROW = 0.12f
private const val LIT_BORDER = 0.45f
private const val DIVIDER_ALPHA = 0.5f

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
internal fun RecheckCard(model: WhatToDoModel, modifier: Modifier) {
    ViewCard(modifier) {
        Column(cardScroll().padding(horizontal = 22.dp, vertical = 18.dp)) {
            CardHead(stringResource(R.string.fork_cells_wtd_when))
            Spacer(Modifier.height(8.dp))
            model.recheck.forEachIndexed { i, (r, lit) ->
                if (i > 0) HorizontalDivider(color = CardBorder.copy(alpha = DIVIDER_ALPHA))
                RecheckRow(r, lit)
            }
        }
    }
}

@Composable
private fun RecheckRow(r: Recheck, lit: Boolean) {
    val accent = AccentGreen
    val (title, hint) = recheckRes(r)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp)
                .background(if (lit) accent.copy(alpha = LIT_FILL) else CardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, if (lit) accent else CardBorder, RoundedCornerShape(10.dp)),
        ) {
            Icon(recheckIcon(r), contentDescription = null, tint = if (lit) accent else TextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(TextRes(title).text(), color = if (lit) accent else TextPrimary, fontSize = 15.sp, fontWeight = if (lit) FontWeight.Medium else null, lineHeight = 20.sp)
            Text(TextRes(hint).text(), color = TextMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
}

/** «Что считается нормой»: the five charge bands with their norm and service number, the current one lit, then yours. */
@Composable
internal fun NormsCard(model: WhatToDoModel, e: Evaluation?, modifier: Modifier) {
    ViewCard(modifier) {
        Column(cardScroll().padding(horizontal = 22.dp, vertical = 18.dp)) {
            CardHead(stringResource(R.string.fork_cells_wtd_norm), TextRes(R.string.fork_cells_wtd_norm_sub, socArg(e?.input?.soc)).text())
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { model.norms.forEach { NormLine(it) } }
            model.yoursMv?.let { mv ->
                Text(
                    TextRes(R.string.fork_cells_wtd_yours, mv, TextRes(yoursRes(model.advice, e))).text(),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    style = tnum(),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun NormLine(n: NormRow) {
    val accent = AccentGreen
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(if (n.current) accent.copy(alpha = LIT_ROW) else Color.Transparent, shape)
            .border(1.dp, if (n.current) accent.copy(alpha = LIT_BORDER) else Color.Transparent, shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(TextRes(normLabelRes(n.labelKey)).text(), color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp, style = tnum())
            if (n.current) Text(stringResource(R.string.fork_cells_wtd_now), color = accent, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        // the norm over the service number, right-aligned: the band's name keeps the width
        Column(horizontalAlignment = Alignment.End) {
            Text(TextRes(R.string.fork_cells_wtd_upto, n.normMv).text(), color = ConsumptionGood, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = tnum())
            val svc = n.serviceMv?.let { TextRes(R.string.fork_cells_wtd_service_from, it).text() } ?: DASH
            Text(svc, color = if (n.serviceMv != null) SocRed else TextMuted, fontSize = 12.sp, lineHeight = 16.sp, style = tnum())
        }
    }
}

/** «Когда ехать в сервис»: the three conditions of §6, each with «сейчас — да/нет», and why one is not enough. */
@Composable
internal fun ServiceCard(model: WhatToDoModel, modifier: Modifier) {
    val conditions = listOf(
        R.string.fork_cells_wtd_svc_twice to model.service.twiceOnDifferentDays,
        R.string.fork_cells_wtd_svc_laggard to model.service.laggardRepeats,
        R.string.fork_cells_wtd_svc_half to model.service.cellBelowHalfService,
    )
    ViewCard(modifier) {
        Column(cardScroll().padding(horizontal = 22.dp, vertical = 18.dp)) {
            CardHead(stringResource(R.string.fork_cells_wtd_service))
            Spacer(Modifier.height(6.dp))
            conditions.forEachIndexed { i, (res, met) ->
                if (i > 0) HorizontalDivider(color = CardBorder.copy(alpha = DIVIDER_ALPHA))
                ConditionLine(stringResource(res), met)
            }
            Text(stringResource(R.string.fork_cells_wtd_footnote), color = TextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ConditionLine(text: String, met: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Box(Modifier.padding(top = 5.dp).size(11.dp).background(if (met) SocRed else CardBorder, RoundedCornerShape(50)))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            Text(
                stringResource(if (met) R.string.fork_cells_wtd_yes else R.string.fork_cells_wtd_no),
                color = if (met) SocRed else TextMuted,
                fontSize = 12.5.sp,
                fontWeight = if (met) FontWeight.Medium else null,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
