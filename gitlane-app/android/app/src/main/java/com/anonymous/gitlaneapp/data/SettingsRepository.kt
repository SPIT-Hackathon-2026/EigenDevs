package com.anonymous.gitlaneapp.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "gitlane_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val standardPrefs = context.getSharedPreferences("gitlane_settings", Context.MODE_PRIVATE)

    fun saveGroqApiKey(key: String) {
        sharedPrefs.edit().putString("groq_api_key", key).apply()
    }

    fun getGroqApiKey(): String? {
        return sharedPrefs.getString("groq_api_key", null)
    }

    fun saveGroqModel(model: String) {
        standardPrefs.edit().putString("groq_model", model).apply()
    }

    fun getGroqModel(): String {
        return standardPrefs.getString("groq_model", "llama3-8b-8192") ?: "llama3-8b-8192"
    }
}
