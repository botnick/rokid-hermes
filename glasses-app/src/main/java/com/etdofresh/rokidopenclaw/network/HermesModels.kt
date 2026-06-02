package com.etdofresh.rokidopenclaw.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible wire models for the Hermes Agent api_server platform
 * (POST /v1/chat/completions). Hermes advertises itself as model "hermes-agent".
 */

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList()
)

object Roles {
    const val SYSTEM = "system"
    const val USER = "user"
    const val ASSISTANT = "assistant"
}
