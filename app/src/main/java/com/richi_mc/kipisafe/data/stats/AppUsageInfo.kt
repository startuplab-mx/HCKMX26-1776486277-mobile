package com.richi_mc.kipisafe.data.stats

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val timeInForegroundMinutes: Long,
    val categoryName: String
)
