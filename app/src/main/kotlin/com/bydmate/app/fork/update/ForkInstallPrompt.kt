package com.bydmate.app.fork.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bydmate.app.R
import com.bydmate.app.ui.skin.Button
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.OutlinedButton
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.ScaledDialogContent
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/**
 * The «Установить обновление X» prompt shown at app open when a downloaded update is ready AND the
 * car is parked (P and speed 0). One primary button launches the system installer; «Позже» just
 * dismisses — the APK stays ready for the next stop. fork-local.
 */
@Composable
fun ForkInstallPrompt(
    version: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ScaledDialogContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* absorb taps inside the card */ },
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.fork_update_prompt_title),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.fork_update_prompt_ready, version),
                            color = TextSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        ) {
                            Text(
                                stringResource(R.string.fork_update_install_button, version),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(R.string.fork_update_later_button), color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
