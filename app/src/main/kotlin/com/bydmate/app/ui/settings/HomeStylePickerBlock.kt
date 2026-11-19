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
import com.bydmate.app.ui.theme.Skin
import com.bydmate.app.ui.theme.SkinState

/**
 * Settings › Appearance: which home screen the first tab shows; «Плеер слева» only with the Graphite
 * home, the one screen it moves. The Graphite look has its own block, [GraphiteLookBlock]. fork-local.
 */
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
            if (mediaLeftShown(HomeStyleState.current)) {
                SettingDivider()
                SettingToggleRow(
                    title = stringResource(R.string.home_gr_set_media_left),
                    description = stringResource(R.string.home_gr_set_media_left_desc),
                    checked = GraphiteLookState.mediaLeft,
                    onCheckedChange = { GraphiteLookState.selectMediaLeft(context, it) },
                )
            }
        }
    }
}

/**
 * Settings › Appearance, below «Цвета приложения»: the Graphite look as a block of its own. It shows
 * with the Graphite home or the «Graphite» app skin, which follows it (owner, 2026-11-19).
 */
@Composable
internal fun GraphiteLookBlock() {
    if (!graphiteLookShown(HomeStyleState.current, SkinState.selected)) return
    // A name: the same in every language.
    SectionHeader(text = "Graphite")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GraphiteLookRows()
        }
    }
}

/** The Graphite look rows show with the Graphite home or the «Graphite» skin, which follows them. */
internal fun graphiteLookShown(home: HomeStyle, skin: Skin): Boolean = home == HomeStyle.GRAPHITE || skin.isGraphite

/**
 * The hint under «Цветовая схема»: the scheme colours the Graphite home and, with the «Graphite»
 * skin, the whole app; with any other skin the app follows «Цвета приложения».
 */
internal fun schemeHint(skin: Skin): Int =
    if (skin.isGraphite) R.string.home_gr_set_scheme_desc_app else R.string.home_gr_set_scheme_desc

/** «Плеер слева» moves the Graphite home's player only, so it shows with that home alone. */
internal fun mediaLeftShown(home: HomeStyle): Boolean = home == HomeStyle.GRAPHITE

/**
 * The Graphite home's own look. The rest of the app keeps the skin from «Цвета приложения»,
 * unless that skin is «Graphite»: then the app follows this look too.
 */
@Composable
private fun GraphiteLookRows() {
    val context = LocalContext.current
    val lang = rememberGrLang()
    val schemes = GrScheme.entries
    val materials = GrMaterial.entries
    val reliefs = GrRelief.entries
    SettingChipRow(
        title = stringResource(R.string.home_gr_set_scheme),
        description = stringResource(schemeHint(SkinState.selected)),
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
}
