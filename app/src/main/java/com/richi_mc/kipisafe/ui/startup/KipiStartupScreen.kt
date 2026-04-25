package com.richi_mc.kipisafe.ui.startup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.richi_mc.kipisafe.R
import com.richi_mc.kipisafe.data.local.AuthManager
import com.richi_mc.kipisafe.data.model.ManualAlertRequest
import com.richi_mc.kipisafe.data.remote.RetrofitClient
import com.richi_mc.kipisafe.service.KipiForegroundService
import com.richi_mc.kipisafe.ui.overlay.KipiOverlayManager
import com.richi_mc.kipisafe.util.isNotificationServiceEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun KipiStartupScreen(activity: ComponentActivity) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val authManager: AuthManager = koinInject()

    var isVerifying by remember { mutableStateOf(true) }
    var isSendingAlert by remember { mutableStateOf(false) }
    var showConfirmHelpDialog by remember { mutableStateOf(false) }
    var postNotificationsGranted by remember {
        mutableStateOf(hasPostNotificationsPermission(activity))
    }
    var listenerEnabled by remember {
        mutableStateOf(activity.isNotificationServiceEnabled())
    }
    var overlayGranted by remember { mutableStateOf(canDrawOverlay(activity)) }
    var showKipiListenerDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }

    fun refreshPermissionState() {
        postNotificationsGranted = hasPostNotificationsPermission(activity)
        listenerEnabled = activity.isNotificationServiceEnabled()
        overlayGranted = canDrawOverlay(activity)
        isVerifying = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        postNotificationsGranted = granted
        if (granted) {
            when {
                !activity.isNotificationServiceEnabled() -> showKipiListenerDialog = true
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlay(activity) -> {
                    showOverlayDialog = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshPermissionState()
        when {
            !postNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            postNotificationsGranted && !listenerEnabled -> {
                showKipiListenerDialog = true
            }
        }
    }

    LaunchedEffect(isVerifying, postNotificationsGranted, listenerEnabled) {
        if (isVerifying) return@LaunchedEffect
        if (!postNotificationsGranted || !listenerEnabled) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlay(activity)) {
            showOverlayDialog = true
        }
    }

    LaunchedEffect(isVerifying, postNotificationsGranted, listenerEnabled) {
        if (!isVerifying && postNotificationsGranted && listenerEnabled) {
            val intent = Intent(activity, KipiForegroundService::class.java)
            ContextCompat.startForegroundService(activity, intent)
        }
    }

    if (showKipiListenerDialog && postNotificationsGranted && !listenerEnabled) {
        AlertDialog(
            onDismissRequest = { showKipiListenerDialog = false },
            title = { Text(text = stringResource(R.string.kipi_dialog_title)) },
            text = {
                Text(text = stringResource(R.string.kipi_dialog_listener_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        showKipiListenerDialog = false
                    },
                ) {
                    Text(stringResource(R.string.kipi_dialog_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKipiListenerDialog = false }) {
                    Text(stringResource(R.string.kipi_dialog_not_now))
                }
            },
        )
    }

    if (showOverlayDialog &&
        postNotificationsGranted &&
        listenerEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !canDrawOverlay(activity)
    ) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text(text = stringResource(R.string.kipi_dialog_title)) },
            text = {
                Text(text = stringResource(R.string.kipi_dialog_overlay_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}"),
                        )
                        activity.startActivity(intent)
                        showOverlayDialog = false
                    },
                ) {
                    Text(stringResource(R.string.kipi_dialog_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) {
                    Text(stringResource(R.string.kipi_dialog_not_now))
                }
            },
        )
    }

    if (showConfirmHelpDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmHelpDialog = false },
            title = { Text("¿Necesitas ayuda?") },
            text = { Text("¿Quieres que Kipi avise a tus padres que necesitas ayuda?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmHelpDialog = false
                        isSendingAlert = true
                        coroutineScope.launch {
                            try {
                                val authHeader = "Device ${authManager.getApiKey()}"
                                val response = RetrofitClient.api.sendManualAlert(
                                    authHeader,
                                    ManualAlertRequest("123e4567-e89b-12d3-a456-426614174000")
                                )
                                withContext(Dispatchers.Main) {
                                    isSendingAlert = false
                                    if (response.isSuccessful && response.body()?.ok == true) {
                                        KipiOverlayManager(context).showKipiAdvice(
                                            "¡Ayuda enviada! Kipi ya avisó a tus padres. Mantente en un lugar seguro y espera a que se comuniquen contigo."
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            "No pudimos enviar la alerta. Por favor, llama directamente por teléfono o busca a un adulto cerca."
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isSendingAlert = false
                                    snackbarHostState.showSnackbar(
                                        "Error de conexión. Intenta llamar directamente o busca ayuda a tu alrededor."
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text("Sí, avisar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmHelpDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    val statusText = when {
        isVerifying -> stringResource(R.string.kipi_status_verifying)
        !postNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            stringResource(R.string.kipi_status_need_post_notifications)
        !listenerEnabled ->
            stringResource(R.string.kipi_status_need_listener)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !overlayGranted ->
            stringResource(R.string.kipi_status_need_overlay)
        else ->
            stringResource(R.string.kipi_status_active)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.kipi_brand_icon),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.kipi_brand_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isSendingAlert) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Button(
                    onClick = {
                        showConfirmHelpDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(0.85f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5722) // Naranja vibrante
                    )
                ) {
                    Text(
                        "Pedir Ayuda Urgente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !hasPostNotificationsPermission(activity) -> {
                            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        !activity.isNotificationServiceEnabled() -> {
                            showKipiListenerDialog = true
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlay(activity) -> {
                            showOverlayDialog = true
                        }
                        else -> {
                            refreshPermissionState()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                Text(stringResource(R.string.kipi_grant_permissions_button))
            }
        }
    }
}

private fun hasPostNotificationsPermission(activity: ComponentActivity): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun canDrawOverlay(activity: ComponentActivity): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(activity)
    } else {
        true
    }
}
