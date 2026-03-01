package me.matsumo.claude.agent.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A content block within a message.
 *
 * Content blocks represent the individual pieces of content in an assistant or user message.
 * The hierarchy is sealed so that `when` expressions are exhaustive.
 */
@Serializable
public sealed interface ContentBlock

/**
 * A plain text content block.
 *
 * @param text The text content.
 */
@Serializable
@SerialName("text")
public data class TextBlock(
    val text: String,
) : ContentBlock

/**
 * A thinking content block, representing Claude's extended thinking output.
 *
 * @param thinking The thinking text.
 * @param signature A cryptographic signature for the thinking block.
 */
@Serializable
@SerialName("thinking")
public data class ThinkingBlock(
    val thinking: String,
    val signature: String,
) : ContentBlock

/**
 * A tool use content block, representing a request from Claude to invoke a tool.
 *
 * @param id The unique identifier for this tool use invocation.
 * @param name The name of the tool to invoke.
 * @param input The tool input arguments as a JSON object.
 */
@Serializable
@SerialName("tool_use")
public data class ToolUseBlock(
    val id: String,
    val name: String,
    val input: JsonObject,
) : ContentBlock

/**
 * A tool result content block, representing the result of a tool invocation.
 *
 * @param toolUseId The identifier of the [ToolUseBlock] this result corresponds to.
 * @param content The result content as a [JsonElement]. May be a JSON string, a JSON array
 *   of content parts, or `null` if no content is present.
 * @param isError Whether the tool execution resulted in an error.
 */
@Serializable
@SerialName("tool_result")
public data class ToolResultBlock(
    @SerialName("tool_use_id")
    val toolUseId: String,
    val content: JsonElement? = null,
    @SerialName("is_error")
    val isError: Boolean? = null,
) : ContentBlock

/**
 * A compaction content block, containing a summary of the conversation after compaction.
 *
 * When a conversation is compacted (via `/compact` or automatically), the CLI generates
 * a summary and embeds it in a `compaction` content block. This summary replaces the
 * earlier conversation history.
 *
 * @param content The summary text of the compacted conversation.
 */
@Serializable
@SerialName("compaction")
public data class CompactionBlock(
    val content: String,
) : ContentBlock
