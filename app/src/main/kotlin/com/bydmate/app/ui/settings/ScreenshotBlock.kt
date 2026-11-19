package com.bydmate.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bydmate.app.R
import com.bydmate.app.fork.ScreenshotTaker
import com.bydmate.app.ui.theme.CardSurfaceElevated

/** Settings › App: whole-screen screenshot through ADB. fork-local. */
@Composable
internal fun ScreenshotBlock() {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            SettingActionRow(
                title = stringResource(R.string.settings_screenshot_title),
                description = stringResource(R.string.settings_screenshot_desc, ScreenshotTaker.DELAY_S),
                buttonLabel = stringResource(R.string.settings_screenshot_button),
                onClick = { ScreenshotTaker.shoot(context) },
            )
        }
    }
}
