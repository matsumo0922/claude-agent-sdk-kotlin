package me.matsumo.claude.agent.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for an MCP (Model Context Protocol) server.
 *
 * MCP servers provide tools that Claude can invoke during a session.
 * The SDK supports multiple transport types: stdio, SSE, and HTTP.
 */
@Serializable
public sealed interface McpServerConfig

/**
 * MCP server configuration using stdio transport.
 *
 * The CLI spawns the server as a subprocess and communicates
 * via stdin/stdout using the MCP JSON-RPC protocol.
 *
 * @param command The command to execute.
 * @param args Command-line arguments.
 * @param env Environment variables for the subprocess.
 */
@Serializable
@SerialName("stdio")
public data class McpStdioServerConfig(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
) : McpServerConfig

/**
 * MCP server configuration using Server-Sent Events (SSE) transport.
 *
 * @param url The SSE endpoint URL.
 * @param headers Additional HTTP headers to send with requests.
 */
@Serializable
@SerialName("sse")
public data class McpSSEServerConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) : McpServerConfig

/**
 * MCP server configuration using HTTP transport.
 *
 * @param url The HTTP endpoint URL.
 * @param headers Additional HTTP headers to send with requests.
 */
@Serializable
@SerialName("http")
public data class McpHttpServerConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) : McpServerConfig

/**
 * MCP server configuration for in-process SDK servers.
 *
 * These servers run inside the same JVM process and handle
 * JSON-RPC requests directly without subprocess communication.
 * Use [me.matsumo.claude.agent.mcp.createSdkMcpServer] to create an
 * [SdkMcpServer] instance, then wrap it in this config.
 *
 * The [server] instance is used internally for routing; the CLI
 * receives a minimal `{"type": "sdk"}` entry in the MCP config.
 */
public data class McpSdkServerConfig(
    val server: me.matsumo.claude.agent.mcp.SdkMcpServer,
) : McpServerConfig
