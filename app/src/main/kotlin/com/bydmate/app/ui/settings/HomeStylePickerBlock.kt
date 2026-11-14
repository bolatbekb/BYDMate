package com.bydmate.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import com.bydmate.app.ui.home.HomeStyle
import com.bydmate.app.ui.home.HomeStyleState
import com.bydmate.app.ui.theme.CardSurfaceElevated

/** Settings › App: which home screen the first tab shows. Fork-local. */
@Composable
internal fun HomeStylePickerBlock() {
    val context = LocalContext.current
    val styles = HomeStyle.entries
    SectionHeader(text = stringResource(R.string.settings_home_title))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingChipRow(
                title = stringResource(R.string.settings_home_title),
                description = stringResource(R.string.settings_home_desc),
                options = styles.map { it.title },
                selectedIndex = styles.indexOf(HomeStyleState.current),
                onSelect = { idx -> HomeStyleState.select(context, styles[idx]) },
            )
        }
    }
}
