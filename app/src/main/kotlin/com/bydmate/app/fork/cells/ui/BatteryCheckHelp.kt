package com.bydmate.app.fork.cells.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.bydmate.app.R
import com.bydmate.app.fork.cells.verdict.THRESHOLD_ROWS
import com.bydmate.app.fork.cells.verdict.ThresholdRow
import com.bydmate.app.fork.cells.verdict.ZoneRow
import com.bydmate.app.ui.skin.AlertDialog
import com.bydmate.app.ui.skin.HorizontalDivider
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.skin.TextButton
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.ScaledDialogContent
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/** Column weights: condition, zone, norm, watch, service, origin. */
private val WEIGHTS = floatArrayOf(0.19f, 0.16f, 0.10f, 0.11f, 0.23f, 0.21f)

/** Rows whose «в сервис» cell is not a service rule (no red there). */
private val NO_SERVICE_KEYS = setOf("top_knee", "bms_flag")

private val RULES = listOf(
    R.string.fork_cells_help_rule_short,
    R.string.fork_cells_help_rule_prelim,
    R.string.fork_cells_help_rule_cold,
    R.string.fork_cells_help_rule_once,
    R.string.fork_cells_help_rule_median,
)

/**
 * «?»: every threshold the app uses, with where it comes from, the «no verdict» rules and why the
 * median; the zone of the chosen measurement is lit. fork-local.
 */
@Composable
internal fun BatteryCheckHelp(current: ZoneRow?, onDismiss: () -> Unit) {
    val conf = LocalConfiguration.current
    val width = (conf.screenWidthDp - 64).coerceIn(320, 1180).dp
    val height = (conf.screenHeightDp - 48).coerceAtLeast(320).dp
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ScaledDialogContent {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.fork_cells_btn_close), fontSize = 15.sp) }
            }
        },
        title = {
            ScaledDialogContent {
                Text(stringResource(R.string.fork_cells_help_title), color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        text = { ScaledDialogContent { HelpBody(current) } },
        containerColor = CardSurface,
        modifier = Modifier.width(width).heightIn(max = height),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

@Composable
private fun HelpBody(current: ZoneRow?) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.fork_cells_help_intro), color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(16.dp))
        HelpHeader()
        HorizontalDivider(color = CardBorder)
        THRESHOLD_ROWS.forEach { row ->
            HelpTableRow(row, row.row != null && row.row == current)
            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RULES.forEach { res ->
                Row {
                    Text("•", color = TextMuted, fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(res), color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun HelpHeader() {
    val heads = listOf(
        R.string.fork_cells_help_col_condition, R.string.fork_cells_help_col_zone, R.string.fork_cells_help_col_norm,
        R.string.fork_cells_help_col_watch, R.string.fork_cells_help_col_service, R.string.fork_cells_help_col_origin,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        heads.forEachIndexed { i, res -> HelpCell(stringResource(res), WEIGHTS[i], TextSecondary, 13.sp, FontWeight.Medium) }
    }
}

@Composable
private fun HelpTableRow(row: ThresholdRow, lit: Boolean) {
    val res = helpRow(row.conditionKey) ?: return
    val muted = row.row == null
    val zone = row.row?.let { zoneRes(it) } ?: R.string.fork_cells_help_row_bms_flag_zone
    val origins = row.origins.map { stringResource(originRes(it)) }.joinToString(" · ")
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
            .background(if (lit) CardSurfaceElevated else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        HelpCell(stringResource(res.cond), WEIGHTS[0], TextPrimary)
        HelpCell(stringResource(zone), WEIGHTS[1], TextSecondary)
        HelpCell(stringResource(res.norm), WEIGHTS[2], if (muted) TextMuted else ConsumptionGood)
        HelpCell(stringResource(res.watch), WEIGHTS[3], if (muted) TextMuted else AccentOrange)
        HelpCell(stringResource(res.service), WEIGHTS[4], if (row.conditionKey in NO_SERVICE_KEYS) TextSecondary else SocRed)
        HelpCell(origins, WEIGHTS[5], TextMuted, 13.sp)
    }
}

@Composable
private fun RowScope.HelpCell(text: String, weight: Float, color: Color, size: TextUnit = 14.sp, fw: FontWeight? = null) {
    Text(text, color = color, fontSize = size, fontWeight = fw, lineHeight = 20.sp, style = tnum(), modifier = Modifier.weight(weight))
}
