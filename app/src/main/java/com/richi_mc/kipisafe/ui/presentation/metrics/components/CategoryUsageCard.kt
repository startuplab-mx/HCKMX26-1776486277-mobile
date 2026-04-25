package com.richi_mc.kipisafe.ui.presentation.metrics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.richi_mc.kipisafe.R
import com.richi_mc.kipisafe.ui.presentation.metrics.model.CategoryUsageInfo

@Composable
fun CategoryUsageCard(
    category: CategoryUsageInfo,
    modifier: Modifier = Modifier
) {
    val (iconRes, color) = getCategoryStyle(category.categoryName)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contenedor del Icono
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tiempo total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = formatMinutes(category.totalMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun getCategoryStyle(categoryName: String): Pair<Int, Color> {
    return when (categoryName) {
        "Juegos" -> R.drawable.juegos to MaterialTheme.colorScheme.primary
        "Social", "Sociales" -> R.drawable.sociales to MaterialTheme.colorScheme.secondary
        "Video" -> R.drawable.video to MaterialTheme.colorScheme.tertiary
        "Productividad" -> R.drawable.productividad to Color(0xFF4CAF50)
        "Noticias" -> R.drawable.noticias to Color(0xFF2196F3)
        "Audio", "Multimedia" -> R.drawable.otros to Color(0xFF9C27B0)
        "Imágenes", "Fotos" -> R.drawable.imagenes to Color(0xFFE91E63)
        else -> R.drawable.no_definido to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
