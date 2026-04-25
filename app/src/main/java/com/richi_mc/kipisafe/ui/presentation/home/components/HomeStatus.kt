package com.richi_mc.kipisafe.ui.presentation.home.components

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.richi_mc.kipisafe.R
import com.richi_mc.kipisafe.ui.presentation.home.HomeUiState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface

import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeStatus(
    uiState: HomeUiState,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when {
        uiState.isVerifying -> 
            stringResource(R.string.kipi_status_verifying) to MaterialTheme.colorScheme.secondary
        !uiState.postNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            stringResource(R.string.kipi_status_need_post_notifications) to MaterialTheme.colorScheme.error
        !uiState.listenerEnabled ->
            stringResource(R.string.kipi_status_need_listener) to MaterialTheme.colorScheme.error
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !uiState.overlayGranted ->
            stringResource(R.string.kipi_status_need_overlay) to MaterialTheme.colorScheme.error
        else ->
            stringResource(R.string.kipi_status_active) to MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = statusColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
