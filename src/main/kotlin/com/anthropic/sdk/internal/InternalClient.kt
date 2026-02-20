package com.anthropic.sdk.internal

import com.anthropic.sdk.internal.transport.Transport
import com.anthropic.sdk.mcp.SdkMcpServer
import com.anthropic.sdk.types.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*

/**
 * Internal client that coordinates [Transport] and [QueryController].
 *
 * Creates the transport, sets up the query controller, runs the initialization
 * handshake, and exposes a [Flow] of [SDKMessage]s. This class is not part of
 * the public API; [ClaudeSDKClient] and the top-level functions delegate to it.
 */
internal class InternalClient(
    private val options: ClaudeAgentOptions,
    private val transportFactory: (ClaudeAgentOptions) -> Transport,
) {
    @Volatile
    private var transport: Transport? = null
    @Volatile
    private var queryController: QueryController? = null

    /**
     * Connect and initialize the control protocol.
     *
     * @param scope The coroutine scope for launching the background message reader.
     * @return The [QueryController] for sending prompts and control requests.
     */
    suspend fun connect(scope: CoroutineScope): QueryController {
        val configuredOptions = configureOptions(options)

        val t = transportFactory(configuredOptions)
        t.connect()
        transport = t

        // Extract SDK MCP servers from options
        val sdkMcpServers = extractSdkMcpServers(configuredOptions)

        val qc = QueryController(
            transport = t,
            options = configuredOptions,
            isStreamingMode = true,
            sdkMcpServers = sdkMcpServers,
        )
        qc.initialize(scope)
        queryController = qc

        return qc
    }

    /**
     * Send a string prompt to the CLI after initialization.
     *
     * For one-shot queries where the prompt is known upfront, this sends
     * the user message and closes stdin so the CLI can produce a response.
     */
    suspend fun sendPromptAndClose(prompt: String) {
        val t = transport ?: error("Not connected")
        val message = buildJsonObject {
            put("type", "user")
            put("session_id", "")
            put("message", buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
            put("parent_tool_use_id", JsonNull)
        }
        val json = Json { ignoreUnknownKeys = true }
        t.write(json.encodeToString(JsonObject.serializer(), message) + "\n")
        t.endInput()
    }

    /**
     * Get the message flow from the query controller.
     */
    fun messages(): Flow<SDKMessage> {
        return queryController?.messages() ?: error("Not connected")
    }

    /**
     * Get the underlying query controller.
     */
    fun controller(): QueryController {
        return queryController ?: error("Not connected")
    }

    /**
     * Close all resources.
     */
    suspend fun close() {
        queryController?.close()
        queryController = null
        transport = null
    }

    /**
     * Apply automatic configuration transformations to options.
     */
    private fun configureOptions(options: ClaudeAgentOptions): ClaudeAgentOptions {
        if (options.canUseTool == null) return options

        // canUseTool requires permission_prompt_tool_name = "stdio"
        require(options.permissionPromptToolName == null) {
            "canUseTool callback cannot be used with permissionPromptToolName. Use one or the other."
        }

        return options.copy(permissionPromptToolName = "stdio")
    }

    /**
     * Extract in-process SDK MCP server instances from options.
     */
    private fun extractSdkMcpServers(options: ClaudeAgentOptions): Map<String, SdkMcpServer> {
        val result = mutableMapOf<String, SdkMcpServer>()
        for ((name, config) in options.mcpServers) {
            if (config is McpSdkServerConfig) {
                result[name] = config.server
            }
        }
        return result
    }
}
