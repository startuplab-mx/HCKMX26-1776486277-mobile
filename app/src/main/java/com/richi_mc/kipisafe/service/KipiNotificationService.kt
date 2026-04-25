package com.richi_mc.kipisafe.service

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.richi_mc.kipisafe.data.local.KipiLocalInference
import com.richi_mc.kipisafe.data.model.NotificationAnalyzeRequest
import com.richi_mc.kipisafe.data.remote.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.richi_mc.kipisafe.ui.overlay.KipiOverlayManager
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Escucha notificaciones del sistema, filtra ruido local y envía previews al backend para análisis.
 */
class KipiNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var localInference: KipiLocalInference

    /** Evita re-analizar el mismo preview en ráfaga (actualizaciones de progreso, grupos). */
    private val processedCache = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        localInference = KipiLocalInference(applicationContext)
    }

    override fun onDestroy() {
        localInference.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val textPreview = extractTextPreview(sbn.notification)
        val isCritical = CRITICAL_EMOJIS.any { textPreview.contains(it) }

        if (isCritical) {
            Log.d("KipiSafe", "Simbología de riesgo detectada localmente")
        }

        // Si es un emoji crítico, ignoramos filtros de sistema o resúmenes de grupo para asegurar el análisis
        if (!isCritical) {
            if (shouldIgnoreNotification(sbn)) {
                return
            }

            if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
                Log.d(TAG, "Ignorada: FLAG_GROUP_SUMMARY (resumen de grupo)")
                return
            }
        }

        val appSource = sbn.packageName

        if (!isValidForAnalysis(textPreview)) {
            Log.d(TAG, "Filtrada (no válida para análisis): ${textPreview.take(PREVIEW_LOG_MAX)}")
            return
        }

        if (isDuplicatePreview(textPreview)) {
            Log.d(TAG, "Ignorada (debounce, mismo texto reciente): ${textPreview.take(PREVIEW_LOG_MAX)}")
            return
        }

        Log.i(
            TAG,
            "Notificación capturada | app_source=$appSource | text_preview=${textPreview.take(PREVIEW_LOG_MAX)}",
        )

        // --- INICIO DEL MOCK PARA PRUEBAS ---
        if (textPreview.contains("prueba kipi", ignoreCase = true)) {
            Log.d(TAG, "Mock activado: Lanzando overlay de Kipi")
            Handler(Looper.getMainLooper()).post {
                val overlayManager = KipiOverlayManager(applicationContext)
                overlayManager.showKipiAdvice("¡Hola! Soy Kipi. Este es un mensaje de prueba para ver si mi interfaz flotante funciona correctamente. ¿Me veo bien?")
            }
            return
        }
        // --- FIN DEL MOCK ---

        // Prioridad alta si se detectó simbología de riesgo
        val startMode = if (isCritical) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT
        serviceScope.launch(Dispatchers.IO, start = startMode) {
            runAnalyzeRequest(appSource, textPreview)
        }
    }

    private suspend fun runAnalyzeRequest(appSource: String, textPreview: String) {
        val hasCriticalEmoji = CRITICAL_EMOJIS.any { textPreview.contains(it) }
        val emojiWarrantsIntervention =
            hasCriticalEmoji && hasSubstantiveUserContentForCriticalEmoji(textPreview)
        val result = localInference.analyzeTextLocally(textPreview)

        when {
            // 1. Amenazas directas (Prioridad Máxima)
            result.label == "THREAT" && result.confidence > THREAT_CONFIDENCE_THRESHOLD -> {
                showCriticalOverlay(
                    "¡ALERTA! Kipi detectó una amenaza directa. Por favor, ponte en un lugar seguro y muestra este mensaje a un adulto de confianza inmediatamente."
                )
                return
            }

            // 2. Reclutamiento / Riesgo Alto
            result.label == "HIGH_RISK" && result.confidence > HIGH_RISK_CONFIDENCE_THRESHOLD -> {
                showCriticalOverlay(
                    "¡ALERTA DE SEGURIDAD! He detectado un intento de reclutamiento o códigos peligrosos. No compartas tus datos, ni fotos, ni tu ubicación."
                )
                return
            }

            // 3. Ciberacoso / Bullying
            result.label == "BULLYING" && result.confidence > BULLYING_CONFIDENCE_THRESHOLD -> {
                showWarningOverlay(
                    "Kipi detectó mensajes ofensivos o ciberacoso. Nadie tiene derecho a tratarte así. Recuerda que no es tu culpa; considera bloquear este contacto y hablar con alguien."
                )
                return
            }

            // 4. Pertenencia / Aislamiento (Grooming)
            result.label == "BELONGING" && result.confidence > BELONGING_CONFIDENCE_THRESHOLD -> {
                showWarningOverlay(
                    "Kipi detectó lenguaje inusual. Recuerda que no debes confiar en personas que intentan alejarte de tu familia o hacerte guardar secretos."
                )
                // Aquí no ponemos 'return' por si queremos que el backend también registre el evento
            }

            // 5. Símbolos (Silencioso, solo log)
            result.label == "SYMBOLS" && result.confidence > SYMBOLS_CONFIDENCE_THRESHOLD -> {
                showWarningOverlay("Kipi detectó símbolos con notación dudosa. Consulta con un adulto antes de contestar.")
                return
            }
        }

        // MIXED = señales contradictorias: siempre pedir segunda opinión al backend, sin umbral de confianza.
        if (result.label == "MIXED" || result.confidence < CLOUD_FALLBACK_CONFIDENCE_THRESHOLD) {
            val request = NotificationAnalyzeRequest(
                minor_id = MINOR_ID_PROTOTYPE,
                app_source = appSource,
                text_preview = textPreview,
            )
            try {
                val response = RetrofitClient.api.analyzeNotifications(request)
                val body = if (response.isSuccessful) response.body() else null
                val apiRiskLevel = body?.analysis?.risk_level ?: 0

                if (emojiWarrantsIntervention || apiRiskLevel >= RISK_LOG_THRESHOLD) {
                    val apiResponseText = body?.analysis?.kipi_response.orEmpty()
                    val isRecruitmentRelated = emojiWarrantsIntervention ||
                        apiResponseText.contains("reclutamiento", ignoreCase = true) ||
                        apiResponseText.contains("códigos", ignoreCase = true)

                    if (isRecruitmentRelated) {
                        Log.w(
                            "KipiSafe",
                            "INTERVENCIÓN POR POSIBLE CAPTACIÓN | app=$appSource | emoji=$emojiWarrantsIntervention",
                        )
                    }

                    val finalMessage = if (emojiWarrantsIntervention) {
                        "¡Cuidado! He detectado códigos de alto riesgo. No compartas tu ubicación ni datos personales. Si alguien te pide encontrarte o hacer algo secreto, avisa a un adulto de confianza inmediatamente."
                    } else {
                        apiResponseText.ifEmpty { "He detectado una situación inusual. Ten cuidado con la información que compartes." }
                    }

                    Log.w(TAG, "Alerta Kipi (risk=$apiRiskLevel, emoji=$emojiWarrantsIntervention): $finalMessage")
                    withContext(Dispatchers.Main) {
                        KipiOverlayManager(applicationContext).showKipiAdvice(finalMessage)
                    }
                } else if (!response.isSuccessful) {
                    Log.e(TAG, "API analyze HTTP ${response.code()}: ${response.errorBody()?.string().orEmpty()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error de red o servidor: ${e.message}")
                Log.w(TAG, "Backend no disponible, confiando en análisis local")

                if (emojiWarrantsIntervention) {
                    Log.w(
                        "KipiSafe",
                        "INTERVENCIÓN POR POSIBLE CAPTACIÓN (Fallback Local por Emoji) | app=$appSource",
                    )
                    showCriticalOverlay(
                        "¡Alerta de seguridad! He detectado códigos peligrosos. Por favor, no compartas tu ubicación ni aceptes invitaciones de desconocidos.",
                    )
                }
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

    private fun shouldIgnoreNotification(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName
        if (pkg == applicationContext.packageName) {
            return true
        }
        if (pkg == "android" || pkg == "com.android.systemui") {
            return true
        }
        return isSystemPackageWithoutUserUpdate(pkg)
    }

    private fun isSystemPackageWithoutUserUpdate(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val updatedByUser = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            isSystem && !updatedByUser
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Texto útil para el modelo: descarta resúmenes multimedia genéricos y contenido demasiado corto
     * una vez quitado el remitente típico ("Nombre — cuerpo").
     */
    private fun isValidForAnalysis(text: String): Boolean {
        if (text.isBlank()) return false
        if (isSystemNoise(text) && !CRITICAL_EMOJIS.any { text.contains(it) }) {
            return false
        }
        if (CRITICAL_EMOJIS.any { text.contains(it) }) return true
        val lowered = text.lowercase(Locale.getDefault())
        for (p in DISCARD_PATTERNS) {
            if (lowered.contains(p)) return false
        }
        val withoutSender = textWithoutSender(text)
        return withoutSender.length >= MIN_MEANINGFUL_LENGTH
    }

    /** Notificaciones técnicas de apps (no conversación humana); el clasificador no está entrenado para ellas. */
    private fun isSystemNoise(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return SYSTEM_NOISE_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Evita overlay solo-por-emoji en textos de sistema o previews vacíos ("WhatsApp — Comprobando…").
     * Sí permite intervención si hay cuerpo sustantivo además del símbolo, o si el riesgo viene del API.
     */
    private fun hasSubstantiveUserContentForCriticalEmoji(text: String): Boolean {
        if (isSystemNoise(text)) return false
        val body = textWithoutSender(text)
        return body.length >= MIN_CONTENT_FOR_CRITICAL_EMOJI
    }

    private fun isDuplicatePreview(text: String): Boolean {
        val now = System.currentTimeMillis()
        val last = processedCache.putIfAbsent(text, now)

        if (last != null) {
            if (now - last < CACHE_EXPIRATION_MS) return true
            processedCache.replace(text, last, now)
        }

        if (processedCache.size > MAX_CACHE_ENTRIES) processedCache.clear()
        return false
    }

    /**
     * Orden fijo (WhatsApp y similares):
     * 1. Quitar cabecera de grupo `(N mensaje[s]):` si existe (debe ir antes del separador `—` del título).
     * 2. Quitar prefijo de remitente (`Nombre — …`, `):`, etc.) sobre el texto ya sin cabecera de grupo.
     */
    private fun textWithoutSender(full: String): String {
        var current = full.trim()
        GROUP_MESSAGE_PREFIX.find(current)?.let { match ->
            val after = current.substring(match.range.last + 1).trim()
            if (after.isNotEmpty()) {
                current = after
            }
        }
        for (sep in SENDER_SEPARATORS) {
            val idx = current.indexOf(sep)
            if (idx >= 0) {
                return current.substring(idx + sep.length).trim()
            }
        }
        return current
    }

    private fun extractTextPreview(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ")
            .orEmpty()

        val body = when {
            text.isNotEmpty() -> text
            bigText.isNotEmpty() -> bigText
            lines.isNotEmpty() -> lines
            else -> ""
        }

        return buildString {
            if (title.isNotEmpty()) {
                append(title)
            }
            if (body.isNotEmpty()) {
                if (isNotEmpty()) append(" — ")
                append(body)
            }
        }
    }

    companion object {
        private const val TAG = "KipiNotificationService"

        // 💡 CORRECCIÓN 1: Se unificaron los emojis de Python con los de Kotlin
        private val CRITICAL_EMOJIS = setOf("🍕", "🥷", "🪖", "🐔", "🐓", "👁️", "🧿", "👺", "👹", "🆖", "🦂")

        private const val PREVIEW_LOG_MAX = 500
        private const val MINOR_ID_PROTOTYPE = "123e4567-e89b-12d3-a456-426614174000"  //TODO modificar a uno verdadero, cuando sirva xd
        private const val RISK_LOG_THRESHOLD = 2
        private const val MIN_MEANINGFUL_LENGTH = 2
        private const val MIN_CONTENT_FOR_CRITICAL_EMOJI = 8

        private const val CACHE_EXPIRATION_MS = 2000L
        private const val MAX_CACHE_ENTRIES = 50

        private val SYSTEM_NOISE_PATTERNS = listOf(
            "comprobando si hay mensajes",
            "whatsapp web está activo",
            "copia de seguridad en curso",
            "cargando...",
            "actualizando",
        )

        /** Resúmenes tipo WhatsApp: "Nombre del grupo (4 mensajes): último mensaje…" */
        private val GROUP_MESSAGE_PREFIX = Regex(
            """\s*\(\d+\s*mensajes?\)\s*:\s*""",
            RegexOption.IGNORE_CASE,
        )

        // --- UMBRALES DE CONFIANZA ACTUALIZADOS ---
        private const val THREAT_CONFIDENCE_THRESHOLD = 0.70    // Ligeramente más bajo para no dejar pasar amenazas
        private const val HIGH_RISK_CONFIDENCE_THRESHOLD = 0.75
        private const val BULLYING_CONFIDENCE_THRESHOLD = 0.80  // Alto para evitar falsos positivos con lenguaje coloquial
        private const val BELONGING_CONFIDENCE_THRESHOLD = 0.85
        private const val SYMBOLS_CONFIDENCE_THRESHOLD = 0.70
        private const val CLOUD_FALLBACK_CONFIDENCE_THRESHOLD = 0.60

        // 💡 CORRECCIÓN 2: Se ajustaron para atrapar solo notificaciones multimedia del sistema,
        // permitiendo que palabras como "foto" o "sticker" pasen al análisis si son parte de una oración.
        private val DISCARD_PATTERNS = listOf(
            "mensajes de",
            "📷 foto",
            "🖼️ sticker",
            "🎤 mensaje de voz",
            "🎵 audio",
            "🎥 vídeo",
            "📄 documento"
        )

        private val SENDER_SEPARATORS = listOf(
            " — ",
            " - ",
            "): ", // grupos: "Grupo X): mensaje"
            ") : ",
            ": ",
        )
    }
}
