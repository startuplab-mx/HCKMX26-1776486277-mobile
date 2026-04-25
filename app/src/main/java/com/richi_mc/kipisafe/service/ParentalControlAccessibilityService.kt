package com.richi_mc.kipisafe.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.richi_mc.kipisafe.data.local.KipiLocalInference
import com.richi_mc.kipisafe.data.model.NotificationAnalyzeRequest
import com.richi_mc.kipisafe.data.remote.RetrofitClient
import com.richi_mc.kipisafe.ui.overlay.KipiOverlayManager
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ParentalControlAccessibilityService : AccessibilityService() {

    // Corrutines in foreground service.
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val appCategoryCache = mutableMapOf<String, Boolean>()
    private lateinit var localInference: KipiLocalInference

    /** Evita re-analizar el mismo texto en ráfaga (debounce). */
    private val processedCache = ConcurrentHashMap<String, Long>()

    private var lastAnalysisTime = 0L

    override fun onCreate() {
        super.onCreate()
        localInference = KipiLocalInference(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Solo procesar si es una red social o app de mensajería
        if (!isSocialOrMessagingApp(packageName)) return

        // Filter events when screen changes or some text are writed.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnalysisTime < ANALYSIS_INTERVAL_MS) return
            lastAnalysisTime = currentTime

            val rootNode = rootInActiveWindow ?: return
            
            serviceScope.launch {
                val extractedText = extractTextFromNode(rootNode)
                rootNode.recycle()

                val cleanText = cleanExtractedText(extractedText)
                if (isValidForAnalysis(cleanText) && !isDuplicateText(cleanText)) {
                    Log.d(TAG, "Analizando texto de accesibilidad: ${cleanText.take(100)}")
                    
                    if (checkRadicalHeuristics(cleanText)) {
                        showCriticalOverlay("Cuidado, el contenido mostrado puede ser inapropiado, por lo que te recomendamos evitarlo y buscar a un adulto de confianza.")
                        return@launch
                    }
                    
                    runAnalyzeRequest(packageName, cleanText)
                }
            }
        }
    }

    private fun cleanExtractedText(text: String): String {
        return text.split("\n")
            .map { it.trim() }
            .filter { it.length > 2 }
            .distinct()
            .joinToString(". ")
            .replace(Regex("\\s+"), " ")
            .trim()
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

    private fun isValidForAnalysis(text: String): Boolean {
        if (text.isBlank()) return false
        if (checkRadicalHeuristics(text)) return true
        if (CRITICAL_EMOJIS.any { text.contains(it) }) return true
        
        val lower = text.lowercase(Locale.getDefault())
        if (SYSTEM_NOISE_PATTERNS.any { lower.contains(it) }) return false
        
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
        val hasCriticalEmoji = CRITICAL_EMOJIS.any { textPreview.contains(it) }
        val result = localInference.analyzeTextLocally(textPreview)

        when {
            result.label == "THREAT" && result.confidence > THREAT_CONFIDENCE_THRESHOLD -> {
                showCriticalOverlay("¡ALERTA! Kipi detectó una amenaza directa en pantalla. Por favor, busca ayuda de un adulto inmediatamente.")
                return
            }
            result.label == "HIGH_RISK" && result.confidence > HIGH_RISK_CONFIDENCE_THRESHOLD -> {
                showCriticalOverlay("¡CUIDADO! He detectado un posible intento de reclutamiento o lenguaje peligroso. No compartas datos personales.")
                return
            }
            result.label == "BULLYING" && result.confidence > BULLYING_CONFIDENCE_THRESHOLD -> {
                showWarningOverlay("Kipi detectó lenguaje ofensivo o ciberacoso en esta aplicación. No respondas a las provocaciones.")
                return
            }
            result.label == "SYMBOLS" && result.confidence > SYMBOLS_CONFIDENCE_THRESHOLD -> {
                showWarningOverlay("Kipi detectó símbolos con notación dudosa. Consulta con un adulto antes de contestar.")
                return
            }
        }

        if (result.label == "MIXED" || result.confidence < CLOUD_FALLBACK_CONFIDENCE_THRESHOLD || hasCriticalEmoji) {
            val request = NotificationAnalyzeRequest(
                minor_id = MINOR_ID_PROTOTYPE,
                app_source = appSource,
                text_preview = textPreview
            )
            try {
                val response = RetrofitClient.api.analyzeNotifications(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    val riskLevel = body?.analysis?.risk_level ?: 0
                    if (riskLevel >= RISK_LOG_THRESHOLD) {
                        val finalMessage = body?.analysis?.kipi_response ?: "He detectado una situación inusual. Ten cuidado."
                        withContext(Dispatchers.Main) {
                            KipiOverlayManager(applicationContext).showKipiAdvice(finalMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en análisis remoto: ${e.message}")
            }
        }
    }

    private suspend fun showCriticalOverlay(message: String) {
        withContext(Dispatchers.Main) {
            KipiOverlayManager(applicationContext).showKipiAdvice(message)
        }
    }

    private suspend fun showWarningOverlay(message: String) {
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
            stringBuilder.append(node.text).append(" ")
        }

        if (!node.contentDescription.isNullOrBlank()) {
            stringBuilder.append(node.contentDescription).append(" ")
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

    private fun isSocialOrMessagingApp(packageName: String): Boolean {
        // 1. Verificación rápida por nombres de paquetes comunes
        val monitoredPackages = setOf(
            "com.whatsapp",
            "com.facebook.orca", // Messenger
            "com.facebook.mlite", // Messenger Lite
            "com.facebook.katana", // Facebook
            "com.facebook.lite", // Facebook Lite
            "com.instagram.android",
            "com.instagram.lite", // Instagram Lite
            "org.telegram.messenger",
            "com.twitter.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.zhiliaoapp.musically.go", // TikTok Lite
            "com.google.android.apps.messaging", // Google Messages
            "com.discord",
            "com.google.android.youtube", // YouTube
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
                // Se incluye CATEGORY_VIDEO para cubrir otras plataformas de streaming
                val isMonitored = appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL ||
                                 appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO
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

    companion object {
        private const val TAG = "KipiAccessibility"
        private const val ANALYSIS_INTERVAL_MS = 1000L // Lectura cada segundo
        
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
        private const val MIN_MEANINGFUL_LENGTH = 5
        
        private const val CACHE_EXPIRATION_MS = 5000L
        private const val MAX_CACHE_ENTRIES = 30

        private const val THREAT_CONFIDENCE_THRESHOLD = 0.70
        private const val HIGH_RISK_CONFIDENCE_THRESHOLD = 0.75
        private const val BULLYING_CONFIDENCE_THRESHOLD = 0.80
        private const val SYMBOLS_CONFIDENCE_THRESHOLD = 0.70
        private const val CLOUD_FALLBACK_CONFIDENCE_THRESHOLD = 0.60

        private val SYSTEM_NOISE_PATTERNS = listOf(
            "comprobando si hay mensajes",
            "cargando...",
            "actualizando",
            "escribiendo..."
        )
    }
}