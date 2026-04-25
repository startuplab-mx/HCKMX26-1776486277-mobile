package com.richi_mc.kipisafe.ui.presentation.metrics.model

// Modelo para la agregación por categoría
data class CategoryUsageInfo(
    val categoryName: String,
    val totalMinutes: Long
)