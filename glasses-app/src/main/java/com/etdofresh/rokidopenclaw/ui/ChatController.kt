package com.etdofresh.rokidopenclaw.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.etdofresh.rokidopenclaw.data.HermesSettings
import com.etdofresh.rokidopenclaw.network.ChatMessage
import com.etdofresh.rokidopenclaw.network.HermesClient
import com.etdofresh.rokidopenclaw.network.Roles

enum class ChatStatus { IDLE, LISTENING, THINKING, ERROR }

/**
 * Holds the conversation state and bridges the UI to [HermesClient]. Plain
 * Compose-snapshot-backed holder; instantiated once via remember in MainActivity.
 */
class ChatController(initial: HermesSettings) {

    val messages = mutableStateListOf<ChatMessage>()

    var status by mutableStateOf(ChatStatus.IDLE)
        private set

    var statusText by mutableStateOf(DEFAULT_HINT)
        private set

    private var client = HermesClient(initial)

    fun updateSettings(settings: HermesSettings) {
        client = HermesClient(settings)
    }

    fun setListening() {
        status = ChatStatus.LISTENING
        statusText = "Listening…"
    }

    fun setPartial(text: String) {
        if (text.isNotBlank()) statusText = text
    }

    fun onError(message: String) {
        status = ChatStatus.ERROR
        statusText = message
    }

    fun reset() {
        status = ChatStatus.IDLE
        statusText = DEFAULT_HINT
    }

    /**
     * Appends [userText], sends the full history to Hermes, appends the reply,
     * and invokes [onReply] with the assistant text on success.
     */
    suspend fun send(userText: String, onReply: (String) -> Unit) {
        messages.add(ChatMessage(Roles.USER, userText))
        status = ChatStatus.THINKING
        statusText = "Thinking…"

        client.chat(messages.toList())
            .onSuccess { reply ->
                messages.add(ChatMessage(Roles.ASSISTANT, reply))
                reset()
                onReply(reply)
            }
            .onFailure { e ->
                onError(e.message ?: "Request failed")
            }
    }

    companion object {
        const val DEFAULT_HINT = "Tap to talk"
    }
}
