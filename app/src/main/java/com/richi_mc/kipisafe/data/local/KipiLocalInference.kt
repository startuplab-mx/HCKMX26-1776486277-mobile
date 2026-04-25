package com.richi_mc.kipisafe.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inferencia local basada en [KipiInferenceEngine] (regresión logística + TF-IDF on-device).
 */
class KipiLocalInference(private val context: Context) {

    private var engine: KipiInferenceEngine? = null

    init {
        try {
            // Intentamos inicializar el motor. Usamos Throwable para capturar ExceptionInInitializerError
            // u otros errores de carga de clase que no son capturados por Exception.
            engine = KipiInferenceEngine(context)
            Log.d("KipiLocal", "Motor matemático KipiInferenceEngine inicializado.")
        } catch (e: Throwable) {
            Log.e("KipiLocal", "Error crítico al cargar el motor matemático: ${e.message}", e)
            // Si el motor falla, 'engine' queda null y analyzeTextLocally usará el resultado por defecto.
        }
    }

    suspend fun analyzeTextLocally(text: String): AnalysisResult = withContext(Dispatchers.Default) {
        val defaultResult = AnalysisResult("SAFE", 1.0, 0)

        val kipiEngine = engine ?: return@withContext defaultResult

        return@withContext try {
            val prediction = kipiEngine.predict(text)

            Log.d(
                "KipiLocal",
                "Inferencia: [${prediction.label}] Confianza: ${prediction.confidence}",
            )

            AnalysisResult(
                label = prediction.label,
                confidence = prediction.confidence,
                level = prediction.level,
            )
        } catch (e: Exception) {
            Log.e("KipiLocal", "Error en inferencia: ${e.message}", e)
            defaultResult
        }
    }

    fun close() {
        // Sin recursos nativos que liberar (el motor solo mantiene arrays en memoria).
    }
}

/** Resultado estructurado de la clasificación local. */
data class AnalysisResult(val label: String, val confidence: Double, val level: Int)
