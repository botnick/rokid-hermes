package com.botnick.rokidhermes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.botnick.rokidhermes.data.HermesSettings
import com.botnick.rokidhermes.network.ChatMessage
import com.botnick.rokidhermes.network.HermesClient
import com.botnick.rokidhermes.network.Roles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatStatus { IDLE, LISTENING, CAPTURING, THINKING, STREAMING, ERROR }

/** Real connectivity state of the configured gateway (not just "fields filled in"). */
enum class Reachability { NOT_SET, UNKNOWN, OK, FAILED }

/**
 * Holds the conversation state and bridges the UI to [HermesClient]. Compose-
 * snapshot-backed; created once via remember in MainActivity. Requests run on the
 * supplied [scope] so they can be cancelled (the STOP button) and retried.
 */
class ChatController(initial: HermesSettings, private val scope: CoroutineScope) {

    val messages = mutableStateListOf<ChatMessage>()

    var status by mutableStateOf(ChatStatus.IDLE)
        private set

    /** Short state word shown under the transcript (Listening… / Thinking… / error). */
    var statusText by mutableStateOf("")
        private set

    /** The assistant reply as it streams in (shown live before it's committed). */
    var streamingReply by mutableStateOf("")
        private set

    /** Live partial speech transcript while listening (shown as a forming user bubble). */
    var partial by mutableStateOf("")
        private set

    var configured by mutableStateOf(initial.isConfigured)
        private set

    var reachability by mutableStateOf(
        if (initial.isConfigured) Reachability.UNKNOWN else Reachability.NOT_SET
    )
        private set

    private var client = HermesClient(initial)
    private var sessionId = UUID.randomUUID().toString()
    private var job: Job? = null
    private var onReplyCb: ((String) -> Unit)? = null
    private var systemPrompt = initial.systemPrompt

    // A captured frame waiting to ride along with the next sent turn. Owned here
    // (not in the UI) so it's cleared consistently on every exit path.
    private var pendingImage: String? = null

    fun updateSettings(settings: HermesSettings) {
        client = HermesClient(settings)
        configured = settings.isConfigured
        systemPrompt = settings.systemPrompt
        reachability = if (settings.isConfigured) Reachability.UNKNOWN else Reachability.NOT_SET
    }

    /** Seeds/refreshes the connection dot from a real probe result. */
    fun markReachability(ok: Boolean) {
        reachability = if (ok) Reachability.OK else Reachability.FAILED
    }

    fun newConversation() {
        job?.cancel()
        messages.clear()
        streamingReply = ""
        partial = ""
        pendingImage = null
        sessionId = UUID.randomUUID().toString()
        reset()
    }

    fun setListening() {
        status = ChatStatus.LISTENING
        statusText = "Listening…"
        partial = ""
    }

    fun setCapturing() {
        status = ChatStatus.CAPTURING
        statusText = "Looking…"
        partial = ""
    }

    /** Stages a captured frame to attach to the next sent turn. */
    fun attachImage(dataUrl: String) {
        pendingImage = dataUrl
    }

    fun updatePartial(text: String) {
        if (status == ChatStatus.LISTENING && text.isNotBlank()) partial = text
    }

    fun onError(message: String) {
        status = ChatStatus.ERROR
        statusText = message
        streamingReply = ""
        partial = ""
        pendingImage = null // don't carry an abandoned frame into the next question
    }

    fun reset() {
        status = ChatStatus.IDLE
        statusText = ""
        partial = ""
    }

    /** Cancels the in-flight request (STOP) and returns to idle. */
    fun cancel() {
        job?.cancel()
        job = null
        streamingReply = ""
        pendingImage = null
        status = ChatStatus.IDLE
        statusText = "Stopped"
    }

    /** Whether there's a last user turn awaiting a reply that we can retry. */
    val canRetry: Boolean
        get() = messages.lastOrNull()?.role == Roles.USER

    fun send(userText: String, onReply: (String) -> Unit) {
        onReplyCb = onReply
        partial = ""
        val image = pendingImage
        pendingImage = null
        val turn = if (image != null) {
            ChatMessage.withImage(Roles.USER, userText, image)
        } else {
            ChatMessage.text(Roles.USER, userText)
        }
        messages.add(turn)
        trimHistory()
        runRequest()
    }

    /** Re-runs the request for the last user turn (after an error). */
    fun retry(onReply: (String) -> Unit) {
        onReplyCb = onReply
        if (canRetry) runRequest()
    }

    private fun runRequest() {
        status = ChatStatus.THINKING
        statusText = "Hermes is thinking…"
        streamingReply = ""
        // Send only the most recent window — bounds request size / tokens for a long
        // chat. A language system-nudge (if any) is prepended but never displayed.
        val window = messages.toList().takeLast(MAX_CONTEXT)
        // Keep an image only on the most recent turn so old frames don't bloat the payload.
        val lastIndex = window.lastIndex
        val pruned = window.mapIndexed { i, m ->
            if (i != lastIndex && m.hasImage) ChatMessage.text(m.role, m.displayText) else m
        }
        val outbound = if (systemPrompt.isNotBlank()) {
            listOf(ChatMessage.text(Roles.SYSTEM, systemPrompt)) + pruned
        } else {
            pruned
        }
        job = scope.launch {
            client.streamChat(outbound, sessionId) { piece ->
                status = ChatStatus.STREAMING
                streamingReply += piece
            }.onSuccess { full ->
                reachability = Reachability.OK
                messages.add(ChatMessage.text(Roles.ASSISTANT, full))
                trimHistory()
                streamingReply = ""
                reset()
                onReplyCb?.invoke(full)
            }.onFailure { e ->
                val msg = e.message ?: ""
                when {
                    // The agent had nothing to say — show a calm assistant turn, not a ⚠ error.
                    msg.contains("Empty reply", true) || msg.contains("Empty response", true) -> {
                        messages.add(ChatMessage.text(Roles.ASSISTANT, "(no response — try rephrasing)"))
                        trimHistory()
                        streamingReply = ""
                        reset()
                    }
                    else -> {
                        if (isConnectionError(msg)) reachability = Reachability.FAILED
                        streamingReply = ""
                        onError(friendlyError(msg))
                    }
                }
            }
        }
    }

    private fun isConnectionError(msg: String): Boolean =
        msg.contains("ConnectException", true) || msg.contains("Failed to connect", true) ||
            msg.contains("UnknownHost", true) || msg.contains("Unable to resolve host", true) ||
            msg.contains("timeout", true) || msg.contains("401") || msg.contains("403") ||
            msg.contains("key rejected", true)

    /** Turns raw network/HTTP errors into a short, actionable HUD message. */
    private fun friendlyError(msg: String): String = when {
        msg.contains("401") || msg.contains("403") || msg.contains("key rejected", true) ->
            "API key rejected — check it in Settings"
        msg.contains("ConnectException", true) || msg.contains("Failed to connect", true) ||
            msg.contains("UnknownHost", true) || msg.contains("Unable to resolve host", true) ->
            "Can't reach Hermes — check the URL & WiFi"
        msg.contains("timeout", true) ->
            "Hermes timed out — the server may be down"
        msg.contains("404") ->
            "Not found — make sure the URL ends with /v1"
        else -> msg.take(80)
    }

    /** Bounds in-memory transcript so a very long session can't grow the heap forever. */
    private fun trimHistory() {
        while (messages.size > MAX_KEPT) messages.removeAt(0)
    }

    private companion object {
        const val MAX_CONTEXT = 40 // messages sent per request (≈20 turns of context)
        const val MAX_KEPT = 200   // hard cap on messages held in memory
    }
}
