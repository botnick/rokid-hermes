package com.etdofresh.rokidopenclaw.data

import android.content.Context

/** Hermes gateway connection settings. */
data class HermesSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val sessionId: String = ""
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    companion object {
        // 10.0.2.2 is the host machine from the Android emulator; on the glasses
        // this becomes the LAN IP of the box running Hermes (set in Settings).
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
        sessionId = prefs.getString(KEY_SESSION_ID, "") ?: ""
    )

    fun save(settings: HermesSettings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl.trim())
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_SESSION_ID, settings.sessionId.trim())
            .apply()
    }

    private companion object {
        const val PREFS = "hermes_settings"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_SESSION_ID = "session_id"
    }
}
