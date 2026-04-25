package com.richi_mc.kipisafe.ui.presentation.home.components

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.richi_mc.kipisafe.R
import com.richi_mc.kipisafe.ui.presentation.home.HomeUiState

@Composable
fun HomeActions(
    uiState: HomeUiState,
    onGrantPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculamos los permisos faltantes para mostrar el contador
    val missingPermissions = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.postNotificationsGranted) add("Notificaciones")
        if (!uiState.listenerEnabled) add("Lectura de Notificaciones")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !uiState.overlayGranted) add("Superposición")
        if (!uiState.accessibilityEnabled) add("Accesibilidad")
    }

    if (missingPermissions.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = onGrantPermissionsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.kipi_grant_permissions_button),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (missingPermissions.size == 1) 
                    "Falta 1 permiso por conceder" 
                else 
                    "Faltan ${missingPermissions.size} permisos por conceder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
