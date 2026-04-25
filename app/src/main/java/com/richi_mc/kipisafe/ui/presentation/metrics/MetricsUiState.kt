package com.richi_mc.kipisafe.ui.presentation.metrics

import com.richi_mc.kipisafe.data.stats.DailyUsageInfo
import com.richi_mc.kipisafe.ui.presentation.metrics.model.CategoryUsageInfo


// Actualizamos el estado
data class MetricsUiState(
    val dailyUsage: List<DailyUsageInfo> = emptyList(),
    val categoryUsage: List<CategoryUsageInfo> = emptyList(), // Nueva lista
    val isLoading: Boolean = false
)