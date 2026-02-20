package com.anthropic.sdk.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

/**
 * Definition of a single MCP tool.
 *
 * Created internally by [McpServerBuilder.tool] and stored in [SdkMcpServer].
 */
@PublishedApi
internal class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val handler: suspend (JsonObject) -> JsonElement,
)

/**
 * An in-process MCP server that hosts tools callable by Claude.
 *
 * Rather than running as a separate subprocess, an [SdkMcpServer] lives
 * inside the same JVM process and handles JSON-RPC requests directly.
 * The [QueryController] routes incoming MCP JSON-RPC messages to [handleRequest].
 *
 * Create instances via [createSdkMcpServer]:
 *
 * ```kotlin
 * val server = createSdkMcpServer("weather") {
 *     tool<WeatherArgs, WeatherResult>("get_weather", "Get current weather") { args ->
 *         fetchWeather(args.city)
 *     }
 * }
 * ```
 */
public class SdkMcpServer @PublishedApi internal constructor(
    public val name: String,
    public val version: String,
    internal val tools: Map<String, ToolDefinition>,
) {

    /**
     * Handle an incoming JSON-RPC request and return the response.
     *
     * Supported methods:
     * - `initialize` – returns server capabilities
     * - `notifications/initialized` – no-op acknowledgment
     * - `tools/list` – returns tool definitions
     * - `tools/call` – executes a tool handler
     *
     * @param request A parsed JSON-RPC request object.
     * @return A JSON-RPC response object, or `null` for notifications.
     */
    public suspend fun handleRequest(request: JsonObject): JsonObject? {
        val method = request["method"]?.jsonPrimitive?.contentOrNull
        val id = request["id"]
        val params = request["params"]?.jsonObject

        return when (method) {
            "initialize" -> jsonRpcResult(id, buildInitializeResult())
            "notifications/initialized" -> null // notification, no response
            "tools/list" -> jsonRpcResult(id, buildToolsList())
            "tools/call" -> handleToolCall(id, params)
            else -> jsonRpcError(id, METHOD_NOT_FOUND, "Method not found: $method")
        }
    }

    // ------------------------------------------------------------------
    // JSON-RPC handlers
    // ------------------------------------------------------------------

    private fun buildInitializeResult(): JsonObject = buildJsonObject {
        put("protocolVersion", "2024-11-05")
        putJsonObject("capabilities") {
            putJsonObject("tools") {
                put("listChanged", false)
            }
        }
        putJsonObject("serverInfo") {
            put("name", name)
            put("version", version)
        }
    }

    private fun buildToolsList(): JsonObject = buildJsonObject {
        putJsonArray("tools") {
            for (tool in tools.values) {
                addJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", tool.inputSchema)
                }
            }
        }
    }

    private suspend fun handleToolCall(
        id: JsonElement?,
        params: JsonObject?,
    ): JsonObject {
        val toolName = params?.get("name")?.jsonPrimitive?.contentOrNull
            ?: return jsonRpcError(id, INVALID_PARAMS, "Missing 'name' in params")

        val tool = tools[toolName]
            ?: return jsonRpcError(id, INVALID_PARAMS, "Tool '$toolName' not found")

        val arguments = params["arguments"]?.jsonObject ?: buildJsonObject { }

        return try {
            val result = tool.handler(arguments)

            val content = when (result) {
                is JsonObject -> {
                    // If the result already has a "content" key, use it as-is
                    if ("content" in result) {
                        result["content"]!!
                    } else {
                        // Wrap the result as a text content block
                        JsonArray(listOf(buildJsonObject {
                            put("type", "text")
                            put("text", result.toString())
                        }))
                    }
                }
                is JsonArray -> result
                else -> {
                    JsonArray(listOf(buildJsonObject {
                        put("type", "text")
                        put("text", result.toString())
                    }))
                }
            }

            jsonRpcResult(id, buildJsonObject {
                put("content", content)
            })
        } catch (e: Exception) {
            jsonRpcResult(id, buildJsonObject {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", "Error: ${e.message}")
                    }
                }
                put("isError", true)
            })
        }
    }

    // ------------------------------------------------------------------
    // JSON-RPC message builders
    // ------------------------------------------------------------------

    private fun jsonRpcResult(id: JsonElement?, result: JsonObject): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id) else put("id", JsonNull)
            put("result", result)
        }

    private fun jsonRpcError(id: JsonElement?, code: Int, message: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id) else put("id", JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }

    private companion object {
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
    }
}

// ------------------------------------------------------------------
// DSL builder
// ------------------------------------------------------------------

/**
 * Builder for configuring tools on an [SdkMcpServer].
 */
public class McpServerBuilder @PublishedApi internal constructor() {

    @PublishedApi
    internal val json: Json = Json { ignoreUnknownKeys = true }

    @PublishedApi
    internal val tools: MutableMap<String, ToolDefinition> = mutableMapOf()

    /**
     * Register a tool whose input and output types are both `@Serializable`.
     *
     * The JSON Schema for [A] is generated automatically via [JsonSchemaGenerator].
     * At invocation time, the raw JSON arguments are deserialized into [A],
     * and the handler's return value [R] is serialized back to JSON.
     *
     * ```kotlin
     * tool<WeatherArgs, WeatherResult>("get_weather", "Get current weather") { args ->
     *     fetchWeather(args.city, args.unit)
     * }
     * ```
     *
     * @param A The tool argument type (must be `@Serializable`).
     * @param R The tool result type (must be `@Serializable`).
     * @param name Unique tool name, visible to Claude.
     * @param description Human-readable description of what the tool does.
     * @param handler Suspending function that receives the deserialized arguments
     *                and returns the result object.
     */
    public inline fun <reified A, reified R> tool(
        name: String,
        description: String,
        noinline handler: suspend (A) -> R,
    ) {
        val inputSchema = JsonSchemaGenerator.jsonSchema<A>()
        val argSerializer: KSerializer<A> = serializer()
        val resultSerializer: KSerializer<R> = serializer()

        tools[name] = ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema,
            handler = { rawArgs ->
                val args: A = json.decodeFromJsonElement(argSerializer, rawArgs)
                val result: R = handler(args)
                json.encodeToJsonElement(resultSerializer, result)
            },
        )
    }

    /**
     * Register a tool with a raw [JsonObject] handler.
     *
     * Use this overload when you want full control over JSON
     * serialization/deserialization.
     *
     * @param name Unique tool name.
     * @param description Human-readable description.
     * @param inputSchema Pre-built JSON Schema for the tool input.
     * @param handler Suspending function receiving raw JSON arguments
     *                and returning a raw JSON result.
     */
    public fun toolRaw(
        name: String,
        description: String,
        inputSchema: JsonObject,
        handler: suspend (JsonObject) -> JsonElement,
    ) {
        tools[name] = ToolDefinition(
            name = name,
            description = description,
            inputSchema = inputSchema,
            handler = handler,
        )
    }
}

// ------------------------------------------------------------------
// Public entry-points
// ------------------------------------------------------------------

/**
 * Create an in-process [SdkMcpServer] with the given name and tool definitions.
 *
 * ```kotlin
 * val server = createSdkMcpServer("weather") {
 *     tool<WeatherArgs, WeatherResult>("get_weather", "Get current weather") { args ->
 *         fetchWeather(args.city)
 *     }
 * }
 * ```
 *
 * @param name Server name, used in MCP tool naming (`mcp__<name>__<tool>`).
 * @param version Server version string (informational). Defaults to `"1.0.0"`.
 * @param block DSL block for registering tools.
 * @return A configured [SdkMcpServer] instance.
 */
public inline fun createSdkMcpServer(
    name: String,
    version: String = "1.0.0",
    block: McpServerBuilder.() -> Unit,
): SdkMcpServer {
    val builder = McpServerBuilder()
    builder.block()
    return SdkMcpServer(
        name = name,
        version = version,
        tools = builder.tools.toMap(),
    )
}

/**
 * Run an [SdkMcpServer] as a standalone MCP server, reading JSON-RPC
 * requests from stdin and writing responses to stdout.
 *
 * This is intended for use in a `fun main()` entry point when the
 * server is spawned as a separate process by the Claude Code CLI.
 *
 * ```kotlin
 * fun main() = runMcpServer(server)
 * ```
 *
 * @param server The MCP server to run.
 */
public fun runMcpServer(server: SdkMcpServer): Unit = runBlocking {
    val json = Json { ignoreUnknownKeys = true }

    withContext(Dispatchers.IO) {
        val reader = System.`in`.bufferedReader(Charsets.UTF_8)
        val writer = System.out.bufferedWriter(Charsets.UTF_8)

        while (true) {
            val line = reader.readLine() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val request = try {
                json.parseToJsonElement(trimmed).jsonObject
            } catch (_: Exception) {
                continue // skip malformed input
            }

            val response = server.handleRequest(request)

            if (response != null) {
                writer.write(response.toString())
                writer.newLine()
                writer.flush()
            }
        }
    }
}
