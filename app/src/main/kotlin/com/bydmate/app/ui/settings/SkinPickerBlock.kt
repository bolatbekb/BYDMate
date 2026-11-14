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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.Skin
import com.bydmate.app.ui.theme.SkinState
import com.bydmate.app.ui.theme.Skins
import com.bydmate.app.ui.theme.TextSecondary

/** Settings › App: a row of skin previews; a tap switches the whole app live. Fork-local. */
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Skins.ALL, key = { it.id }) { skin ->
                    SkinPreview(skin, selected = skin.id == current.id) { SkinState.select(context, skin) }
                }
            }
        }
    }
}

/** A tiny mock screen drawn in the skin's own colors, so each tile shows itself, not the active skin. */
@Composable
private fun SkinPreview(skin: Skin, selected: Boolean, onClick: () -> Unit) {
    val p = skin.palette
    val shape = RoundedCornerShape(12.dp)
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
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp))
                .background(p.cardSurface).border(1.dp, p.cardBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Box(Modifier.width(48.dp).height(6.dp).clip(CircleShape).background(p.textPrimary))
            Box(Modifier.padding(top = 12.dp).width(30.dp).height(5.dp).clip(CircleShape).background(p.textSecondary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(p.accentGreen, p.accentBlue, p.accentOrange, p.accentTeal).forEach {
                Box(Modifier.size(14.dp).clip(CircleShape).background(it))
            }
        }
        Text(
            skin.title,
            color = if (selected) p.accentGreen else p.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
