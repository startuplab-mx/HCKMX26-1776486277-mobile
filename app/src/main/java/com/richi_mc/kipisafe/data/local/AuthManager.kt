package com.richi_mc.kipisafe.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = try {
        createSharedPreferences(context)
    } catch (e: Exception) {
        context.deleteSharedPreferences("auth_prefs")
        createSharedPreferences(context)
    }

    private fun createSharedPreferences(context: Context) = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(apiKey: String, minorId: String) {
        sharedPreferences.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MINOR_ID, minorId)
            .apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    fun getMinorId(): String? {
        return sharedPreferences.getString(KEY_MINOR_ID, null)
    }

    fun clearAuth() {
        sharedPreferences.edit()
            .remove(KEY_API_KEY)
            .remove(KEY_MINOR_ID)
            .apply()
    }

    fun isDevicePaired(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MINOR_ID = "minor_id"
    }
}
