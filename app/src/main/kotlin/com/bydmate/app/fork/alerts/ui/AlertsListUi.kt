package com.bydmate.app.fork.alerts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/**
 * The active alerts, embedded in Settings › «Предупреждения» (no overlay, no own scroll — the
 * settings page scrolls). Grouped by level (critical first), each row carries its block, title,
 * value and when it appeared, with the level's colour down the side; empty shows one muted line.
 */
@Composable
fun ActiveAlerts(alerts: List<Alert>, nowMs: Long, modifier: Modifier = Modifier) {
    if (alerts.isEmpty()) {
        Text(
            text = stringResource(R.string.fork_alerts_list_empty),
            color = TextMuted,
            fontSize = 13.sp,
            modifier = modifier.padding(vertical = 4.dp),
        )
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Already sorted critical-first by the center; keep that order, head each level once.
        Severity.entries.reversed().forEach { level ->
            val group = alerts.filter { it.severity == level }
            if (group.isEmpty()) return@forEach
            Text(
                text = severityLabel(level).uppercase(),
                color = severityColor(level),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            group.forEach { AlertRow(it, nowMs) }
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, nowMs: Long) {
    val color = severityColor(alert.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(CardSurfaceElevated, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(10.dp)
                .background(color, CircleShape),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(blockLabel(alert.block).uppercase(), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(sourceTitle(alert.sourceId), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val label = storageLabel(alert)
            Text(
                text = if (label != null) "$label — ${alertValueText(alert)}" else alertValueText(alert),
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
        Text(
            text = whenText(alert.firstSeenMs, nowMs),
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 14.dp),
        )
    }
}
