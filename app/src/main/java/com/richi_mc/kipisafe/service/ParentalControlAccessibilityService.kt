package com.richi_mc.kipisafe.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class ParentalControlAccessibilityService : AccessibilityService() {

    // Corrutines in foreground service.
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val appCategoryCache = mutableMapOf<String, Boolean>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Solo procesar si es una red social o app de mensajería
        if (!isSocialOrMessagingApp(packageName)) return

        // Filter events when screen changes or some text are writed.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            val rootNode = rootInActiveWindow ?: return
            val packageName = event.packageName?.toString() ?: "Paquete Desconocido"

            serviceScope.launch {
                val extractedText = extractTextFromNode(rootNode)

                rootNode.recycle()

                val cleanText = extractedText.trim()
                if (cleanText.isNotBlank()) {
                    // Aquí es donde inyectarías este texto a tu modelo local (Ej. TensorFlow Lite)
                    Log.d("AccessibilityExtract", "App: $packageName | Texto: $cleanText")

                    // sendToEdgeAI(packageName, cleanText)
                }
            }
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
        val socialPackages = setOf(
            "com.whatsapp",
            "com.facebook.orca", // Messenger
            "com.facebook.katana", // Facebook
            "com.instagram.android",
            "org.telegram.messenger",
            "com.twitter.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.google.android.apps.messaging", // Google Messages
            "com.discord"
        )
        if (socialPackages.contains(packageName)) return true

        // 2. Verificación por categoría del sistema (Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appCategoryCache[packageName]?.let { return it }
            
            return try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val isSocial = appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL
                appCategoryCache[packageName] = isSocial
                isSocial
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
        super.onDestroy()
        serviceJob.cancel()
    }
}