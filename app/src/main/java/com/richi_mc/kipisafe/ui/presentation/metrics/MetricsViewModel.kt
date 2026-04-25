package com.richi_mc.kipisafe.ui.presentation.metrics

import androidx.lifecycle.ViewModel
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.richi_mc.kipisafe.data.stats.StatsDataSource
import com.richi_mc.kipisafe.ui.presentation.metrics.model.CategoryUsageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MetricsViewModel(
    val context: Context,
    val statsDataSource: StatsDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetricsUiState())
    val uiState: StateFlow<MetricsUiState> = _uiState.asStateFlow()

    fun loadDailyStats() {
        _uiState.update { it.copy(isLoading = true) }

        // Lo ideal es ejecutar esto en un hilo secundario (IO)
        viewModelScope.launch(Dispatchers.IO) {
            val stats = statsDataSource.getDailyUsageStats(7) // Últimos 7 días
            _uiState.update {
                it.copy(dailyUsage = stats, isLoading = false)
            }
        }
    }
    fun loadMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val dailyStats = statsDataSource.getDailyUsageStats()
            val appStats = statsDataSource.getAppUsageStats() // La lista de apps individuales

            // Agrupamos las apps por su nombre de categoría y sumamos los minutos
            val categories = appStats
                .groupBy { it.categoryName }
                .map { (name, apps) ->
                    CategoryUsageInfo(
                        categoryName = name,
                        totalMinutes = apps.sumOf { it.timeInForegroundMinutes }
                    )
                }
                .sortedByDescending { it.totalMinutes }

            _uiState.update {
                it.copy(
                    dailyUsage = dailyStats,
                    categoryUsage = categories,
                    isLoading = false
                )
            }
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // Llama a esto si hasUsageStatsPermission retorna false:
    fun openUsageAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}