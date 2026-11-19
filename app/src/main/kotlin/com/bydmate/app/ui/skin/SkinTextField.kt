package com.bydmate.app.ui.skin

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField

// fork: drop-in for the M3 OutlinedTextField, the String overload the author's screens use (same
// name, parameters and defaults as M3 1.3.1). MATERIAL passes every argument straight to M3; the
// other card styles change only the container colours (fieldLook): the caller's text, label,
// border colours and sizes stay. The shape is the caller's (the fork RoundedCornerShape) or M3's
// from the skin's theme shapes.
//
// The field holds the text's selection, focus and scroll: it is one M3 call whatever the style,
// so a skin switch never remounts it.

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val style = SkinState.current.kit.controls.card
    val look = if (style == CardStyle.MATERIAL) FieldLook.MATERIAL else fieldLook(style, SkinState.palette)
    // a field recomposes on every key: the styled colours are built once per (colours, look)
    val fieldColors = if (look.passThrough) colors else remember(colors, look) { colors.withLook(look) }
    M3OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = fieldColors,
    )
}

/** The caller's field colours with the containers of every state in [look] (fieldContainer). */
private fun TextFieldColors.withLook(look: FieldLook): TextFieldColors = copy(
    focusedContainerColor = fieldContainer(focusedContainerColor, look),
    unfocusedContainerColor = fieldContainer(unfocusedContainerColor, look),
    disabledContainerColor = fieldContainer(disabledContainerColor, look),
    errorContainerColor = fieldContainer(errorContainerColor, look),
)
