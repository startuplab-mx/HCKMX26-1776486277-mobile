package com.richi_mc.kipisafe.data.model

/** DTOs para POST /api/notifications/analyze. Los nombres coinciden con el JSON del backend. */
data class NotificationAnalyzeRequest(
    val minor_id: String,
    val app_source: String,
    val text_preview: String,
)

data class ManualAlertRequest(
    val minor_id: String,
)

data class ManualAlertResponse(
    val ok: Boolean,
    val alert_id: String?,
    val message: String?,
)

data class NotificationAnalyzeResponse(
    val ok: Boolean,
    val analysis: NotificationAnalyzeAnalysis,
    val system_action: NotificationAnalyzeSystemAction,
    val procesado_en_ms: Int,
)

data class NotificationAnalyzeAnalysis(
    val risk_level: Int,
    val confidence_score: Double,
    val sensitive_data_flag: Boolean,
    val kipi_response: String,
)

data class NotificationAnalyzeSystemAction(
    val escalated_to_parent: Boolean,
    val reason: String,
)
