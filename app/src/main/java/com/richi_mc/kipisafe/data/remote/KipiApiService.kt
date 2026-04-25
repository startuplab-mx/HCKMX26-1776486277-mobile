package com.richi_mc.kipisafe.data.remote

import com.richi_mc.kipisafe.data.model.ManualAlertRequest
import com.richi_mc.kipisafe.data.model.ManualAlertResponse
import com.richi_mc.kipisafe.data.model.NotificationAnalyzeRequest
import com.richi_mc.kipisafe.data.model.NotificationAnalyzeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface KipiApiService {

    @POST("api/notifications/analyze")
    suspend fun analyzeNotifications(
        @Body request: NotificationAnalyzeRequest,
    ): Response<NotificationAnalyzeResponse>

    @POST("api/alerts/manual")
    suspend fun sendManualAlert(
        @Body request: ManualAlertRequest,
    ): Response<ManualAlertResponse>
}
