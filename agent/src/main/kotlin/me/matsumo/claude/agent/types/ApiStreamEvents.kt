package me.matsumo.claude.agent.types

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Anthropic API streaming event types, parsed from [StreamEvent.event].
 *
 * Provides type-safe access to the raw [JsonObject] data in stream events,
 * eliminating the need for consumers to manually parse JSON.
 */
public sealed interface ApiStreamEvent {
    /** message_start: message creation with initial usage info. */
    public data class MessageStart(
        val messageId: String?,
        val model: String?,
        val usage: MessageUsage?,
    ) : ApiStreamEvent

    /** content_block_start: a new content block begins. */
    public data class ContentBlockStart(
        val index: Int,
        val contentBlock: JsonObject?,
    ) : ApiStreamEvent

    /** content_block_delta: incremental update to a content block. */
    public data class ContentBlockDelta(
        val index: Int,
        val delta: JsonObject?,
    ) : ApiStreamEvent

    /** content_block_stop: a content block is complete. */
    public data class ContentBlockStop(
        val index: Int,
    ) : ApiStreamEvent

    /** message_delta: message-level update (stop_reason, usage). */
    public data class MessageDelta(
        val delta: JsonObject?,
        val usage: MessageUsage?,
    ) : ApiStreamEvent

    /** message_stop: message is complete. */
    public data object MessageStop : ApiStreamEvent

    /** Unrecognized event type. */
    public data class Unknown(
        val type: String,
        val raw: JsonObject,
    ) : ApiStreamEvent
}

/** Token usage information from message_start / message_delta events. */
public data class MessageUsage(
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheCreationInputTokens: Long = 0L,
    val cacheReadInputTokens: Long = 0L,
)

/**
 * Parse the raw [StreamEvent.event] into a typed [ApiStreamEvent].
 */
public fun StreamEvent.parsed(): ApiStreamEvent = parseApiStreamEvent(this.event)

/**
 * Parse a raw event [JsonObject] into a typed [ApiStreamEvent].
 */
public fun parseApiStreamEvent(event: JsonObject): ApiStreamEvent {
    val type = event["type"]?.jsonPrimitive?.contentOrNull ?: return ApiStreamEvent.Unknown("", event)

    return when (type) {
        "message_start" -> {
            val message = event["message"]?.jsonObject
            ApiStreamEvent.MessageStart(
                messageId = message?.get("id")?.jsonPrimitive?.contentOrNull,
                model = message?.get("model")?.jsonPrimitive?.contentOrNull,
                usage = message?.get("usage")?.jsonObject?.let { parseUsage(it) },
            )
        }

        "content_block_start" -> ApiStreamEvent.ContentBlockStart(
            index = event["index"]?.jsonPrimitive?.int ?: 0,
            contentBlock = event["content_block"]?.jsonObject,
        )

        "content_block_delta" -> ApiStreamEvent.ContentBlockDelta(
            index = event["index"]?.jsonPrimitive?.int ?: 0,
            delta = event["delta"]?.jsonObject,
        )

        "content_block_stop" -> ApiStreamEvent.ContentBlockStop(
            index = event["index"]?.jsonPrimitive?.int ?: 0,
        )

        "message_delta" -> ApiStreamEvent.MessageDelta(
            delta = event["delta"]?.jsonObject,
            usage = event["usage"]?.jsonObject?.let { parseUsage(it) },
        )

        "message_stop" -> ApiStreamEvent.MessageStop

        else -> ApiStreamEvent.Unknown(type, event)
    }
}

private fun parseUsage(usage: JsonObject): MessageUsage = MessageUsage(
    inputTokens = usage["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L,
    outputTokens = usage["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0L,
    cacheCreationInputTokens = usage["cache_creation_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L,
    cacheReadInputTokens = usage["cache_read_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0L,
)
