package com.bydmate.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.SkinSwitchFace
import com.bydmate.app.ui.skin.family
import com.bydmate.app.ui.skin.shapeFor
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.Skin
import com.bydmate.app.ui.theme.SkinState
import com.bydmate.app.ui.theme.Skins
import com.bydmate.app.ui.theme.TextSecondary
import com.bydmate.app.ui.theme.live
import kotlinx.coroutines.flow.first

/** Settings › App: a row of skin previews; a tap switches the whole app live. fork-local. */
@Composable
internal fun SkinPickerBlock() {
    val context = LocalContext.current
    val current = SkinState.current
    SectionHeader(text = stringResource(R.string.settings_skin_title))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_skin_desc), color = TextSecondary, fontSize = 13.sp)
            val rowState = rememberLazyListState()
            // The selected tile (the «Graphite» one is last) is scrolled into view when the row opens.
            LaunchedEffect(Unit) {
                snapshotFlow { rowState.layoutInfo }.first { it.totalItemsCount > 0 }
                val idx = Skins.ALL.indexOfFirst { it.id == SkinState.selected.id }
                if (!tileFullyVisible(rowState.layoutInfo, idx)) rowState.scrollToItem(idx)
            }
            LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Skins.ALL, key = { it.id }) { skin ->
                    // live: the «Graphite» tile shows the Graphite home's scheme and font of the moment
                    SkinPreview(skin.live(), selected = skin.id == current.id) { SkinState.select(context, skin) }
                }
            }
        }
    }
}

/** Whether tile [idx] lies wholly inside the row's viewport. No tile (-1) counts as visible: nothing to scroll to. */
internal fun tileFullyVisible(info: LazyListLayoutInfo, idx: Int): Boolean {
    val tile = info.visibleItemsInfo.firstOrNull { it.index == idx } ?: return idx < 0
    return tile.offset >= info.viewportStartOffset && tile.offset + tile.size <= info.viewportEndOffset
}

/**
 * A tiny mock screen in the skin's own colors, font, corners and switch, so each tile shows itself,
 * not the active skin. The tile keeps its size: the sample card is the corner sample.
 */
@Composable
private fun SkinPreview(skin: Skin, selected: Boolean, onClick: () -> Unit) {
    val p = skin.palette
    val kit = skin.kit
    val font = kit.fonts.text.family() ?: FontFamily.Default
    val shape = shapeFor(kit.corners, 12.dp)
    val sampleShape = shapeFor(kit.corners, 8.dp)
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(shape)
            .background(p.navyDark)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.accentGreen else p.cardBorder, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(34.dp).clip(sampleShape)
                .background(p.cardSurface).border(1.dp, p.cardBorder, sampleShape)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Аа 123", color = p.textPrimary, fontSize = 13.sp, fontFamily = font)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // the switch is 52 x 32 dp; drawn at half size in a 26 x 16 slot
            Box(Modifier.size(width = 26.dp, height = 16.dp), contentAlignment = Alignment.Center) {
                SkinSwitchFace(kit.controls.switch, p, checked = true, modifier = Modifier.scale(0.5f))
            }
            listOf(p.accentGreen, p.accentBlue, p.accentOrange, p.accentTeal).forEach {
                Box(Modifier.size(14.dp).clip(CircleShape).background(it))
            }
        }
        Text(
            skin.title,
            color = if (selected) p.accentGreen else p.textPrimary,
            fontSize = 13.sp,
            fontFamily = font,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
