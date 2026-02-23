package me.matsumo.claude.agent.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.matsumo.claude.agent.errors.CLIJsonDecodeException
import me.matsumo.claude.agent.errors.MessageParseException
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.AssistantMessageError
import me.matsumo.claude.agent.types.ContentBlock
import me.matsumo.claude.agent.types.ResultMessage
import me.matsumo.claude.agent.types.SDKMessage
import me.matsumo.claude.agent.types.StreamEvent
import me.matsumo.claude.agent.types.SystemMessage
import me.matsumo.claude.agent.types.TextBlock
import me.matsumo.claude.agent.types.ThinkingBlock
import me.matsumo.claude.agent.types.ToolResultBlock
import me.matsumo.claude.agent.types.ToolUseBlock
import me.matsumo.claude.agent.types.UserMessage

/**
 * Parses JSON lines from the Claude Code CLI into typed [SDKMessage] objects.
 *
 * The CLI outputs newline-delimited JSON. Each line is a JSON object with a `type`
 * field that determines the message kind. For `"assistant"` and `"user"` messages,
 * the actual message content is nested under a `"message"` key.
 *
 * Control messages (type starting with `"control_"`) are not [SDKMessage]s and are
 * handled separately via [parseControlMessage].
 */
internal object MessageParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Result of parsing a JSON line from the CLI.
     *
     * Either an [SDKMessage] or a [ControlMessage] (for control protocol messages).
     */
    sealed interface ParseResult {
        data class Message(val message: SDKMessage) : ParseResult
        data class Control(val controlMessage: JsonObject) : ParseResult
    }

    /**
     * Parses a single JSON line from the CLI output.
     *
     * @param line The raw JSON string.
     * @return A [ParseResult] wrapping either an [SDKMessage] or a control message.
     * @throws CLIJsonDecodeException if the line is not valid JSON.
     * @throws MessageParseException if the JSON cannot be mapped to a known message type.
     */
    fun parseLine(line: String): ParseResult {
        val jsonObject = try {
            json.parseToJsonElement(line).jsonObject
        } catch (e: Exception) {
            throw CLIJsonDecodeException(line, e)
        }

        val type = jsonObject["type"]?.jsonPrimitive?.contentOrNull
            ?: throw MessageParseException(line)

        // Control messages are handled separately
        if (type.startsWith("control_")) {
            return ParseResult.Control(jsonObject)
        }

        return ParseResult.Message(parseMessage(jsonObject, type, line))
    }

    /**
     * Parses a JSON object into a typed [SDKMessage] based on the `type` field.
     */
    private fun parseMessage(data: JsonObject, type: String, rawLine: String): SDKMessage {
        return when (type) {
            "system" -> parseSystemMessage(data, rawLine)
            "assistant" -> parseAssistantMessage(data, rawLine)
            "user" -> parseUserMessage(data, rawLine)
            "result" -> parseResultMessage(data, rawLine)
            "stream_event" -> parseStreamEvent(data, rawLine)
            else -> throw MessageParseException("Unknown message type: $type", rawLine)
        }
    }

    private fun parseSystemMessage(data: JsonObject, rawLine: String): SystemMessage {
        try {
            val sessionId = data.sessionId()
            val subtype = data.requireString("subtype")
            return SystemMessage(
                sessionId = sessionId,
                subtype = subtype,
                data = data,
            )
        } catch (e: MessageParseException) {
            throw e
        } catch (e: Exception) {
            throw MessageParseException("Missing required field in system message: ${e.message}", rawLine)
        }
    }

    private fun parseAssistantMessage(data: JsonObject, rawLine: String): AssistantMessage {
        try {
            val sessionId = data.sessionId()
            val parentToolUseId = data.optionalString("parent_tool_use_id")
            val error = data["error"]?.jsonPrimitive?.contentOrNull?.let { errorStr ->
                try {
                    json.decodeFromJsonElement<AssistantMessageError>(JsonPrimitive(errorStr))
                } catch (_: Exception) {
                    null
                }
            }

            val uuid = data.optionalString("uuid")

            val messageObj = data["message"]?.jsonObject
                ?: throw MessageParseException("Assistant message missing 'message' field", rawLine)

            val model = messageObj["model"]?.jsonPrimitive?.contentOrNull ?: ""
            val contentArray = messageObj["content"]?.jsonArray ?: JsonArray(emptyList())
            val contentBlocks = contentArray.map { parseContentBlock(it.jsonObject) }

            return AssistantMessage(
                sessionId = sessionId,
                content = contentBlocks,
                model = model,
                parentToolUseId = parentToolUseId,
                uuid = uuid,
                error = error,
            )
        } catch (e: MessageParseException) {
            throw e
        } catch (e: Exception) {
            throw MessageParseException("Missing required field in assistant message: ${e.message}", rawLine)
        }
    }

    private fun parseUserMessage(data: JsonObject, rawLine: String): UserMessage {
        try {
            val sessionId = data.sessionId()
            val uuid = data.optionalString("uuid")
            val parentToolUseId = data.optionalString("parent_tool_use_id")
            val toolUseResult = data["tool_use_result"]?.jsonObject

            val messageObj = data["message"]?.jsonObject
            val content = messageObj?.get("content")

            return UserMessage(
                sessionId = sessionId,
                content = content,
                uuid = uuid,
                parentToolUseId = parentToolUseId,
                toolUseResult = toolUseResult,
            )
        } catch (e: MessageParseException) {
            throw e
        } catch (e: Exception) {
            throw MessageParseException("Missing required field in user message: ${e.message}", rawLine)
        }
    }

    private fun parseResultMessage(data: JsonObject, rawLine: String): ResultMessage {
        try {
            return ResultMessage(
                sessionId = data.sessionId(),
                subtype = data.requireString("subtype"),
                durationMs = data["duration_ms"]?.jsonPrimitive?.int ?: 0,
                durationApiMs = data["duration_api_ms"]?.jsonPrimitive?.int ?: 0,
                isError = data["is_error"]?.jsonPrimitive?.boolean ?: false,
                numTurns = data["num_turns"]?.jsonPrimitive?.int ?: 0,
                totalCostUsd = data["total_cost_usd"]?.jsonPrimitive?.doubleOrNull,
                usage = data["usage"]?.jsonObject,
                result = data["result"]?.jsonPrimitive?.contentOrNull,
                structuredOutput = data["structured_output"],
            )
        } catch (e: MessageParseException) {
            throw e
        } catch (e: Exception) {
            throw MessageParseException("Missing required field in result message: ${e.message}", rawLine)
        }
    }

    private fun parseStreamEvent(data: JsonObject, rawLine: String): StreamEvent {
        try {
            return StreamEvent(
                sessionId = data.sessionId(),
                uuid = data.requireString("uuid"),
                event = data["event"]?.jsonObject
                    ?: throw MessageParseException("Stream event missing 'event' field", rawLine),
                parentToolUseId = data.optionalString("parent_tool_use_id"),
            )
        } catch (e: MessageParseException) {
            throw e
        } catch (e: Exception) {
            throw MessageParseException("Missing required field in stream_event message: ${e.message}", rawLine)
        }
    }

    /**
     * Parses a single content block JSON object into a typed [ContentBlock].
     */
    private fun parseContentBlock(block: JsonObject): ContentBlock {
        return when (val blockType = block["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> TextBlock(
                text = block["text"]?.jsonPrimitive?.content ?: "",
            )
            "thinking" -> ThinkingBlock(
                thinking = block["thinking"]?.jsonPrimitive?.content ?: "",
                signature = block["signature"]?.jsonPrimitive?.content ?: "",
            )
            "tool_use" -> ToolUseBlock(
                id = block["id"]?.jsonPrimitive?.content ?: "",
                name = block["name"]?.jsonPrimitive?.content ?: "",
                input = block["input"]?.jsonObject ?: JsonObject(emptyMap()),
            )
            "tool_result" -> ToolResultBlock(
                toolUseId = block["tool_use_id"]?.jsonPrimitive?.content ?: "",
                content = block["content"],
                isError = block["is_error"]?.jsonPrimitive?.booleanOrNull,
            )
            else -> TextBlock(text = "[unknown block type: $blockType]")
        }
    }

    // --- JSON helpers ---

    private fun JsonObject.sessionId(): String =
        this["session_id"]?.jsonPrimitive?.contentOrNull ?: ""

    private fun JsonObject.requireString(key: String): String =
        this[key]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing required field: $key")

    private fun JsonObject.optionalString(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}

/**
 * Parse a single JSONL transcript line into an [SDKMessage], or null if unparseable.
 */
public fun parseTranscriptLine(line: String): SDKMessage? {
    if (line.isBlank()) return null
    return try {
        (MessageParser.parseLine(line) as? MessageParser.ParseResult.Message)?.message
    } catch (_: Exception) {
        null
    }
}
