package com.richi_mc.kipisafe.ui.presentation.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel

@Composable
fun PairingScreen(
    viewModel: PairingViewModel = koinViewModel(),
    onPairingSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    PairingContent(
        uiState = uiState,
        onClaimClick = { viewModel.claimPairing() },
        onRetryClick = { viewModel.generatePairingCode() },
        onPairingSuccess = onPairingSuccess
    )
}

@Composable
fun PairingContent(
    uiState: PairingUiState,
    onClaimClick: () -> Unit,
    onRetryClick: () -> Unit,
    onPairingSuccess: () -> Unit
) {
    // Manejo del estado Success
    if (uiState is PairingUiState.Success) {
        LaunchedEffect(Unit) {
            onPairingSuccess()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (uiState) {
                is PairingUiState.Loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Generando código de vinculación...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                is PairingUiState.CodeGenerated -> {
                    Text(
                        text = "Vincula este dispositivo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Ingresa el siguiente código en la aplicación del padre para comenzar la protección de Kipi.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // Card para el código OTP
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.otp,
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 8.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = onClaimClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Ya ingresé el código",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is PairingUiState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = onRetryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 2.dp
                        )
                    ) {
                        Text(
                            text = "Reintentar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                is PairingUiState.Success -> {
                    // El LaunchedEffect ya maneja la navegación,
                    // opcionalmente podrías mostrar un check de éxito aquí.
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PairingScreenPreview_Code() {
    MaterialTheme {
        PairingContent(
            uiState = PairingUiState.CodeGenerated("session123", "849201"),
            onClaimClick = {},
            onRetryClick = {},
            onPairingSuccess = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PairingScreenPreview_Loading() {
    MaterialTheme {
        PairingContent(
            uiState = PairingUiState.Loading,
            onClaimClick = {},
            onRetryClick = {},
            onPairingSuccess = {}
        )
    }
}
