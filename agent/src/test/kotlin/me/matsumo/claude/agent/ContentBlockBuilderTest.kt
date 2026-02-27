package me.matsumo.claude.agent

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.matsumo.claude.agent.types.contentBlocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentBlockBuilderTest {

    @Test
    fun `builds text block`() {
        val blocks = contentBlocks {
            text("Hello, Claude!")
        }

        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals("text", block["type"]?.jsonPrimitive?.content)
        assertEquals("Hello, Claude!", block["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `builds image block with base64 source`() {
        val blocks = contentBlocks {
            image(mediaType = "image/png", data = "iVBOR...")
        }

        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals("image", block["type"]?.jsonPrimitive?.content)

        val source = block["source"]?.jsonObject!!
        assertEquals("base64", source["type"]?.jsonPrimitive?.content)
        assertEquals("image/png", source["media_type"]?.jsonPrimitive?.content)
        assertEquals("iVBOR...", source["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun `builds document block with fileName`() {
        val blocks = contentBlocks {
            document(mediaType = "application/pdf", data = "JVBERi...", fileName = "report.pdf")
        }

        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals("document", block["type"]?.jsonPrimitive?.content)
        assertEquals("report.pdf", block["title"]?.jsonPrimitive?.content)

        val source = block["source"]?.jsonObject!!
        assertEquals("base64", source["type"]?.jsonPrimitive?.content)
        assertEquals("application/pdf", source["media_type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `builds document block without fileName`() {
        val blocks = contentBlocks {
            document(mediaType = "application/pdf", data = "JVBERi...")
        }

        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertNull(block["title"])
    }

    @Test
    fun `builds multiple mixed blocks including raw`() {
        val customBlock = buildJsonObject {
            put("type", "custom")
            put("data", "some-data")
        }

        val blocks = contentBlocks {
            text("First message")
            image(mediaType = "image/jpeg", data = "/9j/4AAQ...")
            raw(customBlock)
        }

        assertEquals(3, blocks.size)
        assertEquals("text", blocks[0]["type"]?.jsonPrimitive?.content)
        assertEquals("image", blocks[1]["type"]?.jsonPrimitive?.content)
        assertEquals("custom", blocks[2]["type"]?.jsonPrimitive?.content)
        assertEquals("some-data", blocks[2]["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun `empty builder produces empty list`() {
        val blocks = contentBlocks { }
        assertEquals(0, blocks.size)
    }
}
