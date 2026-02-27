package me.matsumo.claude.agent

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.matsumo.claude.agent.types.ApiStreamEvent
import me.matsumo.claude.agent.types.StreamEvent
import me.matsumo.claude.agent.types.parseApiStreamEvent
import me.matsumo.claude.agent.types.parsed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ApiStreamEventTest {

    @Test
    fun `parses message_start with usage`() {
        val event = buildJsonObject {
            put("type", "message_start")
            put("message", buildJsonObject {
                put("id", "msg_123")
                put("model", "claude-sonnet-4-20250514")
                put("usage", buildJsonObject {
                    put("input_tokens", 100L)
                    put("output_tokens", 0L)
                    put("cache_creation_input_tokens", 50L)
                    put("cache_read_input_tokens", 25L)
                })
            })
        }

        val parsed = parseApiStreamEvent(event)
        assertIs<ApiStreamEvent.MessageStart>(parsed)
        assertEquals("msg_123", parsed.messageId)
        assertEquals("claude-sonnet-4-20250514", parsed.model)
        assertEquals(100L, parsed.usage?.inputTokens)
        assertEquals(50L, parsed.usage?.cacheCreationInputTokens)
        assertEquals(25L, parsed.usage?.cacheReadInputTokens)
    }

    @Test
    fun `parses message_start without usage`() {
        val event = buildJsonObject {
            put("type", "message_start")
            put("message", buildJsonObject {
                put("id", "msg_456")
            })
        }

        val parsed = parseApiStreamEvent(event)
        assertIs<ApiStreamEvent.MessageStart>(parsed)
        assertEquals("msg_456", parsed.messageId)
        assertNull(parsed.usage)
    }

    @Test
    fun `parses content_block_start`() {
        val contentBlock = buildJsonObject {
            put("type", "text")
            put("text", "")
        }
        val event = buildJsonObject {
            put("type", "content_block_start")
            put("index", 0)
            put("content_block", contentBlock)
        }

        val parsed = parseApiStreamEvent(event)
        assertIs<ApiStreamEvent.ContentBlockStart>(parsed)
        assertEquals(0, parsed.index)
        assertEquals(contentBlock, parsed.contentBlock)
    }

    @Test
    fun `parses content_block_delta`() {
        val delta = buildJsonObject {
            put("type", "text_delta")
            put("text", "Hello")
        }
        val event = buildJsonObject {
            put("type", "content_block_delta")
            put("index", 1)
            put("delta", delta)
        }

        val parsed = parseApiStreamEvent(event)
        assertIs<ApiStreamEvent.ContentBlockDelta>(parsed)
        assertEquals(1, parsed.index)
        assertEquals(delta, parsed.delta)
    }

    @Test
    fun `parses content_block_stop`() {
        val event = buildJsonObject {
            put("type", "content_block_stop")
            put("index", 2)
        }

        val parsed = parseApiStreamEvent(event)
        assertIs<ApiStreamEvent.ContentBlockStop>(parsed)
        assertEquals(2, parsed.index)
    }

    @Test
    fun `parses message_delta with usage`() {
        val event = buildJsonObject {
            put("type", "message_delta")
            put("delta", buildJsonObject {
                put("stop_reason", "end_turn")
            })
            put("usage", buildJsonObject {
                put("output_tokens", 42L)
            })
        }

        val parsed = parseApiStreamEvent(event)
        assertIs<ApiStreamEvent.MessageDelta>(parsed)
        assertEquals(42L, parsed.usage?.outputTokens)
    }

    @Test
    fun `parses message_stop`() {
        val event = buildJsonObject {
            put("type", "message_stop")
        }

        val parsed = parseApiStreamEvent(event)
        assertIs<ApiStreamEvent.MessageStop>(parsed)
    }

    @Test
    fun `returns Unknown for unrecognized type`() {
        val event = buildJsonObject {
            put("type", "ping")
            put("data", "keepalive")
        }

        val parsed = parseApiStreamEvent(event)
        assertIs<ApiStreamEvent.Unknown>(parsed)
        assertEquals("ping", parsed.type)
        assertEquals(event, parsed.raw)
    }

    @Test
    fun `StreamEvent parsed() extension works`() {
        val rawEvent = buildJsonObject {
            put("type", "message_stop")
        }
        val streamEvent = StreamEvent(
            sessionId = "session-1",
            uuid = "uuid-1",
            event = rawEvent,
        )

        val parsed = streamEvent.parsed()
        assertIs<ApiStreamEvent.MessageStop>(parsed)
    }
}
