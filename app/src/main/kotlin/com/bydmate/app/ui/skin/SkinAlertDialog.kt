package com.bydmate.app.ui.skin

import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.AlertDialog as M3AlertDialog

// fork: drop-in for the M3 AlertDialog (same name, parameters and defaults as M3 1.3.1). The
// default shape is MaterialTheme.shapes.extraLarge, which the skin theme already sets. OUTLINED,
// BORDER_ONLY and STRIPE outline the dialog surface with a 1 dp CardBorder. GRAPHITE makes the
// surface the Graphite home's card (grCard, the live look) under a see-through container with no
// tonal elevation; the caller's text colours stay. Every other style passes the caller's
// arguments straight to M3. One M3AlertDialog call inside one provider, whatever the style: a
// skin switch with the dialog open keeps its window and its content's state.

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    // M3 hands the modifier to the box that wraps the dialog surface, so the border and the
    // Graphite card sit on its edge
    val style = SkinState.current.kit.controls.card
    val graphite = style == CardStyle.GRAPHITE
    val outlined = dialogHasBorder(style)
    ProvideGraphiteLook(graphite) {
        M3AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = when {
                graphite -> modifier.graphiteSkinCard(shape)
                outlined -> modifier.border(CardBorderWidth, SkinState.palette.cardBorder, shape)
                else -> modifier
            },
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = if (graphite) Color.Transparent else containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = if (graphite) 0.dp else tonalElevation,
            properties = properties,
        )
    }
}
