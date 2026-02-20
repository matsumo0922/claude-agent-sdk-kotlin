package me.matsumo.claude.agent.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A message emitted from the Claude Code CLI via the streaming JSON protocol.
 *
 * The hierarchy is sealed so that `when` expressions are exhaustive:
 * ```kotlin
 * when (message) {
 *     is SystemMessage -> ...
 *     is AssistantMessage -> ...
 *     is UserMessage -> ...
 *     is ResultMessage -> ...
 *     is StreamEvent -> ...
 * }
 * ```
 */
@Serializable
public sealed interface SDKMessage {
    /** The session identifier assigned by Claude Code. */
    @SerialName("session_id")
    public val sessionId: String
}

/**
 * Known error types that can occur on assistant messages.
 */
@Serializable
public enum class AssistantMessageError {
    @SerialName("authentication_failed")
    AUTHENTICATION_FAILED,

    @SerialName("billing_error")
    BILLING_ERROR,

    @SerialName("rate_limit")
    RATE_LIMIT,

    @SerialName("invalid_request")
    INVALID_REQUEST,

    @SerialName("server_error")
    SERVER_ERROR,

    @SerialName("unknown")
    UNKNOWN,
}

/**
 * A system message emitted by the CLI (e.g. on session initialization or errors).
 *
 * @param sessionId The session identifier.
 * @param subtype The system message subtype (e.g. `"init"`, `"error"`).
 * @param data Additional metadata for this system event.
 */
@Serializable
@SerialName("system")
public data class SystemMessage(
    @SerialName("session_id")
    override val sessionId: String,
    val subtype: String,
    val data: JsonObject = JsonObject(emptyMap()),
) : SDKMessage {
    /** Whether this is the session initialization message. */
    val isInit: Boolean get() = subtype == "init"
}

/**
 * A message from Claude containing content blocks.
 *
 * @param sessionId The session identifier.
 * @param content The list of content blocks (text, thinking, tool use, etc.).
 * @param model The model identifier used for this response.
 * @param parentToolUseId If this message is a sub-turn inside a tool, the parent tool use ID.
 * @param error An error code if this message represents an API error.
 */
@Serializable
@SerialName("assistant")
public data class AssistantMessage(
    @SerialName("session_id")
    override val sessionId: String,
    val content: List<ContentBlock> = emptyList(),
    val model: String = "",
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null,
    val error: AssistantMessageError? = null,
) : SDKMessage {
    /**
     * Extracts the concatenated text content from all [TextBlock]s in this message.
     */
    public fun textContent(): String =
        content.filterIsInstance<TextBlock>().joinToString("") { it.text }
}

/**
 * A user message, typically representing a tool result sent back to Claude.
 *
 * @param sessionId The session identifier.
 * @param content The message content (either plain text or structured content blocks).
 * @param uuid A unique identifier for this user message, used for file checkpointing.
 * @param parentToolUseId If this is a tool result, the parent tool use ID.
 * @param toolUseResult Raw tool result data, if applicable.
 */
@Serializable
@SerialName("user")
public data class UserMessage(
    @SerialName("session_id")
    override val sessionId: String,
    val content: JsonElement? = null,
    val uuid: String? = null,
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null,
    @SerialName("tool_use_result")
    val toolUseResult: JsonObject? = null,
) : SDKMessage

/**
 * A result message signaling the end of a turn.
 *
 * Contains cost information, token usage, and the final result text.
 *
 * @param sessionId The session identifier.
 * @param subtype The result subtype (e.g. `"success"`, `"error"`, `"max_turns"`, `"interrupted"`).
 * @param durationMs Total wall-clock time for this turn in milliseconds.
 * @param durationApiMs Time spent on API calls in milliseconds.
 * @param isError Whether the turn ended with an error.
 * @param numTurns Number of turns executed.
 * @param totalCostUsd Estimated total cost in USD.
 * @param usage Raw token usage data.
 * @param result The final result text, if available.
 * @param structuredOutput Structured output data when using an output schema.
 */
@Serializable
@SerialName("result")
public data class ResultMessage(
    @SerialName("session_id")
    override val sessionId: String,
    val subtype: String,
    @SerialName("duration_ms")
    val durationMs: Int = 0,
    @SerialName("duration_api_ms")
    val durationApiMs: Int = 0,
    @SerialName("is_error")
    val isError: Boolean = false,
    @SerialName("num_turns")
    val numTurns: Int = 0,
    @SerialName("total_cost_usd")
    val totalCostUsd: Double? = null,
    val usage: JsonObject? = null,
    val result: String? = null,
    @SerialName("structured_output")
    val structuredOutput: JsonElement? = null,
) : SDKMessage

/**
 * A stream event for partial message updates during streaming.
 *
 * Emitted when [ClaudeAgentOptions.includePartialMessages] is `true`.
 *
 * @param sessionId The session identifier.
 * @param uuid A unique identifier for this stream event.
 * @param event The raw Anthropic API stream event data.
 * @param parentToolUseId The parent tool use ID, if applicable.
 */
@Serializable
@SerialName("stream_event")
public data class StreamEvent(
    @SerialName("session_id")
    override val sessionId: String,
    val uuid: String,
    val event: JsonObject,
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null,
) : SDKMessage
