package com.anthropic.sdk

import com.anthropic.sdk.errors.CLIConnectionException
import com.anthropic.sdk.internal.InternalClient
import com.anthropic.sdk.internal.QueryController
import com.anthropic.sdk.internal.transport.Transport
import com.anthropic.sdk.types.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.serialization.json.JsonObject
import java.io.Closeable

/**
 * A value type wrapping a user-message UUID for file checkpointing.
 */
@JvmInline
public value class CheckpointId(public val value: String)

/**
 * Bidirectional, interactive client for Claude Code sessions.
 *
 * Use [createSession] or [resumeSession] to obtain an instance.
 * The client implements [Closeable] so it works with Kotlin's `use {}`:
 *
 * ```kotlin
 * createSession {
 *     model = Model.SONNET
 *     allowTools("Read", "Glob")
 *     bypassPermissions()
 * }.use { session ->
 *     session.send("Hello")
 *     session.receive().collect { msg ->
 *         when (msg) {
 *             is AssistantMessage -> print(msg.textContent())
 *             is ResultMessage -> println("\nDone: ${msg.result}")
 *             else -> {}
 *         }
 *     }
 * }
 * ```
 */
public class ClaudeSDKClient internal constructor(
    private val options: ClaudeAgentOptions,
    private val transportFactory: (ClaudeAgentOptions) -> Transport,
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob())
    private val internalClient = InternalClient(options, transportFactory)
    private var controller: QueryController? = null

    @Volatile
    private var connected = false

    /** The session ID, available after the first [SystemMessage] with subtype "init". */
    @Volatile
    public var sessionId: String? = null
        private set

    /**
     * Connect to the Claude Code CLI and initialize the control protocol.
     *
     * This is called automatically by [createSession] and [resumeSession].
     */
    public suspend fun connect() {
        controller = internalClient.connect(scope)
        connected = true
    }

    /**
     * Send a user prompt to Claude.
     *
     * @param prompt The text prompt to send.
     * @throws CLIConnectionException if the client is not connected.
     */
    public suspend fun send(prompt: String) {
        ensureConnected()
        controller!!.sendPrompt(prompt)
    }

    /**
     * Returns a [Flow] of [SDKMessage]s from the current turn.
     *
     * The flow emits messages as they arrive from the CLI. Collect it to
     * process responses:
     *
     * ```kotlin
     * session.receive()
     *     .filterIsInstance<AssistantMessage>()
     *     .map { it.textContent() }
     *     .collect { print(it) }
     * ```
     *
     * @throws CLIConnectionException if the client is not connected.
     */
    public fun receive(): Flow<SDKMessage> {
        ensureConnected()
        return internalClient.messages()
    }

    /**
     * Returns a [Flow] that emits messages until (and including) the first [ResultMessage].
     *
     * This is a convenience over [receive] for single-turn workflows.
     */
    public fun receiveResponse(): Flow<SDKMessage> {
        var resultSeen = false
        return receive().takeWhile { msg ->
            if (msg is SystemMessage && msg.isInit) {
                sessionId = msg.sessionId
            }
            if (resultSeen) return@takeWhile false
            if (msg is ResultMessage) {
                resultSeen = true
            }
            true
        }
    }

    /**
     * Send an interrupt signal to the CLI.
     */
    public suspend fun interrupt() {
        ensureConnected()
        controller!!.interrupt()
    }

    /**
     * Change the permission mode during the session.
     */
    public suspend fun setPermissionMode(mode: PermissionMode) {
        ensureConnected()
        controller!!.setPermissionMode(mode)
    }

    /**
     * Change the model during the session.
     *
     * @param model The model name, or `null` to reset to the default.
     */
    public suspend fun setModel(model: String?) {
        ensureConnected()
        controller!!.setModel(model)
    }

    /**
     * Returns the server info from the CLI initialization handshake.
     */
    public fun getServerInfo(): JsonObject? {
        ensureConnected()
        return controller!!.getServerInfo()
    }

    /**
     * Rewind tracked files to the state at the specified checkpoint.
     *
     * Requires [ClaudeAgentOptions.enableFileCheckpointing] to be `true`.
     */
    public suspend fun rewindFiles(checkpointId: CheckpointId) {
        ensureConnected()
        controller!!.rewindFiles(checkpointId.value)
    }

    /**
     * Get current MCP server connection status.
     */
    public suspend fun getMcpStatus(): JsonObject {
        ensureConnected()
        return controller!!.getMcpStatus()
    }

    /**
     * Close the session and release all resources.
     */
    override fun close() {
        connected = false
        scope.cancel()
    }

    /**
     * Suspending close that properly shuts down the transport.
     */
    public suspend fun disconnect() {
        connected = false
        internalClient.close()
        scope.cancel()
    }

    private fun ensureConnected() {
        if (!connected) {
            throw CLIConnectionException("Not connected. Call connect() first.")
        }
    }
}
