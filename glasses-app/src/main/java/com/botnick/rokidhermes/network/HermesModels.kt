package com.botnick.rokidhermes.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible wire models for the Hermes Agent `api_server` platform.
 * Hermes advertises itself as model "hermes-agent" and speaks the standard
 * Chat Completions schema (including SSE streaming chunks).
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

// ----- Non-streaming response -----

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

// ----- Streaming response (SSE: lines of `data: {chunk}`) -----

@Serializable
data class ChatDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ChatStreamChoice(
    val index: Int = 0,
    val delta: ChatDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatStreamChunk(
    val id: String? = null,
    val choices: List<ChatStreamChoice> = emptyList()
)

// ----- GET /v1/models (used for the connection test) -----

@Serializable
data class ModelInfo(val id: String)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList()
)

object Roles {
    const val SYSTEM = "system"
    const val USER = "user"
    const val ASSISTANT = "assistant"
}
