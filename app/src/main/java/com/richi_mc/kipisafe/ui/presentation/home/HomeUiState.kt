package com.richi_mc.kipisafe.ui.presentation.home

data class HomeUiState(
    val isVerifying: Boolean = true,
    val isSendingAlert: Boolean = false,
    val postNotificationsGranted: Boolean = false,
    val listenerEnabled: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val showKipiListenerDialog: Boolean = false,
    val showOverlayDialog: Boolean = false,
    val showConfirmHelpDialog: Boolean = false,
    val snackbarMessage: String? = null
)
