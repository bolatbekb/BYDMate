package com.bydmate.app.fork.alerts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import androidx.compose.ui.res.stringResource

/**
 * The active-alerts list as an overlay over «Машина». Grouped by level (critical first), each row
 * carries its block, title, value and when it appeared, with the level's colour down the side.
 */
@Composable
fun AlertsList(alerts: List<Alert>, nowMs: Long, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0A1420))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            modifier = Modifier
                .fillMaxWidth(WIDTH_FRACTION)
                .fillMaxHeight(HEIGHT_FRACTION)
                // Swallow taps inside the card so the scrim's dismiss does not fire.
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Header(onClose)
                Spacer(Modifier.size(12.dp))
                if (alerts.isEmpty()) {
                    EmptyState()
                } else {
                    AlertGroups(alerts, nowMs)
                }
            }
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.fork_alerts_list_title),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.fork_alerts_close), tint = TextSecondary)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.fork_alerts_list_empty), color = TextMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AlertGroups(alerts: List<Alert>, nowMs: Long) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(blockLabel(alert.block).uppercase(), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
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

private const val WIDTH_FRACTION = 0.72f
private const val HEIGHT_FRACTION = 0.82f
