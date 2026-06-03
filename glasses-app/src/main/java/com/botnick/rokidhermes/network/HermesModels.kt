package com.botnick.rokidhermes.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * OpenAI-compatible wire models for the Hermes Agent `api_server` platform.
 * Hermes advertises itself as model "hermes-agent" and speaks the standard
 * Chat Completions schema (text + multimodal content, plus SSE streaming chunks).
 */

// ----- Message content: either a bare string (text) or an array of parts (multimodal) -----

@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrl? = null
)

@Serializable
data class ImageUrl(val url: String)

@Serializable(with = MessageContentSerializer::class)
sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Parts(val parts: List<ContentPart>) : MessageContent
}

/** The plain-text view of any content (first text part for multimodal turns). */
val MessageContent.asText: String
    get() = when (this) {
        is MessageContent.Text -> text
        is MessageContent.Parts -> parts.firstNotNullOfOrNull { it.text } ?: ""
    }

/**
 * Serializes [MessageContent.Text] as a JSON string and [MessageContent.Parts] as a
 * JSON array — matching OpenAI's `content` field, which is either form. On decode,
 * a string becomes Text and an array becomes Parts (so assistant replies, which are
 * plain strings, round-trip correctly).
 */
object MessageContentSerializer : KSerializer<MessageContent> {
    private val partsSerializer = ListSerializer(ContentPart.serializer())
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MessageContent) {
        val json = encoder as JsonEncoder
        when (value) {
            is MessageContent.Text -> json.encodeJsonElement(JsonPrimitive(value.text))
            is MessageContent.Parts ->
                json.encodeSerializableValue(partsSerializer, value.parts)
        }
    }

    override fun deserialize(decoder: Decoder): MessageContent {
        val json = decoder as JsonDecoder
        return when (val element = json.decodeJsonElement()) {
            is JsonArray -> MessageContent.Parts(
                json.json.decodeFromJsonElement(partsSerializer, element)
            )
            is JsonPrimitive -> MessageContent.Text(element.content)
            else -> MessageContent.Text("")
        }
    }
}

@Serializable
data class ChatMessage(
    val role: String,
    val content: MessageContent
) {
    /** Text shown in the HUD transcript. */
    val displayText: String get() = content.asText

    /** Whether this turn carries an image attachment. */
    val hasImage: Boolean
        get() {
            val c = content
            return c is MessageContent.Parts && c.parts.any { it.imageUrl != null }
        }

    companion object {
        fun text(role: String, text: String) = ChatMessage(role, MessageContent.Text(text))

        fun withImage(role: String, text: String, imageDataUrl: String) = ChatMessage(
            role,
            MessageContent.Parts(buildList {
                if (text.isNotBlank()) add(ContentPart(type = "text", text = text))
                add(ContentPart(type = "image_url", imageUrl = ImageUrl(imageDataUrl)))
            })
        )
    }
}

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
