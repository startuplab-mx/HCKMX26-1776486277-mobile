package com.richi_mc.kipisafe.data.remote

import com.richi_mc.kipisafe.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface KipiApiService {

    // ==========================================
    // HEALTHCHECK
    // ==========================================
    @GET("health")
    suspend fun checkHealth(): Response<HealthResponse>

    // ==========================================
    // PAIRING (Emparejamiento de dispositivo) -> Solo Debug, verifica que el servidor este deployado
    // NO se utiliza en producción
    // ==========================================
    @POST("api/pairing/generate-code")
    suspend fun generatePairingCode(
        @Body request: GenerateCodeRequest
    ): Response<GenerateCodeResponse>

    @POST("api/pairing/claim")
    suspend fun claimPairing(
        @Body request: ClaimPairingRequest
    ): Response<ClaimPairingResponse>

    // ==========================================
    // ENDPOINTS DE DISPOSITIVO (Android App)
    // Header esperado: "Device <api_key>"
    // ==========================================
    @GET("api/device/me")
    suspend fun getDeviceIdentity(
        @Header("Authorization") authHeader: String
    ): Response<DeviceMeResponse>

    @POST("api/device/heartbeat")
    suspend fun sendHeartbeat(
        @Header("Authorization") authHeader: String,
        @Body request: HeartbeatRequest
    ): Response<BaseKipiResponse>

    @POST("api/device/alerts")
    suspend fun sendDeviceAlert(
        @Header("Authorization") authHeader: String,
        @Body request: DeviceAlertRequest
    ): Response<DeviceAlertResponse>

    @POST("api/device/screen-time/batch")
    suspend fun sendScreenTimeBatch(
        @Header("Authorization") authHeader: String,
        @Body request: ScreenTimeBatchRequest
    ): Response<BatchInsertResponse>

    @POST("api/device/app-events/batch")
    suspend fun sendAppEventsBatch(
        @Header("Authorization") authHeader: String,
        @Body request: AppEventsBatchRequest
    ): Response<BatchInsertResponse>

    // ==========================================
    // ENDPOINTS PADRE / PWA (Heredados/Existentes)
    // Header esperado: "Bearer <access_token>"
    // ==========================================
    @POST("api/notifications/analyze")
    suspend fun analyzeNotifications(
        @Header("Authorization") authHeader: String,
        @Body request: NotificationAnalyzeRequest,
    ): Response<NotificationAnalyzeResponse>

    @POST("api/alerts/manual")
    suspend fun sendManualAlert(
        @Header("Authorization") authHeader: String,
        @Body request: ManualAlertRequest,
    ): Response<ManualAlertResponse>
}