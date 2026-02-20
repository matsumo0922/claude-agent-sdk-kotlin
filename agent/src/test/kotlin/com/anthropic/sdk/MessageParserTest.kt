package com.anthropic.sdk

import com.anthropic.sdk.errors.CLIJsonDecodeException
import com.anthropic.sdk.errors.MessageParseException
import com.anthropic.sdk.internal.MessageParser
import com.anthropic.sdk.types.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageParserTest {

    @Test
    fun `parse system message with init subtype`() {
        val json = """{"type":"system","subtype":"init","session_id":"sess-123","data":{}}"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<SystemMessage>(msg)
        assertEquals("sess-123", msg.sessionId)
        assertEquals("init", msg.subtype)
        assertTrue(msg.isInit)
    }

    @Test
    fun `parse system message with error subtype`() {
        val json = """{"type":"system","subtype":"error","session_id":"sess-1","data":{}}"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<SystemMessage>(msg)
        assertEquals("error", msg.subtype)
    }

    @Test
    fun `parse assistant message with text block`() {
        val json = """{
            "type": "assistant",
            "session_id": "sess-1",
            "message": {
                "model": "claude-sonnet-4-5-20250514",
                "content": [{"type": "text", "text": "Hello, world!"}]
            }
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<AssistantMessage>(msg)
        assertEquals("claude-sonnet-4-5-20250514", msg.model)
        assertEquals(1, msg.content.size)
        val block = msg.content[0]
        assertIs<TextBlock>(block)
        assertEquals("Hello, world!", block.text)
    }

    @Test
    fun `parse assistant message with thinking block`() {
        val json = """{
            "type": "assistant",
            "session_id": "sess-1",
            "message": {
                "model": "claude-opus-4-20250514",
                "content": [{"type": "thinking", "thinking": "Let me think...", "signature": "sig123"}]
            }
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<AssistantMessage>(msg)
        val block = msg.content[0]
        assertIs<ThinkingBlock>(block)
        assertEquals("Let me think...", block.thinking)
        assertEquals("sig123", block.signature)
    }

    @Test
    fun `parse assistant message with tool_use block`() {
        val json = """{
            "type": "assistant",
            "session_id": "sess-1",
            "message": {
                "model": "claude-sonnet-4-5-20250514",
                "content": [{
                    "type": "tool_use",
                    "id": "tu_123",
                    "name": "Read",
                    "input": {"file_path": "/tmp/test.txt"}
                }]
            }
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<AssistantMessage>(msg)
        val block = msg.content[0]
        assertIs<ToolUseBlock>(block)
        assertEquals("tu_123", block.id)
        assertEquals("Read", block.name)
        assertEquals("/tmp/test.txt", block.input["file_path"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parse assistant message with multiple content blocks`() {
        val json = """{
            "type": "assistant",
            "session_id": "sess-1",
            "message": {
                "model": "claude-sonnet-4-5-20250514",
                "content": [
                    {"type": "thinking", "thinking": "Thinking...", "signature": "sig1"},
                    {"type": "text", "text": "Here is the result"},
                    {"type": "tool_use", "id": "tu_1", "name": "Write", "input": {}}
                ]
            }
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<AssistantMessage>(msg)
        assertEquals(3, msg.content.size)
        assertIs<ThinkingBlock>(msg.content[0])
        assertIs<TextBlock>(msg.content[1])
        assertIs<ToolUseBlock>(msg.content[2])
    }

    @Test
    fun `parse assistant message with error`() {
        val json = """{
            "type": "assistant",
            "session_id": "sess-1",
            "error": "rate_limit",
            "message": {"model": "claude-sonnet-4-5-20250514", "content": []}
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<AssistantMessage>(msg)
        assertEquals(AssistantMessageError.RATE_LIMIT, msg.error)
    }

    @Test
    fun `parse user message with UUID`() {
        val json = """{
            "type": "user",
            "session_id": "sess-1",
            "uuid": "uuid-abc-123",
            "message": {"content": "tool result text"}
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<UserMessage>(msg)
        assertEquals("uuid-abc-123", msg.uuid)
    }

    @Test
    fun `parse user message with parent_tool_use_id`() {
        val json = """{
            "type": "user",
            "session_id": "sess-1",
            "parent_tool_use_id": "tu_parent",
            "message": {"content": "result"}
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<UserMessage>(msg)
        assertEquals("tu_parent", msg.parentToolUseId)
    }

    @Test
    fun `parse result message with all fields`() {
        val json = """{
            "type": "result",
            "session_id": "sess-1",
            "subtype": "success",
            "duration_ms": 5000,
            "duration_api_ms": 3000,
            "is_error": false,
            "num_turns": 3,
            "total_cost_usd": 0.0123,
            "result": "The answer is 42",
            "usage": {"input_tokens": 100, "output_tokens": 50}
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<ResultMessage>(msg)
        assertEquals("success", msg.subtype)
        assertEquals(5000, msg.durationMs)
        assertEquals(3000, msg.durationApiMs)
        assertEquals(false, msg.isError)
        assertEquals(3, msg.numTurns)
        assertEquals(0.0123, msg.totalCostUsd)
        assertEquals("The answer is 42", msg.result)
    }

    @Test
    fun `parse result message with structured output`() {
        val json = """{
            "type": "result",
            "session_id": "sess-1",
            "subtype": "success",
            "duration_ms": 1000,
            "duration_api_ms": 800,
            "is_error": false,
            "num_turns": 1,
            "result": null,
            "structured_output": {"name": "test", "score": 95}
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<ResultMessage>(msg)
        assertNull(msg.result)
        assertTrue(msg.structuredOutput != null)
    }

    @Test
    fun `parse stream event`() {
        val json = """{
            "type": "stream_event",
            "session_id": "sess-1",
            "uuid": "se-uuid-1",
            "event": {"type": "content_block_delta", "delta": {"text": "Hi"}},
            "parent_tool_use_id": null
        }"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Message>(result)
        val msg = result.message
        assertIs<StreamEvent>(msg)
        assertEquals("se-uuid-1", msg.uuid)
    }

    @Test
    fun `control messages are routed separately`() {
        val json = """{"type":"control_response","response":{"subtype":"success","request_id":"req_1","response":{}}}"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Control>(result)
    }

    @Test
    fun `control request messages are routed separately`() {
        val json = """{"type":"control_request","request_id":"req_2","request":{"subtype":"hook_callback"}}"""
        val result = MessageParser.parseLine(json)
        assertIs<MessageParser.ParseResult.Control>(result)
    }

    @Test
    fun `invalid JSON throws CLIJsonDecodeException`() {
        assertFailsWith<CLIJsonDecodeException> {
            MessageParser.parseLine("not valid json {{{")
        }
    }

    @Test
    fun `missing type field throws MessageParseException`() {
        assertFailsWith<MessageParseException> {
            MessageParser.parseLine("""{"data": "no type"}""")
        }
    }

    @Test
    fun `unknown message type throws MessageParseException`() {
        assertFailsWith<MessageParseException> {
            MessageParser.parseLine("""{"type": "unknown_type", "session_id": "s1"}""")
        }
    }

    @Test
    fun `textContent helper extracts all text blocks`() {
        val msg = AssistantMessage(
            sessionId = "s1",
            content = listOf(
                TextBlock("Hello, "),
                ThinkingBlock("...", "sig"),
                TextBlock("world!"),
            ),
            model = "test",
        )
        assertEquals("Hello, world!", msg.textContent())
    }

    // Helper import for jsonPrimitive
    private val kotlinx.serialization.json.JsonElement.jsonPrimitive
        get() = this as kotlinx.serialization.json.JsonPrimitive
}
