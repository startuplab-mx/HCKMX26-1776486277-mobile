package com.richi_mc.kipisafe.ui.presentation.home.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.richi_mc.kipisafe.R
import com.richi_mc.kipisafe.ui.presentation.home.HomeUiState

@Composable
fun HomeDialogs(
    uiState: HomeUiState,
    onDismissKipiListener: () -> Unit,
    onDismissOverlay: () -> Unit,
    onDismissConfirmHelp: () -> Unit,
    onConfirmHelp: () -> Unit
) {
    val context = LocalContext.current

    if (uiState.showKipiListenerDialog && uiState.postNotificationsGranted && !uiState.listenerEnabled) {
        AlertDialog(
            onDismissRequest = onDismissKipiListener,
            title = { Text(text = stringResource(R.string.kipi_dialog_title)) },
            text = {
                Text(text = stringResource(R.string.kipi_dialog_listener_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        onDismissKipiListener()
                    },
                ) {
                    Text(stringResource(R.string.kipi_dialog_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissKipiListener) {
                    Text(stringResource(R.string.kipi_dialog_not_now))
                }
            },
        )
    }

    if (uiState.showOverlayDialog &&
        uiState.postNotificationsGranted &&
        uiState.listenerEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !uiState.overlayGranted
    ) {
        AlertDialog(
            onDismissRequest = onDismissOverlay,
            title = { Text(text = stringResource(R.string.kipi_dialog_title)) },
            text = {
                Text(text = stringResource(R.string.kipi_dialog_overlay_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                        onDismissOverlay()
                    },
                ) {
                    Text(stringResource(R.string.kipi_dialog_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissOverlay) {
                    Text(stringResource(R.string.kipi_dialog_not_now))
                }
            },
        )
    }

    if (uiState.showConfirmHelpDialog) {
        AlertDialog(
            onDismissRequest = onDismissConfirmHelp,
            title = { Text("¿Necesitas ayuda?") },
            text = { Text("¿Quieres que Kipi avise a tus padres que necesitas ayuda?") },
            confirmButton = {
                TextButton(
                    onClick = onConfirmHelp
                ) {
                    Text("Sí, avisar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmHelp) {
                    Text("Cancelar")
                }
            }
        )
    }
}
