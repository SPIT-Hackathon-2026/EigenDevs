package com.anonymous.gitlaneapp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores GitHub / GitLab / Bitbucket Personal Access Tokens securely
 * using Android EncryptedSharedPreferences (AES-256).
 *
 * Key format: "pat_<host>"  e.g. "pat_github.com"
 */
class CredentialsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "gitlane_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Save a PAT for a host (e.g. "github.com"). */
    fun savePat(host: String, token: String) {
        prefs.edit().putString(patKey(host), token).apply()
    }

    /** Retrieve a PAT for a host. Returns null if not set. */
    fun getPat(host: String): String? = prefs.getString(patKey(host), null)

    /** Remove a PAT for a host. */
    fun deletePat(host: String) {
        prefs.edit().remove(patKey(host)).apply()
    }

    /** Retrieve a PAT by inspecting the remote URL's hostname. */
    fun getPatForUrl(remoteUrl: String): String? {
        val host = extractHost(remoteUrl) ?: return null
        return getPat(host)
    }

    /** Returns all saved host → masked-token pairs for display. */
    fun listAll(): List<Pair<String, String>> {
        return prefs.all
            .filter { it.key.startsWith("pat_") }
            .map { (key, value) ->
                val host  = key.removePrefix("pat_")
                val token = value as? String ?: ""
                val masked = if (token.length > 8)
                    token.take(4) + "••••" + token.takeLast(4)
                else "••••••••"
                host to masked
            }
    }

    fun hasAnyToken(): Boolean = prefs.all.any { it.key.startsWith("pat_") }

    /** Clear all stored credentials. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun patKey(host: String) = "pat_$host"

    companion object {
        /** Extract the hostname from an HTTPS git URL. */
        fun extractHost(url: String): String? {
            return try {
                when {
                    url.startsWith("https://") -> {
                        val after = url.removePrefix("https://")
                        after.substringBefore("/")
                    }
                    url.startsWith("http://") -> {
                        val after = url.removePrefix("http://")
                        after.substringBefore("/")
                    }
                    else -> null
                }
            } catch (e: Exception) { null }
        }

        /** Determine the service name from a host string. */
        fun serviceLabel(host: String): String = when {
            host.contains("github")    -> "GitHub"
            host.contains("gitlab")    -> "GitLab"
            host.contains("bitbucket") -> "Bitbucket"
            else                       -> host
        }
    }
}
