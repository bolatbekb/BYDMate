package com.bydmate.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bydmate.app.R
import com.bydmate.app.fork.hud.HudCustomIcons

/** Settings › Display › HUD: switch for the custom HUD icons from Download/bydmate_hud_icons. Fork-local. */
@Composable
internal fun HudCustomIconsSwitch() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(HudCustomIcons.enabled) }
    SettingToggleRow(
        title = stringResource(R.string.settings_hud_custom_icons_title),
        description = stringResource(R.string.settings_hud_custom_icons_desc),
        checked = on,
        onCheckedChange = { on = it; HudCustomIcons.setEnabled(context, it) },
        traceId = "hud_custom_icons",
    )
}
