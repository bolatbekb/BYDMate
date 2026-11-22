package com.bydmate.app.fork.cells.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bydmate.app.R
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.ui.skin.Button
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.OutlinedButton
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.ScaledDialogContent
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/** Below this width (a split screen) the window is one scrolling column. */
internal val NARROW_WIDTH = 900.dp

/** True inside a narrow window: views draw a smaller grid and take no weight (the column scrolls). */
internal val LocalCheckNarrow = staticCompositionLocalOf { false }

/**
 * «Проверка батареи»: header, chips with the picker, one manual line, the stream line, the body
 * ([body] draws the chosen view), the footer with the thresholds help, and «Подробно» over it all.
 * Full screen with a 16 dp margin. fork-local.
 */
@Composable
internal fun BatteryCheckWindow(ui: BatteryCheckUi, actions: BatteryCheckActions, body: @Composable (BatteryCheckUi) -> Unit) {
    Dialog(onDismissRequest = actions.dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ScaledDialogContent {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val narrow = maxWidth < NARROW_WIDTH
                    CompositionLocalProvider(LocalCheckNarrow provides narrow) { WindowColumn(ui, actions, narrow, body) }
                    DetailsPanel(ui, actions, narrow)
                }
            }
            if (ui.helpOpen) BatteryCheckHelp(ui.selected?.row) { actions.help(false) }
        }
    }
}

@Composable
private fun WindowColumn(ui: BatteryCheckUi, actions: BatteryCheckActions, narrow: Boolean, body: @Composable (BatteryCheckUi) -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .then(if (narrow) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = if (narrow) 18.dp else 28.dp, vertical = 14.dp),
    ) {
        TopRow(ui, actions, narrow)
        Spacer(Modifier.height(10.dp))
        ChipsRow(ui, actions, narrow)
        Notices(ui)
        Spacer(Modifier.height(16.dp))
        Box(if (narrow) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f)) { Body(ui, actions, body) }
        Spacer(Modifier.height(14.dp))
        Footer(ui, actions)
    }
}

@Composable
private fun TopRow(ui: BatteryCheckUi, actions: BatteryCheckActions, narrow: Boolean) {
    val status = collectText(ui.config).text()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = actions.dismiss, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.fork_cells_btn_close),
                tint = TextSecondary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.fork_cells_title), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (!narrow) {
            Spacer(Modifier.width(18.dp))
            Text(status, color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.weight(1f))
        if (!narrow) Actions(ui, actions)
    }
    if (narrow) {
        Text(status, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 54.dp))
        Row(Modifier.padding(top = 10.dp)) { Actions(ui, actions) }
    }
}

@Composable
private fun Actions(ui: BatteryCheckUi, actions: BatteryCheckActions) {
    Button(onClick = actions.takeManual, enabled = ui.manual != ManualUi.Taking, modifier = Modifier.height(48.dp)) {
        Text(stringResource(R.string.fork_cells_btn_measure), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.width(12.dp))
    OutlinedButton(onClick = { actions.details(DetailsTab.DEVIATIONS) }, enabled = ui.selected != null, modifier = Modifier.height(48.dp)) {
        Text(stringResource(R.string.fork_cells_btn_details), fontSize = 15.sp, color = if (ui.selected != null) TextPrimary else TextMuted)
    }
}

/** The manual result line and the stream line, each only when it has something to say. */
@Composable
private fun Notices(ui: BatteryCheckUi) {
    manualText(ui.manual)?.let {
        Text(it.text(), color = manualColor(ui.manual), fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
    }
    val stream = streamText(ui.stream, ui.framesSeen) ?: return
    if (ui.stream.state == StreamState.CONNECTING) {
        Text(stream.text(), color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
            .background(CardSurfaceElevated, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.width(4.dp).height(22.dp).background(AccentOrange, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(14.dp))
        Text(stream.text(), color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** The chosen view, or why there is none yet: collection off (with «Включить сбор»), no measurements. */
@Composable
private fun Body(ui: BatteryCheckUi, actions: BatteryCheckActions, body: @Composable (BatteryCheckUi) -> Unit) {
    when {
        ui.loading -> Unit
        ui.picker.isEmpty() && !ui.config.collect -> Banner(stringResource(R.string.fork_cells_off_text)) {
            Button(onClick = actions.enableCollection, modifier = Modifier.padding(top = 20.dp).height(48.dp)) {
                Text(stringResource(R.string.fork_cells_btn_enable), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        ui.picker.isEmpty() -> Banner(stringResource(R.string.fork_cells_empty))
        ui.selected == null -> Banner(stringResource(R.string.fork_cells_title_none))
        else -> body(ui)
    }
}

@Composable
private fun Banner(text: String, action: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 680.dp)) {
            Text(text, color = TextSecondary, fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
            action()
        }
    }
}

/** The measuring conditions and the zone's rule, and the round «?» with the thresholds. */
@Composable
private fun Footer(ui: BatteryCheckUi, actions: BatteryCheckActions) {
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
        val e = ui.selected
        Text(
            e?.let { footerText(it, locale).text() }.orEmpty(),
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            style = tnum(),
            modifier = Modifier.weight(1f),
        )
        HelpButton { actions.help(true) }
    }
}

@Composable
private fun HelpButton(onClick: () -> Unit) {
    val label = stringResource(R.string.fork_cells_btn_help)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp)
            .clip(RoundedCornerShape(50))
            .background(CardSurfaceElevated)
            .border(1.dp, CardBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text("?", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** [TextRes] in the current configuration's language. */
@Composable
internal fun TextRes.text(): String {
    LocalConfiguration.current
    return resolve(this, LocalContext.current.resources)
}
