package com.botnick.rokidhermes.network

import com.botnick.rokidhermes.data.HermesSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Client for a running Hermes Agent gateway exposing the OpenAI-compatible
 * `api_server` platform.
 *
 * - [testConnection]  GET  {baseUrl}/models       — verify URL + API key reach Hermes
 * - [streamChat]      POST {baseUrl}/chat/completions (stream=true) — token-by-token reply
 *
 * baseUrl must include the `/v1` suffix, e.g. `http://192.168.1.50:8642/v1`.
 * The api_server requires a Bearer key (it refuses to start without one), so
 * [HermesSettings.apiKey] is always sent. Session continuity rides on
 * `X-Hermes-Session-Id`; long-term memory scoping on `X-Hermes-Session-Key`.
 */
class HermesClient(private val settings: HermesSettings) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Short timeouts: a quick reachability/auth probe. */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Idle-read timeout (gap between streamed chunks), not a total cap: a long
    // reply keeps streaming tokens, but a hung/stalled gateway eventually errors
    // instead of pinning the UI forever.
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Verifies the gateway is reachable and the key is accepted. Returns model ids. */
    suspend fun testConnection(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = baseRequest("/models").get().build()
            probeClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(httpError(response.code, raw))
                }
                val models = json.decodeFromString(ModelsResponse.serializer(), raw)
                Result.success(models.data.map { it.id })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Streams the assistant reply. [onDelta] is invoked (on an IO thread) for each
     * token chunk; the accumulated full text is returned on success.
     */
    suspend fun streamChat(
        history: List<ChatMessage>,
        sessionId: String,
        onDelta: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = ChatRequest(model = MODEL, messages = history, stream = true)
            val body = json.encodeToString(ChatRequest.serializer(), payload)
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = baseRequest("/chat/completions", sessionId)
                .header("Accept", "text/event-stream")
                .post(body)
                .build()

            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    return@withContext Result.failure(httpError(response.code, raw))
                }
                val source = response.body?.source()
                    ?: return@withContext Result.failure(RuntimeException("Empty response"))

                val full = StringBuilder()
                while (!source.exhausted()) {
                    // Cooperative cancellation: stop reading promptly when the
                    // caller's coroutine is cancelled (e.g. the user taps STOP).
                    if (!coroutineContext.isActive) break
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data:")) continue
                    val data = line.substringAfter("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = try {
                        json.decodeFromString(ChatStreamChunk.serializer(), data)
                    } catch (e: Exception) {
                        continue // skip keep-alives / non-JSON frames
                    }
                    val piece = chunk.choices.firstOrNull()?.delta?.content
                    if (!piece.isNullOrEmpty()) {
                        full.append(piece)
                        onDelta(piece)
                    }
                }

                val text = full.toString().trim()
                if (text.isEmpty()) {
                    Result.failure(RuntimeException("Empty reply from Hermes"))
                } else {
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun baseRequest(path: String, sessionId: String = ""): Request.Builder {
        val url = settings.baseUrl.trimEnd('/') + path
        val builder = Request.Builder().url(url)
        if (settings.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${settings.apiKey}")
        }
        if (sessionId.isNotBlank()) {
            builder.header("X-Hermes-Session-Id", sessionId)
        }
        if (settings.memoryKey.isNotBlank()) {
            builder.header("X-Hermes-Session-Key", settings.memoryKey)
        }
        return builder
    }

    private fun httpError(code: Int, raw: String): RuntimeException {
        val hint = when (code) {
            401, 403 -> "API key rejected"
            404 -> "Endpoint not found — check the URL ends with /v1"
            else -> raw.take(160).ifBlank { "HTTP $code" }
        }
        return RuntimeException("HTTP $code: $hint")
    }

    companion object {
        const val MODEL = "hermes-agent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
