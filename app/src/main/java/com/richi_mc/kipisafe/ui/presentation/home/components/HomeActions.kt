package com.richi_mc.kipisafe.ui.presentation.home.components

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.richi_mc.kipisafe.R
import com.richi_mc.kipisafe.ui.presentation.home.HomeUiState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*

@Composable
fun HomeActions(
    uiState: HomeUiState,
    onHelpClick: () -> Unit,
    onGrantPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.isSendingAlert) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Button(
                onClick = onHelpClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Pedir Ayuda Urgente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Secondary action for permissions if needed
        val needsPermissions = !uiState.postNotificationsGranted || 
                             !uiState.listenerEnabled || 
                             (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !uiState.overlayGranted)

        if (needsPermissions) {
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
        }
    }
}
