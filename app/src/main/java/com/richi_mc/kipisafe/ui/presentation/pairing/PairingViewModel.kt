package com.richi_mc.kipisafe.ui.presentation.pairing

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richi_mc.kipisafe.data.local.AuthManager
import com.richi_mc.kipisafe.data.model.ClaimPairingRequest
import com.richi_mc.kipisafe.data.model.GenerateCodeRequest
import com.richi_mc.kipisafe.data.remote.KipiApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PairingUiState {
    object Loading : PairingUiState()
    data class CodeGenerated(val sessionId: String, val otp: String) : PairingUiState()
    object Success : PairingUiState()
    data class Error(val message: String) : PairingUiState()
}

class PairingViewModel(
    private val api: KipiApiService,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Loading)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        generatePairingCode()
    }

    fun generatePairingCode() {
        viewModelScope.launch {
            _uiState.value = PairingUiState.Loading
            try {
                val request = GenerateCodeRequest(
                    device_model = "${Build.MANUFACTURER} ${Build.MODEL}"
                )
                val response = api.generatePairingCode(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.ok) {
                        _uiState.value = PairingUiState.CodeGenerated(body.session_id, body.otp)
                    } else {
                        _uiState.value = PairingUiState.Error(body?.message ?: "Error al generar código")
                    }
                } else {
                    _uiState.value = PairingUiState.Error("Error del servidor: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = PairingUiState.Error("Error de conexión: ${e.message}")
            }
        }
    }

    fun claimPairing() {
        val currentState = _uiState.value
        if (currentState is PairingUiState.CodeGenerated) {
            viewModelScope.launch {
                try {
                    val request = ClaimPairingRequest(
                        session_id = currentState.sessionId,
                        otp = currentState.otp
                    )
                    val response = api.claimPairing(request)
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.ok) {
                            authManager.saveApiKey(body.api_key, body.minor_id)
                            _uiState.value = PairingUiState.Success
                        } else {
                            _uiState.value = PairingUiState.Error("No se pudo completar el emparejamiento")
                        }
                    } else {
                        _uiState.value = PairingUiState.Error("Error al reclamar: ${response.code()}")
                    }
                } catch (e: Exception) {
                    _uiState.value = PairingUiState.Error("Error de red: ${e.message}")
                }
            }
        }
    }
}
