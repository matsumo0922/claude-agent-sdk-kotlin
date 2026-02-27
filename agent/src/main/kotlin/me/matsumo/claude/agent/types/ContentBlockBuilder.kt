package me.matsumo.claude.agent.types

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Type-safe DSL for building content blocks to send via [me.matsumo.claude.agent.ClaudeSDKClient.send].
 *
 * Usage:
 * ```kotlin
 * val blocks = contentBlocks {
 *     text("Hello, Claude!")
 *     image(mediaType = "image/png", data = base64Data)
 *     document(mediaType = "application/pdf", data = base64Data, fileName = "report.pdf")
 * }
 * client.send(blocks)
 * ```
 */
public class ContentBlocksBuilder {
    private val blocks = mutableListOf<JsonObject>()

    /** Add a text content block. */
    public fun text(text: String) {
        blocks.add(buildJsonObject {
            put("type", "text")
            put("text", text)
        })
    }

    /** Add a base64-encoded image content block. */
    public fun image(mediaType: String, data: String) {
        blocks.add(buildJsonObject {
            put("type", "image")
            put("source", buildJsonObject {
                put("type", "base64")
                put("media_type", mediaType)
                put("data", data)
            })
        })
    }

    /** Add a base64-encoded document content block. */
    public fun document(mediaType: String, data: String, fileName: String? = null) {
        blocks.add(buildJsonObject {
            put("type", "document")
            put("source", buildJsonObject {
                put("type", "base64")
                put("media_type", mediaType)
                put("data", data)
            })
            if (fileName != null) put("title", fileName)
        })
    }

    /** Add a pre-built raw [JsonObject] block. */
    public fun raw(block: JsonObject) {
        blocks.add(block)
    }

    public fun build(): List<JsonObject> = blocks.toList()
}

/** DSL entry point for building content blocks. */
public fun contentBlocks(block: ContentBlocksBuilder.() -> Unit): List<JsonObject> =
    ContentBlocksBuilder().apply(block).build()
