package com.richi_mc.kipisafe.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.richi_mc.kipisafe.data.local.AuthManager
import com.richi_mc.kipisafe.data.local.KipiLocalInference
import com.richi_mc.kipisafe.data.model.ManualAlertRequest
import com.richi_mc.kipisafe.data.remote.RetrofitClient
import com.richi_mc.kipisafe.ui.overlay.KipiOverlayManager
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ParentalControlAccessibilityService : AccessibilityService() {

    // Corrutines in foreground service.
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val appCategoryCache = mutableMapOf<String, Boolean>()
    private lateinit var localInference: KipiLocalInference
    private val authManager: AuthManager by inject()

    /** Evita re-analizar el mismo texto en ráfaga (debounce). */
    private val processedCache = ConcurrentHashMap<String, Long>()

    private var lastAnalysisTime = 0L
    private var lastOverlayShownTime = 0L

    override fun onCreate() {
        super.onCreate()
        localInference = KipiLocalInference(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Solo procesar si es una plataforma de streaming o video
        if (!isStreamingApp(packageName)) return

        // Filter events when screen changes or some text are writed.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastOverlayShownTime < POST_OVERLAY_COOLDOWN_MS) return
            if (currentTime - lastAnalysisTime < ANALYSIS_INTERVAL_MS) return
            lastAnalysisTime = currentTime

            val rootNode = rootInActiveWindow ?: return

            serviceScope.launch {
                val extractedText = extractTextFromNode(rootNode)
                rootNode.recycle()

                val textChunks = cleanAndChunkText(extractedText)
                for (chunk in textChunks) {
                    if (isValidForAnalysis(chunk) && !isDuplicateText(chunk)) {
                        Log.d(TAG, "Analizando chunk de streaming: ${chunk.take(100)}")

                        if (checkRadicalHeuristics(chunk)) {
                            showCriticalOverlay("Cuidado, el contenido mostrado puede ser inapropiado, por lo que te recomendamos evitarlo y buscar a un adulto de confianza.")
                            notifyBackend(packageName, "Alerta de vocabulario: Se detectó una palabra de extremo riesgo en pantalla. Contexto capturado: \"$chunk\"", 2) // Nivel 2 de riesgo
                            break // Detener análisis si se encuentra algo crítico
                        }

                        runAnalyzeRequest(packageName, chunk)
                    }
                }
            }
        }
    }

    private fun cleanAndChunkText(text: String): List<String> {
        return text.split("\n")
            .map { it.trim() }
            .filter { it.length > MIN_MEANINGFUL_LENGTH }
            .distinct()
    }

    private fun isDuplicateText(text: String): Boolean {
        val now = System.currentTimeMillis()
        val last = processedCache.putIfAbsent(text, now)
        if (last != null) {
            if (now - last < CACHE_EXPIRATION_MS) return true
            processedCache.replace(text, last, now)
        }
        if (processedCache.size > MAX_CACHE_ENTRIES) processedCache.clear()
        return false
    }

    // CAMBIO 2: Filtro mejorado con validación de cantidad de palabras
    private fun isValidForAnalysis(text: String): Boolean {
        if (text.isBlank()) return false
        if (checkRadicalHeuristics(text)) return true
        if (CRITICAL_EMOJIS.any { text.contains(it) }) return true

        val lower = text.lowercase(Locale.getDefault())
        if (SYSTEM_NOISE_PATTERNS.any { lower.contains(it) }) return false

        // Rechazar textos con menos de 3 palabras que no sean palabras clave radicales
        val wordCount = text.trim().split("\\s+".toRegex()).size
        if (wordCount < 3 && !checkRadicalHeuristics(text)) return false

        return text.length >= MIN_MEANINGFUL_LENGTH
    }

    private fun checkRadicalHeuristics(text: String): Boolean {
        val upperText = text.uppercase(Locale.getDefault())
        return RADICAL_KEYWORDS.any { keyword ->
            if (keyword.contains(Regex("[^A-Z0-9]"))) { // Si tiene emojis o símbolos
                upperText.contains(keyword.uppercase(Locale.getDefault()))
            } else {
                // Para siglas, buscamos coincidencia de palabra completa o patrón claro
                upperText.contains(Regex("\\b$keyword\\b")) || upperText.contains(keyword)
            }
        }
    }

    private suspend fun runAnalyzeRequest(appSource: String, textPreview: String) {
        val result = localInference.analyzeTextLocally(textPreview)

        // Variables para alimentar el DTO basado en el análisis local
        var localRiskLevel = 1
        var kipiMessage = ""
        var riskDescription = ""
        var isCertain = false

        // 1. EVALUACIÓN Y ACCIÓN LOCAL (Inmediata)
        when {
            result.label == "THREAT" && result.confidence > THREAT_CONFIDENCE_THRESHOLD -> {
                localRiskLevel = 3
                riskDescription = "Alerta crítica: El sistema detectó una amenaza directa en la pantalla."
                kipiMessage = "¡ALERTA! Kipi detectó una amenaza directa en pantalla. Por favor, busca ayuda de un adulto inmediatamente."
                isCertain = true
                showCriticalOverlay(kipiMessage)
            }
            result.label == "HIGH_RISK" && result.confidence > HIGH_RISK_CONFIDENCE_THRESHOLD -> {
                localRiskLevel = 3
                riskDescription = "Alerta grave: Posible intento de reclutamiento o exposición a lenguaje peligroso."
                kipiMessage = "¡CUIDADO! He detectado un posible intento de reclutamiento o lenguaje peligroso. No compartas datos personales."
                isCertain = true
                showCriticalOverlay(kipiMessage)
            }
            result.label == "BULLYING" && result.confidence > BULLYING_CONFIDENCE_THRESHOLD -> {
                localRiskLevel = 2
                riskDescription = "Advertencia: Se detectó lenguaje ofensivo o indicios de ciberacoso."
                kipiMessage = "Kipi detectó lenguaje ofensivo o ciberacoso en esta aplicación. No respondas a las provocaciones."
                isCertain = true
                showWarningOverlay(kipiMessage)
            }
            result.label == "SYMBOLS" && result.confidence > SYMBOLS_CONFIDENCE_THRESHOLD -> {
                localRiskLevel = 1
                riskDescription = "Precaución: Kipi detectó simbología, emojis o notaciones sospechosas."
                kipiMessage = "Kipi detectó símbolos con notación dudosa. Consulta con un adulto antes de contestar."
                isCertain = true
                showWarningOverlay(kipiMessage)
            }
        }

        // 2. COMUNICACIÓN CON EL BACKEND (ALERTA MANUAL)
        if (isCertain) {
            notifyBackend(appSource, riskDescription, localRiskLevel)
        }

    }

    private suspend fun showCriticalOverlay(message: String) {
        lastOverlayShownTime = System.currentTimeMillis()
        withContext(Dispatchers.Main) {
            KipiOverlayManager(applicationContext).showKipiAdvice(message)
        }
    }

    private suspend fun showWarningOverlay(message: String) {
        lastOverlayShownTime = System.currentTimeMillis()
        withContext(Dispatchers.Main) {
            KipiOverlayManager(applicationContext).showKipiAdvice(message)
        }
    }

    /**
     * Función recursiva para recorrer el árbol de vistas de la pantalla.
     * Extrae el texto visible y las descripciones de contenido.
     */
    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val stringBuilder = StringBuilder()

        if (!node.text.isNullOrBlank()) {
            stringBuilder.append(node.text).append("\n")
        }

        if (!node.contentDescription.isNullOrBlank()) {
            stringBuilder.append(node.contentDescription).append("\n")
        }

        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                stringBuilder.append(extractTextFromNode(childNode))
                childNode.recycle()
            }
        }

        return stringBuilder.toString()
    }

    private fun isStreamingApp(packageName: String): Boolean {
        // 1. Verificación rápida por nombres de paquetes de streaming/video
        val monitoredPackages = setOf(
            "com.google.android.youtube", // YouTube
            "com.zhiliaoapp.musically", // TikTok
            "com.zhiliaoapp.musically.go", // TikTok Lite
            "com.netflix.mediaclient",    // Netflix
            "com.disney.disneyplus",      // Disney+
            "com.amazon.avod.thirdpartyclient", // Prime Video
            "tv.twitch.android.app",      // Twitch
            "com.wbd.stream"              // Max
        )
        if (monitoredPackages.contains(packageName)) return true

        // 2. Verificación por categoría del sistema (Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appCategoryCache[packageName]?.let { return it }

            return try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                // Se inspecciona solo CATEGORY_VIDEO
                val isMonitored = appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO
                appCategoryCache[packageName] = isMonitored
                isMonitored
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    override fun onInterrupt() {
        Log.e("AccessibilityExtract", "El servicio de accesibilidad fue interrumpido.")
    }

    override fun onDestroy() {
        localInference.close()
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun notifyBackend(appSource: String, description: String, riskLevel: Int) {
        try {
            val minorId = authManager.getMinorId() ?: MINOR_ID_PROTOTYPE
            val authHeader = "Bearer ${authManager.getApiKey()}"

            val manualRequest = ManualAlertRequest(
                minor_id = minorId,
                is_manual_help = false,
                description = description,
                app_source = appSource,
                risk_level = riskLevel
            )
            RetrofitClient.api.sendManualAlert(authHeader, manualRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando alerta manual (Streaming): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "KipiAccessibility"
        private const val ANALYSIS_INTERVAL_MS = 2000L // Lectura cada 2 segundos
        private const val POST_OVERLAY_COOLDOWN_MS = 10000L // Cooldown de 10 segs post-alerta

        private val CRITICAL_EMOJIS = setOf("🍕", "🥷", "🪖", "🐔", "🐓", "👁️", "🧿", "👺", "👹", "🆖", "🦂")

        private val RADICAL_KEYWORDS = setOf(
            "CJNG",
            "CDN",
            "CDG",
            "CHA🍕",
            "CHAPIZZA",
            "CHAPO",
            "ZETAS",
            "ALUCIN",
            "JALE",
            "CUERNO DE CHIVO"
        )

        private const val MINOR_ID_PROTOTYPE = "123e4567-e89b-12d3-a456-426614174000"
        private const val RISK_LOG_THRESHOLD = 2

        // CAMBIO 1: Constantes actualizadas
        private const val MIN_MEANINGFUL_LENGTH = 15       // era 5
        private const val CACHE_EXPIRATION_MS = 5000L
        private const val MAX_CACHE_ENTRIES = 200          // era 30
        // private const val CLOUD_COOLDOWN_MS = 5000L     // reservado para re-habilitar análisis en nube

        private const val THREAT_CONFIDENCE_THRESHOLD = 0.70
        private const val HIGH_RISK_CONFIDENCE_THRESHOLD = 0.75
        private const val BULLYING_CONFIDENCE_THRESHOLD = 0.80
        private const val SYMBOLS_CONFIDENCE_THRESHOLD = 0.70
        // private const val CLOUD_FALLBACK_CONFIDENCE_THRESHOLD = 0.60  // reservado para re-habilitar análisis en nube

        // CAMBIO 1: SYSTEM_NOISE_PATTERNS ampliado con términos comunes de UI
        private val SYSTEM_NOISE_PATTERNS = listOf(
            "comprobando si hay mensajes",
            "cargando...",
            "actualizando",
            "escribiendo...",
            "suscripciones",
            "patrocinado",
            "inicio",
            "shorts",
            "biblioteca",
            "buscar",
            "editar sugerencia",
            "vistas",
            "hace un momento",
            "reproducir",
            "saltar anuncio"
        )
    }
}