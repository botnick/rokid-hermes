package com.etdofresh.rokidopenclaw.network

import com.etdofresh.rokidopenclaw.data.HermesSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client for a running Hermes Agent gateway exposing the OpenAI-compatible
 * `api_server` platform. Talks to POST {baseUrl}/chat/completions.
 *
 * baseUrl is expected to already include the `/v1` suffix, e.g.
 * `http://192.168.1.50:8642/v1` — matching how Open WebUI / LobeChat point at Hermes.
 */
class HermesClient(private val settings: HermesSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Sends the conversation so far and returns the assistant's reply text.
     * The whole [history] is sent each turn (stateless mode); session continuity
     * is layered on via the X-Hermes-Session-Id header when configured.
     */
    suspend fun chat(history: List<ChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = settings.baseUrl.trimEnd('/') + "/chat/completions"
            val payload = ChatRequest(model = MODEL, messages = history, stream = false)
            val body = json.encodeToString(ChatRequest.serializer(), payload)
                .toRequestBody(JSON_MEDIA_TYPE)

            val builder = Request.Builder().url(url).post(body)
            if (settings.apiKey.isNotBlank()) {
                builder.header("Authorization", "Bearer ${settings.apiKey}")
            }
            if (settings.sessionId.isNotBlank()) {
                builder.header("X-Hermes-Session-Id", settings.sessionId)
            }

            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("HTTP ${response.code}: ${raw.take(200)}")
                    )
                }
                val parsed = json.decodeFromString(ChatResponse.serializer(), raw)
                val reply = parsed.choices.firstOrNull()?.message?.content?.trim()
                if (reply.isNullOrEmpty()) {
                    Result.failure(RuntimeException("Empty reply from Hermes"))
                } else {
                    Result.success(reply)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val MODEL = "hermes-agent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
