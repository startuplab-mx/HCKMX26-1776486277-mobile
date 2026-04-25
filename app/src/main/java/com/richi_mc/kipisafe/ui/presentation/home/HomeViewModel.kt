package com.richi_mc.kipisafe.ui.presentation.home

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richi_mc.kipisafe.data.model.ManualAlertRequest
import com.richi_mc.kipisafe.data.remote.RetrofitClient
import com.richi_mc.kipisafe.service.KipiForegroundService
import com.richi_mc.kipisafe.service.ParentalControlAccessibilityService
import com.richi_mc.kipisafe.ui.overlay.KipiOverlayManager
import com.richi_mc.kipisafe.util.isNotificationServiceEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refreshPermissionState() {
        _uiState.update {
            it.copy(
                postNotificationsGranted = hasPostNotificationsPermission(),
                listenerEnabled = context.isNotificationServiceEnabled(),
                overlayGranted = canDrawOverlay(),
                accessibilityEnabled = isAccessibilityServiceEnabled(),
                isVerifying = false
            )
        }
        checkAndStartService()
    }

    private fun checkAndStartService() {
        val state = _uiState.value
        if (!state.isVerifying && state.postNotificationsGranted && state.listenerEnabled) {
            val intent = Intent(context, KipiForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(postNotificationsGranted = granted) }
        if (granted) {
            if (!context.isNotificationServiceEnabled()) {
                _uiState.update { it.copy(showKipiListenerDialog = true) }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlay()) {
                _uiState.update { it.copy(showOverlayDialog = true) }
            }
        }
    }

    fun sendManualAlert() {
        _uiState.update { it.copy(isSendingAlert = true, showConfirmHelpDialog = false) }
        viewModelScope.launch {
            try {
                val response = RetrofitClient.api.sendManualAlert(
                    ManualAlertRequest("123e4567-e89b-12d3-a456-426614174000")
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSendingAlert = false) }
                    if (response.isSuccessful && response.body()?.ok == true) {
                        KipiOverlayManager(context).showKipiAdvice(
                            "¡Ayuda enviada! Kipi ya avisó a tus padres. Mantente en un lugar seguro y espera a que se comuniquen contigo."
                        )
                    } else {
                        _uiState.update { it.copy(snackbarMessage = "No pudimos enviar la alerta. Por favor, llama directamente por teléfono o busca a un adulto cerca.") }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isSendingAlert = false,
                            snackbarMessage = "Error de conexión. Intenta llamar directamente o busca ayuda a tu alrededor."
                        )
                    }
                }
            }
        }
    }

    fun setShowConfirmHelpDialog(show: Boolean) {
        _uiState.update { it.copy(showConfirmHelpDialog = show) }
    }

    fun setShowKipiListenerDialog(show: Boolean) {
        _uiState.update { it.copy(showKipiListenerDialog = show) }
    }

    fun setShowOverlayDialog(show: Boolean) {
        _uiState.update { it.copy(showOverlayDialog = show) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(context, ParentalControlAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
