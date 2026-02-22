package me.matsumo.claude.agent.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.matsumo.claude.agent.errors.ClaudeSDKException
import me.matsumo.claude.agent.internal.transport.Transport
import me.matsumo.claude.agent.mcp.SdkMcpServer
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.BaseHookInput
import me.matsumo.claude.agent.types.ClaudeAgentOptions
import me.matsumo.claude.agent.types.HookCallback
import me.matsumo.claude.agent.types.HookContext
import me.matsumo.claude.agent.types.HookEvent
import me.matsumo.claude.agent.types.HookInput
import me.matsumo.claude.agent.types.HookJSONOutput
import me.matsumo.claude.agent.types.NotificationHookInput
import me.matsumo.claude.agent.types.PermissionMode
import me.matsumo.claude.agent.types.PermissionRequestHookInput
import me.matsumo.claude.agent.types.PermissionResultAllow
import me.matsumo.claude.agent.types.PermissionResultDeny
import me.matsumo.claude.agent.types.PermissionUpdate
import me.matsumo.claude.agent.types.PostToolUseFailureHookInput
import me.matsumo.claude.agent.types.PostToolUseHookInput
import me.matsumo.claude.agent.types.PreCompactHookInput
import me.matsumo.claude.agent.types.PreToolUseHookInput
import me.matsumo.claude.agent.types.SDKMessage
import me.matsumo.claude.agent.types.StopHookInput
import me.matsumo.claude.agent.types.SubagentStartHookInput
import me.matsumo.claude.agent.types.SubagentStopHookInput
import me.matsumo.claude.agent.types.ToolPermissionContext
import me.matsumo.claude.agent.types.ToolUseBlock
import me.matsumo.claude.agent.types.UserPromptSubmitHookInput
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles the bidirectional control protocol on top of [Transport].
 *
 * Responsibilities:
 * - Route incoming messages: SDK messages go to [messages], control responses
 *   are matched to pending requests, control requests are handled in-process.
 * - Execute the initialization handshake with the CLI.
 * - Dispatch hook callbacks and tool permission callbacks.
 * - Provide high-level suspend functions for interrupt, setPermissionMode, rewindFiles, etc.
 */
internal class QueryController(
    private val transport: Transport,
    private val options: ClaudeAgentOptions,
    private val isStreamingMode: Boolean = true,
    private val sdkMcpServers: Map<String, SdkMcpServer> = emptyMap(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // --- Control protocol state ---

    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val hookCallbacks = ConcurrentHashMap<String, HookCallback>()
    private val nextCallbackId = AtomicInteger(0)
    private val requestCounter = AtomicInteger(0)

    // --- ToolUse tracking for parentToolName enrichment ---

    private val toolUseIndex = ConcurrentHashMap<String, String>()

    // --- Message channel ---

    private val messageChannel = Channel<SDKMessage>(capacity = Channel.UNLIMITED)

    // --- Lifecycle ---

    @Volatile
    private var readJob: Job? = null
    @Volatile
    private var scope: CoroutineScope? = null
    @Volatile
    private var closed = false
    @Volatile
    private var initialized = false
    @Volatile
    private var initializationResult: JsonObject? = null

    /**
     * Returns the initialization result from the CLI, if available.
     */
    fun getServerInfo(): JsonObject? = initializationResult

    // Track first result for proper stream closure when hooks/MCP are present
    private val firstResultDeferred = CompletableDeferred<Unit>()

    /**
     * Returns a [Flow] of [SDKMessage]s received from the CLI.
     *
     * Control messages are filtered out and handled internally.
     */
    fun messages(): Flow<SDKMessage> = messageChannel.receiveAsFlow()

    /**
     * Initialize the control protocol.
     *
     * Starts the background message reader and performs the initialization
     * handshake with the CLI if in streaming mode.
     *
     * @param coroutineScope The scope to launch the background reader in.
     * @param initializeTimeout Timeout in milliseconds for the initialize request.
     * @return The initialization response, or null if not in streaming mode.
     */
    suspend fun initialize(
        coroutineScope: CoroutineScope,
        initializeTimeout: Long = 60_000L,
    ): JsonObject? {
        scope = coroutineScope

        // Start background reader
        readJob = coroutineScope.launch {
            readMessages()
        }

        if (!isStreamingMode) return null

        // Build hooks configuration
        val hooksConfig = buildHooksConfig()

        // Build initialize request
        val requestPayload = buildJsonObject {
            put("subtype", "initialize")
            if (hooksConfig.isNotEmpty()) {
                put("hooks", json.parseToJsonElement(json.encodeToString(JsonObject.serializer(), JsonObject(hooksConfig))))
            } else {
                put("hooks", JsonNull)
            }
            options.agents?.let { agents ->
                put("agents", buildJsonObject {
                    agents.forEach { (name, def) ->
                        put(name, buildJsonObject {
                            put("description", def.description)
                            put("prompt", def.prompt)
                            def.tools?.let { tools ->
                                put("tools", JsonArray(tools.map { JsonPrimitive(it) }))
                            }
                            def.model?.let { put("model", it) }
                        })
                    }
                })
            }
        }

        val response = sendControlRequest(requestPayload, initializeTimeout)
        initialized = true
        initializationResult = response
        return response
    }

    /**
     * Send a user prompt to the CLI via the control protocol.
     */
    suspend fun sendPrompt(prompt: String, sessionId: String = "") {
        val message = buildJsonObject {
            put("type", "user")
            put("session_id", sessionId)
            put("message", buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
            put("parent_tool_use_id", JsonNull)
        }
        transport.write(json.encodeToString(JsonObject.serializer(), message) + "\n")
    }

    /**
     * Send an interrupt control request.
     */
    suspend fun interrupt() {
        sendControlRequest(buildJsonObject { put("subtype", "interrupt") })
    }

    /**
     * Change the permission mode.
     */
    suspend fun setPermissionMode(mode: PermissionMode) {
        sendControlRequest(buildJsonObject {
            put("subtype", "set_permission_mode")
            put("mode", json.encodeToJsonElement(mode))
        })
    }

    /**
     * Change the AI model.
     */
    suspend fun setModel(model: String?) {
        sendControlRequest(buildJsonObject {
            put("subtype", "set_model")
            if (model != null) put("model", model) else put("model", JsonNull)
        })
    }

    /**
     * Rewind tracked files to their state at a specific user message.
     *
     * Requires file checkpointing to be enabled.
     */
    suspend fun rewindFiles(userMessageId: String) {
        sendControlRequest(buildJsonObject {
            put("subtype", "rewind_files")
            put("user_message_id", userMessageId)
        })
    }

    /**
     * Get current MCP server connection status.
     */
    suspend fun getMcpStatus(): JsonObject {
        return sendControlRequest(buildJsonObject { put("subtype", "mcp_status") })
    }

    /**
     * Stream input to the transport then close stdin, waiting for the first
     * result if hooks or MCP servers need bidirectional communication.
     */
    suspend fun streamInput(messages: Flow<JsonObject>) {
        try {
            messages.collect { message ->
                if (closed) return@collect
                transport.write(json.encodeToString(JsonObject.serializer(), message) + "\n")
            }

            // If hooks are present, wait for first result before closing stdin
            val hasHooks = !options.hooks.isNullOrEmpty()
            val hasMcpServers = options.mcpServers.isNotEmpty()
            if (hasMcpServers || hasHooks) {
                withTimeoutOrNull(60_000L) {
                    firstResultDeferred.await()
                }
            }

            transport.endInput()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Input streaming errors are non-fatal
        }
    }

    /**
     * Close the controller and release resources.
     */
    suspend fun close() {
        closed = true
        readJob?.cancel()
        messageChannel.close()
        toolUseIndex.clear()
        transport.close()
    }

    // --- Background message reading ---

    private suspend fun readMessages() {
        try {
            transport.readMessages().collect { line ->
                if (closed) return@collect

                val parsed = try {
                    json.parseToJsonElement(line).jsonObject
                } catch (_: Exception) {
                    return@collect
                }

                val msgType = parsed["type"]?.jsonPrimitive?.contentOrNull ?: return@collect

                when (msgType) {
                    // Control response: match to pending request
                    "control_response" -> {
                        handleControlResponse(parsed)
                    }
                    // Incoming control request from CLI
                    "control_request" -> {
                        scope?.launch { handleControlRequest(parsed) }
                    }
                    // Cancel request
                    "control_cancel_request" -> {
                        // TODO: cancellation support
                    }
                    // Regular SDK message
                    else -> {
                        if (msgType == "result") {
                            firstResultDeferred.complete(Unit)
                        }

                        try {
                            val parseResult = MessageParser.parseLine(line)
                            if (parseResult is MessageParser.ParseResult.Message) {
                                messageChannel.send(enrichMessage(parseResult.message))
                            }
                        } catch (_: Exception) {
                            // Skip unparseable messages
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Signal all pending requests so they fail fast
            pendingResponses.forEach { (_, deferred) ->
                deferred.completeExceptionally(e)
            }
        } finally {
            firstResultDeferred.complete(Unit) // Unblock streamInput on abnormal exit
            messageChannel.close()
        }
    }

    /**
     * Enrich an [SDKMessage] with derived fields not present in the wire format.
     *
     * For [AssistantMessage]s, indexes all [ToolUseBlock]s by ID for future lookup,
     * then resolves [AssistantMessage.parentToolName] from the index.
     */
    private fun enrichMessage(message: SDKMessage): SDKMessage {
        if (message is AssistantMessage) {
            for (block in message.content) {
                if (block is ToolUseBlock && block.id.isNotBlank()) {
                    toolUseIndex[block.id] = block.name
                }
            }
            val pid = message.parentToolUseId
            if (pid != null) {
                val toolName = toolUseIndex[pid]
                if (toolName != null) {
                    return message.copy(parentToolName = toolName)
                }
            }
        }
        return message
    }

    // --- Control response handling ---

    private fun handleControlResponse(data: JsonObject) {
        val response = data["response"]?.jsonObject ?: return
        val requestId = response["request_id"]?.jsonPrimitive?.contentOrNull ?: return
        val deferred = pendingResponses.remove(requestId) ?: return
        val subtype = response["subtype"]?.jsonPrimitive?.contentOrNull

        if (subtype == "error") {
            val errorMsg = response["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
            deferred.completeExceptionally(ClaudeSDKException(errorMsg))
        } else {
            deferred.complete(response["response"]?.jsonObject ?: JsonObject(emptyMap()))
        }
    }

    // --- Incoming control request handling ---

    private suspend fun handleControlRequest(data: JsonObject) {
        val requestId = data["request_id"]?.jsonPrimitive?.contentOrNull ?: return
        val request = data["request"]?.jsonObject ?: return
        val subtype = request["subtype"]?.jsonPrimitive?.contentOrNull ?: return

        try {
            val responseData: JsonObject = when (subtype) {
                "can_use_tool" -> handleCanUseTool(request)
                "hook_callback" -> handleHookCallback(request)
                "mcp_message" -> handleMcpMessage(request)
                else -> throw ClaudeSDKException("Unsupported control request subtype: $subtype")
            }

            sendControlResponse(requestId, responseData)
        } catch (e: Exception) {
            sendControlErrorResponse(requestId, e.message ?: "Unknown error")
        }
    }

    private suspend fun handleCanUseTool(request: JsonObject): JsonObject {
        val canUseTool = options.canUseTool ?: throw ClaudeSDKException("canUseTool callback is not provided")
        val toolName = request["tool_name"]?.jsonPrimitive?.content ?: throw ClaudeSDKException("Missing tool_name in can_use_tool request")
        val input = request["input"]?.jsonObject ?: JsonObject(emptyMap())
        val suggestions = request["permission_suggestions"]?.jsonArray
            ?.mapNotNull { element ->
                runCatching { json.decodeFromJsonElement<PermissionUpdate>(element) }.getOrNull()
            }
            ?: emptyList()

        val inputMap: Map<String, Any?> = input.toMap()
        val context = ToolPermissionContext(signal = null, suggestions = suggestions)

        return when (val result = canUseTool(toolName, inputMap, context)) {
            is PermissionResultAllow -> buildJsonObject {
                put("behavior", "allow")
                put("updatedInput", result.updatedInput ?: input)
                result.updatedPermissions?.let { perms ->
                    put("updatedPermissions", json.encodeToJsonElement(perms))
                }
            }
            is PermissionResultDeny -> buildJsonObject {
                put("behavior", "deny")
                put("message", result.message)
                if (result.interrupt) put("interrupt", true)
            }
        }
    }

    private suspend fun handleHookCallback(request: JsonObject): JsonObject {
        val callbackId = request["callback_id"]?.jsonPrimitive?.contentOrNull
            ?: throw ClaudeSDKException("Missing callback_id in hook_callback request")

        val callback = hookCallbacks[callbackId]
            ?: throw ClaudeSDKException("No hook callback found for ID: $callbackId")

        val input = request["input"]
        val toolUseId = request["tool_use_id"]?.jsonPrimitive?.contentOrNull
        val context = HookContext(signal = null)

        // Create a minimal BaseHookInput from the raw JSON
        val hookInput = createHookInput(input)

        val output = callback(hookInput, toolUseId, context)

        return json.encodeToJsonElement(HookJSONOutput.serializer(), output).jsonObject
    }

    private fun createHookInput(input: JsonElement?): HookInput {
        val obj = input?.jsonObject ?: JsonObject(emptyMap())
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull ?: ""
        val transcriptPath = obj["transcript_path"]?.jsonPrimitive?.contentOrNull ?: ""
        val cwd = obj["cwd"]?.jsonPrimitive?.contentOrNull ?: ""
        val permissionMode = obj["permission_mode"]?.jsonPrimitive?.contentOrNull
        val hookEventName = obj["hook_event_name"]?.jsonPrimitive?.contentOrNull

        return when (hookEventName) {
            "PreToolUse" -> PreToolUseHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                toolName = obj["tool_name"]?.jsonPrimitive?.content ?: "",
                toolInput = obj["tool_input"]?.jsonObject ?: JsonObject(emptyMap()),
                toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content ?: "",
            )
            "PostToolUse" -> PostToolUseHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                toolName = obj["tool_name"]?.jsonPrimitive?.content ?: "",
                toolInput = obj["tool_input"]?.jsonObject ?: JsonObject(emptyMap()),
                toolResponse = obj["tool_response"],
                toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content ?: "",
            )
            "PostToolUseFailure" -> PostToolUseFailureHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                toolName = obj["tool_name"]?.jsonPrimitive?.content ?: "",
                toolInput = obj["tool_input"]?.jsonObject ?: JsonObject(emptyMap()),
                toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content ?: "",
                error = obj["error"]?.jsonPrimitive?.content ?: "",
                isInterrupt = obj["is_interrupt"]?.jsonPrimitive?.booleanOrNull,
            )
            "UserPromptSubmit" -> UserPromptSubmitHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                prompt = obj["prompt"]?.jsonPrimitive?.content ?: "",
            )
            "Stop" -> StopHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                stopHookActive = obj["stop_hook_active"]?.jsonPrimitive?.boolean ?: false,
            )
            "SubagentStop" -> SubagentStopHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                stopHookActive = obj["stop_hook_active"]?.jsonPrimitive?.boolean ?: false,
                agentId = obj["agent_id"]?.jsonPrimitive?.content ?: "",
                agentTranscriptPath = obj["agent_transcript_path"]?.jsonPrimitive?.content ?: "",
                agentType = obj["agent_type"]?.jsonPrimitive?.content ?: "",
            )
            "PreCompact" -> PreCompactHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                trigger = obj["trigger"]?.jsonPrimitive?.content ?: "auto",
                customInstructions = obj["custom_instructions"]?.jsonPrimitive?.contentOrNull,
            )
            "Notification" -> NotificationHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                message = obj["message"]?.jsonPrimitive?.content ?: "",
                title = obj["title"]?.jsonPrimitive?.contentOrNull,
                notificationType = obj["notification_type"]?.jsonPrimitive?.content ?: "",
            )
            "SubagentStart" -> SubagentStartHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                agentId = obj["agent_id"]?.jsonPrimitive?.content ?: "",
                agentType = obj["agent_type"]?.jsonPrimitive?.content ?: "",
            )
            "PermissionRequest" -> PermissionRequestHookInput(
                sessionId = sessionId,
                transcriptPath = transcriptPath,
                cwd = cwd,
                permissionMode = permissionMode,
                toolName = obj["tool_name"]?.jsonPrimitive?.content ?: "",
                toolInput = obj["tool_input"]?.jsonObject ?: JsonObject(emptyMap()),
                permissionSuggestions = obj["permission_suggestions"]?.jsonArray?.toList(),
            )
            else -> object : BaseHookInput {
                override val sessionId = sessionId
                override val transcriptPath = transcriptPath
                override val cwd = cwd
                override val permissionMode = permissionMode
            }
        }
    }

    private suspend fun handleMcpMessage(request: JsonObject): JsonObject {
        val serverName = request["server_name"]?.jsonPrimitive?.contentOrNull
            ?: throw ClaudeSDKException("Missing server_name for MCP request")
        val message = request["message"]?.jsonObject
            ?: throw ClaudeSDKException("Missing message for MCP request")

        val server = sdkMcpServers[serverName]
        if (server == null) {
            return buildJsonObject {
                put("mcp_response", buildJsonObject {
                    put("jsonrpc", "2.0")
                    message["id"]?.let { put("id", it) }
                    put("error", buildJsonObject {
                        put("code", -32601)
                        put("message", "Server '$serverName' not found")
                    })
                })
            }
        }

        val response = try {
            server.handleRequest(message) ?: buildJsonObject {
                put("jsonrpc", "2.0")
                put("result", JsonObject(emptyMap()))
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("jsonrpc", "2.0")
                message["id"]?.let { put("id", it) }
                put("error", buildJsonObject {
                    put("code", -32603)
                    put("message", e.message ?: "Internal error")
                })
            }
        }

        return buildJsonObject {
            put("mcp_response", response)
        }
    }

    // --- Control protocol I/O ---

    private suspend fun sendControlRequest(
        request: JsonObject,
        timeoutMs: Long = 60_000L,
    ): JsonObject {
        if (!isStreamingMode) {
            throw ClaudeSDKException("Control requests require streaming mode")
        }

        val requestId = "req_${requestCounter.incrementAndGet()}_${System.nanoTime().toString(16)}"
        val deferred = CompletableDeferred<JsonObject>()
        pendingResponses[requestId] = deferred

        val controlRequest = buildJsonObject {
            put("type", "control_request")
            put("request_id", requestId)
            put("request", request)
        }

        transport.write(json.encodeToString(JsonObject.serializer(), controlRequest) + "\n")

        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingResponses.remove(requestId)
            throw ClaudeSDKException("Control request timeout: ${request["subtype"]?.jsonPrimitive?.contentOrNull}")
        } catch (e: CancellationException) {
            pendingResponses.remove(requestId)
            throw e
        }
    }

    private suspend fun sendControlResponse(requestId: String, responseData: JsonObject) {
        val response = buildJsonObject {
            put("type", "control_response")
            put("response", buildJsonObject {
                put("subtype", "success")
                put("request_id", requestId)
                put("response", responseData)
            })
        }
        transport.write(json.encodeToString(JsonObject.serializer(), response) + "\n")
    }

    private suspend fun sendControlErrorResponse(requestId: String, error: String) {
        val response = buildJsonObject {
            put("type", "control_response")
            put("response", buildJsonObject {
                put("subtype", "error")
                put("request_id", requestId)
                put("error", error)
            })
        }
        transport.write(json.encodeToString(JsonObject.serializer(), response) + "\n")
    }

    // --- Hooks configuration ---

    private fun buildHooksConfig(): Map<String, JsonElement> {
        val hooks = options.hooks ?: return emptyMap()
        val config = mutableMapOf<String, JsonElement>()

        hooks.forEach { (event, matchers) ->
            if (matchers.isNotEmpty()) {
                val matcherArray = matchers.map { matcher ->
                    val callbackIds = matcher.hooks.map { callback ->
                        val callbackId = "hook_${nextCallbackId.getAndIncrement()}"
                        hookCallbacks[callbackId] = callback
                        JsonPrimitive(callbackId)
                    }
                    buildJsonObject {
                        matcher.matcher?.let { put("matcher", it) } ?: put("matcher", JsonNull)
                        put("hookCallbackIds", JsonArray(callbackIds))
                        matcher.timeout?.let { put("timeout", it) }
                    }
                }
                config[json.encodeToString(HookEvent.serializer(), event).trim('"')] = JsonArray(matcherArray)
            }
        }

        return config
    }

    // --- Helpers ---

    private fun JsonObject.toMap(): Map<String, Any?> {
        return this.mapValues { (_, value) -> value.toAny() }
    }

    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> {
            booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull ?: contentOrNull
        }
        is JsonObject -> toMap()
        is JsonArray -> map { it.toAny() }
    }
}
