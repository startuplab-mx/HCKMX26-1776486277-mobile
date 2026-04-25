package com.richi_mc.kipisafe.data.model

// --- Respuestas Base ---
data class BaseKipiResponse(
    val ok: Boolean,
    val message: String? = null
)

data class HealthResponse(
    val ok: Boolean,
    val supabase_configured: Boolean?
)

// --- PAIRING ---
data class GenerateCodeRequest(
    val device_model: String? = null,
    val fcm_push_token: String? = null
)

data class GenerateCodeResponse(
    val ok: Boolean,
    val session_id: String,
    val otp: String,
    val expires_at: String,
    val message: String? = null
)

data class ClaimPairingRequest(
    val session_id: String,
    val otp: String
)

data class ClaimPairingResponse(
    val ok: Boolean,
    val device_id: String,
    val minor_id: String,
    val api_key: String
)

// --- DEVICE (Android a Backend) ---
data class DeviceMeResponse(
    val ok: Boolean,
    val device_id: String,
    val minor_id: String
)

data class HeartbeatRequest(
    val battery: Double? = null,
    val status: String? = null, // "online" | "offline"
    val protection_active: Boolean? = null
)

data class DeviceAlertRequest(
    val app_source: String? = "Android",
    val risk_level: Int, // 1, 2, 3
    val confidence_score: Double? = 0.8,
    val sensitive_data_flag: Boolean? = null,
    val description: String? = null
)

data class DeviceAlertResponse(
    val ok: Boolean,
    val alert_id: String,
    val system_action: DeviceSystemAction
)

data class DeviceSystemAction(
    val escalated_to_parent: Boolean,
    val reason: String
)

data class ScreenTimeBatchRequest(
    val rows: List<ScreenTimeRow>
)

data class ScreenTimeRow(
    val app_name: String,
    val category: String,
    val minutes: Double,
    val log_date: String // YYYY-MM-DD
)

data class AppEventsBatchRequest(
    val events: List<AppEventRow>
)

data class AppEventRow(
    val app_name: String,
    val event_type: String, // "installed" | "updated" | "uninstalled"
    val category: String,
    val risk_level: String, // "low" | "medium" | "high"
    val created_at: String? = null // ISO8601
)

data class BatchInsertResponse(
    val ok: Boolean,
    val inserted: Int
)

data class NotificationAnalyzeAnalysis(
    val risk_level: Int,
    val confidence_score: Double,
    val sensitive_data_flag: Boolean,
    val kipi_response: String? = null
)

data class NotificationAnalyzeSystemAction(
    val escalated_to_parent: Boolean,
    val reason: String
)

data class ManualAlertRequest(
    val minor_id: String,
    val app_source: String? = "Manual",
    val risk_level: Int? = 2
)

data class ManualAlertResponse(
    val ok: Boolean,
    val alert_id: String?,
    val message: String?
)

data class NotificationAnalyzeRequest(
    val minor_id: String,
    val text_preview: String,
    val app_source: String? = "Sistema",
    val risk_level: Int? = null,
    val mock_risk_level: Int? = null,
    val confidence_score: Double? = null,
    val sensitive_data_flag: Boolean? = null,
    val kipi_response: String? = null,
    val force_cloud: Boolean? = null,
    val cloud_confidence_threshold: Double? = null,
    val shared_alert_levels: List<Int>? = null
)

// Opcional: Actualizar el Response si quieres capturar el objeto "cloud" que envía el nuevo backend
data class NotificationAnalyzeResponse(
    val ok: Boolean,
    val analysis: NotificationAnalyzeAnalysis,
    val system_action: NotificationAnalyzeSystemAction,
    val cloud: CloudAnalysisInfo?, // Nuevo campo de tu backend
    val alert_id: String?,
    val procesado_en_ms: Int
)

data class CloudAnalysisInfo(
    val error: String? = null,
    val escalated: Boolean? = null
)