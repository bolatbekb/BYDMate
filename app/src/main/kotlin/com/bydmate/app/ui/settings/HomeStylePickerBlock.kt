package com.bydmate.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bydmate.app.R
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.home.HomeStyle
import com.bydmate.app.ui.home.HomeStyleState
import com.bydmate.app.ui.home.graphite.GraphiteLookState
import com.bydmate.app.ui.home.graphite.GrMaterial
import com.bydmate.app.ui.home.graphite.GrFont
import com.bydmate.app.ui.home.graphite.GrRelief
import com.bydmate.app.ui.home.graphite.GrScheme
import com.bydmate.app.ui.home.graphite.rememberGrLang
import com.bydmate.app.ui.theme.CardSurfaceElevated

/** Settings › App: which home screen the first tab shows, and the Graphite home's look. fork-local. */
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
            if (HomeStyleState.current == HomeStyle.GRAPHITE) GraphiteLookRows()
        }
    }
}

/** The Graphite home's own look; the rest of the app keeps the skin from «Цвета приложения». */
@Composable
private fun GraphiteLookRows() {
    val context = LocalContext.current
    val lang = rememberGrLang()
    val schemes = GrScheme.entries
    val materials = GrMaterial.entries
    val reliefs = GrRelief.entries
    SettingDivider()
    SettingSubhead("Graphite")
    SettingChipRow(
        title = stringResource(R.string.home_gr_set_scheme),
        description = stringResource(R.string.home_gr_set_scheme_desc),
        // Scheme names are names: the same in every language.
        options = schemes.map { it.title },
        selectedIndex = schemes.indexOf(GraphiteLookState.scheme),
        onSelect = { GraphiteLookState.selectScheme(context, schemes[it]) },
    )
    SettingChipRow(
        title = stringResource(R.string.home_gr_set_font),
        // Font names are names: the same in every language.
        options = GrFont.entries.map { it.title },
        selectedIndex = GrFont.entries.indexOf(GraphiteLookState.font),
        onSelect = { GraphiteLookState.selectFont(context, GrFont.entries[it]) },
    )
    SettingChipRow(
        title = stringResource(R.string.home_gr_set_material),
        description = stringResource(R.string.home_gr_set_material_desc),
        options = materials.map { it.title(lang) },
        selectedIndex = materials.indexOf(GraphiteLookState.material),
        onSelect = { GraphiteLookState.selectMaterial(context, materials[it]) },
    )
    SettingChipRow(
        title = stringResource(R.string.home_gr_set_relief),
        options = reliefs.map { it.title(lang) },
        selectedIndex = reliefs.indexOf(GraphiteLookState.relief),
        onSelect = { GraphiteLookState.selectRelief(context, reliefs[it]) },
    )
    SettingToggleRow(
        title = stringResource(R.string.home_gr_set_edges),
        description = stringResource(R.string.home_gr_set_edges_desc),
        checked = GraphiteLookState.edges25d,
        onCheckedChange = { GraphiteLookState.selectEdges25d(context, it) },
    )
    SettingToggleRow(
        title = stringResource(R.string.home_gr_set_media_left),
        description = stringResource(R.string.home_gr_set_media_left_desc),
        checked = GraphiteLookState.mediaLeft,
        onCheckedChange = { GraphiteLookState.selectMediaLeft(context, it) },
    )
}
