package com.richi_mc.kipisafe.data.stats

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.collections.iterator

class StatsDataSource (
    private val context: Context
) {

    fun getAppUsageStats(): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        // Definir el rango de tiempo: Últimas 24 horas
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val appUsageList = mutableListOf<AppUsageInfo>()

        for ((packageName, stats) in usageStatsMap) {
            val timeInForegroundMs = stats.totalTimeInForeground

            if (timeInForegroundMs > 0) {
                val minutes = (timeInForegroundMs / 1000) / 60

                // Filtramos para obtener solo apps con al menos 1 minuto de uso
                if (minutes > 0) {
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()

                        // Obtener la categoría de forma segura según la versión de Android
                        val categoryId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            appInfo.category
                        } else {
                            ApplicationInfo.CATEGORY_UNDEFINED
                        }

                        val categoryName = getCategoryName(categoryId)

                        appUsageList.add(
                            AppUsageInfo(
                                packageName = packageName,
                                appName = appName,
                                timeInForegroundMinutes = minutes,
                                categoryName = categoryName
                            )
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        // Ignorar limpiamente las aplicaciones del sistema o desinstaladas
                        // que no se encuentren en el PackageManager
                    }
                }
            }
        }

        // Devolver la lista ordenada de mayor a menor tiempo de uso
        return appUsageList.sortedByDescending { it.timeInForegroundMinutes }
    }
    fun getDailyUsageStats(daysToFetch: Int = 7): List<DailyUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dailyList = mutableListOf<DailyUsageInfo>()

        // Formateador para crear una etiqueta corta para tu gráfico (ej: "24/04")
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

        // Iteramos hacia atrás, desde hoy (0) hasta los días solicitados
        for (i in 0 until daysToFetch) {
            // Configurar el límite final del día (23:59:59)
            val endCalendar = Calendar.getInstance()
            endCalendar.add(Calendar.DAY_OF_YEAR, -i)
            endCalendar.set(Calendar.HOUR_OF_DAY, 23)
            endCalendar.set(Calendar.MINUTE, 59)
            endCalendar.set(Calendar.SECOND, 59)
            val endTime = endCalendar.timeInMillis

            // Configurar el límite inicial del día (00:00:00)
            val startCalendar = Calendar.getInstance()
            startCalendar.add(Calendar.DAY_OF_YEAR, -i)
            startCalendar.set(Calendar.HOUR_OF_DAY, 0)
            startCalendar.set(Calendar.MINUTE, 0)
            startCalendar.set(Calendar.SECOND, 0)
            val startTime = startCalendar.timeInMillis

            // Consultamos la data exclusivamente para esa ventana de 24 horas
            val usageStatsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

            var totalTimeMs = 0L
            for (stats in usageStatsMap.values) {
                totalTimeMs += stats.totalTimeInForeground
            }

            // Convertimos a minutos y generamos la etiqueta del día
            val totalMinutes = (totalTimeMs / 1000) / 60
            val dateLabel = dateFormat.format(startCalendar.time)

            dailyList.add(DailyUsageInfo(dateLabel, totalMinutes))
        }

        // Invertimos la lista para que quede en orden cronológico (de izquierda a derecha para tu gráfico)
        return dailyList.reversed()
    }

    private fun getCategoryName(category: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "No definida"
        }

        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> "Juegos"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productividad"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_IMAGE -> "Imágenes"
            ApplicationInfo.CATEGORY_NEWS -> "Noticias"
            ApplicationInfo.CATEGORY_MAPS -> "Mapas"
            ApplicationInfo.CATEGORY_UNDEFINED -> "No definida"
            else -> "Otra"
        }
    }
}