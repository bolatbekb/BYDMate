package com.bydmate.app.ui.skin

import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.AlertDialog as M3AlertDialog

// fork: drop-in for the M3 AlertDialog (same name, parameters and defaults as M3 1.3.1). The
// default shape is MaterialTheme.shapes.extraLarge, which the skin theme already sets. OUTLINED,
// BORDER_ONLY and STRIPE outline the dialog surface with a 1 dp CardBorder; every other style
// passes the caller's arguments straight to M3.

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
    // M3 hands the modifier to the box that wraps the dialog surface, so the border sits on its edge
    val outlined = dialogHasBorder(SkinState.current.kit.controls.card)
    M3AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = if (outlined) modifier.border(CardBorderWidth, SkinState.palette.cardBorder, shape) else modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
