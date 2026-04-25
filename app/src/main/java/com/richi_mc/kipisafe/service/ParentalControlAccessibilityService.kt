package com.richi_mc.kipisafe.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class ParentalControlAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val appCategoryCache = mutableMapOf<String, Boolean>()
    
    private var lastAnalyzedText = ""
    private var analysisJob: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (!isSocialOrMessagingApp(packageName)) return

        // Solo iniciamos el ciclo de análisis si no hay uno activo para esta app
        if (analysisJob == null || analysisJob?.isCompleted == true) {
            startAnalysisCycle(packageName)
        }
    }

    private fun startAnalysisCycle(packageName: String) {
        analysisJob = serviceScope.launch {
            while (isActive) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val extractedText = StringBuilder()
                    extractLeafText(rootNode, extractedText)
                    rootNode.recycle()

                    val cleanText = extractedText.toString().trim().replace("\\s+".toRegex(), " ")
                    
                    if (cleanText.isNotBlank() && cleanText != lastAnalyzedText) {
                        lastAnalyzedText = cleanText
                        
                        Log.d("AccessibilityExtract", """
                            
                            -------------------------
                            App: ${getAppName(packageName)}
                            Analisis: "$cleanText"
                            -------------------------
                        """.trimIndent())

                        analyzeLocal(cleanText, packageName)
                    }
                }
                delay(3000)
            }
        }
    }

    private fun extractLeafText(node: AccessibilityNodeInfo?, outText: StringBuilder) {
        if (node == null) return

        if (node.childCount == 0) {
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            if (!text.isNullOrBlank()) {
                outText.append(text).append(" ")
            }
        } else {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                extractLeafText(child, outText)
                child?.recycle()
            }
        }
    }

    private fun analyzeLocal(text: String, packageName: String) {
        // Placeholder para integración con IA
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.split(".").last().replaceFirstChar { it.uppercase() }
        }
    }

    private fun isSocialOrMessagingApp(packageName: String): Boolean {
        val monitoredPackages = setOf(
            "com.whatsapp", "com.facebook.orca", "com.facebook.katana", 
            "com.instagram.android", "org.telegram.messenger", "com.twitter.android",
            "com.snapchat.android", "com.zhiliaoapp.musically", "com.google.android.apps.messaging",
            "com.discord", "com.google.android.youtube", "com.netflix.mediaclient",
            "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "tv.twitch.android.app",
            "com.wbd.stream"
        )
        if (monitoredPackages.contains(packageName)) return true

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appCategoryCache[packageName]?.let { return it }
            return try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
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
        Log.e("AccessibilityExtract", "Servicio interrumpido.")
        analysisJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
