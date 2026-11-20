package com.bydmate.app.ui.automation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.data.local.entity.ActionDef
import com.bydmate.app.fork.automation.YAMUSIC_CAR_COMMANDS
import com.bydmate.app.fork.automation.YAMUSIC_CAR_KIND
import com.bydmate.app.fork.automation.YamusicCarId
import com.bydmate.app.fork.automation.yamusicCarCommand
import com.bydmate.app.fork.automation.yamusicCarCommandName
import com.bydmate.app.fork.automation.yamusicCarUriFromPayload
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.ScaledDialogContent
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import org.json.JSONObject

// Fork: the editor of the «Я.Музыка Car» action (kind `yamusic_car`).

/** A new action: «Моя волна», the player stays in the background. */
fun newYamusicCarAction(context: Context): ActionDef = ActionDef(
    command = "",
    displayName = context.getString(R.string.yamusic_car_name),
    kind = YAMUSIC_CAR_KIND,
    payload = JSONObject().put("command", "wave").put("show", false).toString(),
)

/** The fields of the dialog, as they are saved in the payload. */
private data class YamusicCarDraft(
    val command: String = "wave",
    val id: String = "",
    val uid: String = "",
    val kind: String = "",
    val show: Boolean = false,
) {
    fun toPayload(): String {
        val json = JSONObject().put("command", command)
        when (yamusicCarCommand(command)?.idKind) {
            YamusicCarId.STATION -> json.put("id", id.trim())
            YamusicCarId.PLAYLIST -> json.put("uid", uid.trim()).put("kind", kind.trim())
            else -> Unit
        }
        return json.put("show", show && yamusicCarCommand(command)?.allowsShow != false).toString()
    }

    /** Changing the command clears the fields that no longer apply. */
    fun withCommand(newCommand: String): YamusicCarDraft {
        val idKind = yamusicCarCommand(newCommand)?.idKind
        val allowsShow = yamusicCarCommand(newCommand)?.allowsShow != false
        return copy(
            command = newCommand,
            id = if (idKind == YamusicCarId.STATION && yamusicCarCommand(command)?.idKind == YamusicCarId.STATION) id else "",
            uid = if (idKind == YamusicCarId.PLAYLIST) uid else "",
            kind = if (idKind == YamusicCarId.PLAYLIST) kind else "",
            show = show && allowsShow,
        )
    }
}

private fun ActionDef.yamusicCarDraft(): YamusicCarDraft {
    val json = try { JSONObject(payload ?: "{}") } catch (_: Exception) { JSONObject() }
    return YamusicCarDraft(
        command = json.optString("command").ifBlank { "wave" },
        id = json.optString("id"),
        uid = json.optString("uid"),
        kind = json.optString("kind"),
        show = json.optBoolean("show", false),
    )
}

@Composable
internal fun YamusicCarActionControls(
    action: ActionDef,
    onUpdate: (ActionDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val name = yamusicCarCommandName(action.payload, LocalContext.current)
    val preview = name ?: stringResource(R.string.automation_tap_to_configure)

    Row(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(6.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
            .clickable { editing = true }
            .heightIn(min = FIELD_HEIGHT)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = preview,
            fontSize = 16.sp,
            color = if (name == null) TextSecondary else TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }

    if (editing) {
        YamusicCarEditDialog(
            initial = action.yamusicCarDraft(),
            onDismiss = { editing = false },
            onSave = { draft ->
                onUpdate(action.copy(payload = draft.toPayload()))
                editing = false
            },
        )
    }
}

@Composable
private fun YamusicCarEditDialog(
    initial: YamusicCarDraft,
    onDismiss: () -> Unit,
    onSave: (YamusicCarDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val command = yamusicCarCommand(draft.command)
    val canSave = yamusicCarUriFromPayload(draft.toPayload()) != null

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = AccentGreen,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentGreen,
        errorBorderColor = Color(0xFFEF4444),
        errorLabelColor = Color(0xFFEF4444),
    )

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text(stringResource(R.string.yamusic_car_name), color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.yamusic_car_field_command), fontSize = 14.sp, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Box {
                    Text(
                        command?.let { stringResource(it.nameRes) } ?: draft.command,
                        fontSize = 14.sp,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(10.dp, 8.dp),
                    )
                    DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                        ScaledDialogContent {
                            YAMUSIC_CAR_COMMANDS.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(c.nameRes), fontSize = 16.sp) },
                                    onClick = {
                                        draft = draft.withCommand(c.id)
                                        dropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                when (command?.idKind) {
                    YamusicCarId.STATION -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.id,
                            onValueChange = { draft = draft.copy(id = it) },
                            label = { Text(stringResource(stationIdLabel(command.id))) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    YamusicCarId.PLAYLIST -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.uid,
                            onValueChange = { draft = draft.copy(uid = it.filter(Char::isDigit)) },
                            label = { Text(stringResource(R.string.yamusic_car_field_uid)) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.kind,
                            onValueChange = { draft = draft.copy(kind = it.filter(Char::isDigit)) },
                            label = { Text(stringResource(R.string.yamusic_car_field_kind)) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> Unit
                }
                if (command?.allowsShow != false) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { draft = draft.copy(show = !draft.show) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = draft.show,
                            onCheckedChange = { draft = draft.copy(show = it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentGreen,
                                uncheckedColor = CardBorder,
                                checkmarkColor = TextPrimary,
                            ),
                        )
                        Text(stringResource(R.string.yamusic_car_field_show), fontSize = 16.sp, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (canSave) onSave(draft) }, enabled = canSave) {
                Text(stringResource(R.string.automation_save_button), color = if (canSave) AccentGreen else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_cancel_button), color = TextSecondary)
            }
        },
    )
}

private fun stationIdLabel(command: String): Int = when (command) {
    "wave_track" -> R.string.yamusic_car_field_track_id
    "wave_album" -> R.string.yamusic_car_field_album_id
    else -> R.string.yamusic_car_field_artist_id
}
