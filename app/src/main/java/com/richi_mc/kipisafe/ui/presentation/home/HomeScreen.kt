package com.richi_mc.kipisafe.ui.presentation.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.richi_mc.kipisafe.ui.presentation.home.components.HomeActions
import com.richi_mc.kipisafe.ui.presentation.home.components.HomeDialogs
import com.richi_mc.kipisafe.ui.presentation.home.components.HomeHeader
import com.richi_mc.kipisafe.ui.presentation.home.components.HomeStatus
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState()
        if (!uiState.postNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.5f))
            
            HomeHeader()

            Spacer(modifier = Modifier.height(32.dp))

            HomeStatus(uiState = uiState)

            Spacer(modifier = Modifier.weight(1f))

            HomeActions(
                uiState = uiState,
                onHelpClick = { viewModel.setShowConfirmHelpDialog(true) },
                onGrantPermissionsClick = {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.postNotificationsGranted -> {
                            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        !uiState.listenerEnabled -> {
                            viewModel.setShowKipiListenerDialog(true)
                        }

                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !uiState.overlayGranted -> {
                            viewModel.setShowOverlayDialog(true)
                        }

                        else -> {
                            viewModel.refreshPermissionState()
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        HomeDialogs(
            uiState = uiState,
            onDismissKipiListener = { viewModel.setShowKipiListenerDialog(false) },
            onDismissOverlay = { viewModel.setShowOverlayDialog(false) },
            onDismissConfirmHelp = { viewModel.setShowConfirmHelpDialog(false) },
            onConfirmHelp = { viewModel.sendManualAlert() }
        )
    }
}
