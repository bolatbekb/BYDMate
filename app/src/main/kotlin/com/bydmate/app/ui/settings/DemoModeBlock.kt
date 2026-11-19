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
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.ui.theme.CardSurfaceElevated

/** Settings › App: DEMO mode switch. Switching restarts the app into the other database. fork-local. */
@Composable
internal fun DemoModeBlock() {
    val context = LocalContext.current
    SectionHeader(text = stringResource(R.string.settings_demo_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SettingToggleRow(
                title = stringResource(R.string.settings_demo_title),
                description = stringResource(R.string.settings_demo_desc),
                checked = DemoMode.isOn(context),
                onCheckedChange = { DemoMode.setAndRestart(context, it) },
            )
        }
    }
}
