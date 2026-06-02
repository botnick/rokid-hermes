package com.botnick.rokidhermes.data

import android.content.Context
import java.util.UUID

/**
 * Hermes gateway connection settings.
 *
 * [apiKey] is required: the Hermes api_server refuses to start without an
 * API_SERVER_KEY and rejects unauthenticated requests. [memoryKey] is a stable
 * per-install id sent as X-Hermes-Session-Key so Hermes' long-term memory can
 * recognise this device across conversations.
 */
data class HermesSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val memoryKey: String = ""
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    companion object {
        // 10.0.2.2 reaches the host machine from the Android emulator; on the
        // glasses this becomes the LAN IP of the box running Hermes.
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8642/v1"
    }
}

/** Persists [HermesSettings] in SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): HermesSettings = HermesSettings(
        baseUrl = prefs.getString(KEY_BASE_URL, HermesSettings.DEFAULT_BASE_URL)
            ?: HermesSettings.DEFAULT_BASE_URL,
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        memoryKey = ensureMemoryKey()
    )

    fun save(settings: HermesSettings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl.trim())
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_MEMORY_KEY, settings.memoryKey.ifBlank { ensureMemoryKey() })
            .apply()
    }

    /** Returns the stable per-install memory key, generating it on first use. */
    private fun ensureMemoryKey(): String {
        val existing = prefs.getString(KEY_MEMORY_KEY, "") ?: ""
        if (existing.isNotBlank()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_MEMORY_KEY, generated).apply()
        return generated
    }

    private companion object {
        const val PREFS = "hermes_settings"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MEMORY_KEY = "memory_key"
    }
}
