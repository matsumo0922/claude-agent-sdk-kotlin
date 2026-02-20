package me.matsumo.claude.agent.internal.transport

import kotlinx.coroutines.flow.Flow

/**
 * Abstract transport interface for communicating with the Claude Code CLI.
 *
 * Implementations manage the lifecycle of the underlying connection
 * (e.g. a subprocess) and expose read/write primitives that the
 * higher-level session controller builds on.
 */
public interface Transport {

    /**
     * Establish the connection (e.g. spawn the CLI process).
     *
     * Must be called before [write] or [readMessages].
     */
    public suspend fun connect()

    /**
     * Write a raw string to the transport's input channel (stdin).
     *
     * The caller is responsible for appending a newline if the protocol requires it.
     *
     * @throws me.matsumo.claude.agent.errors.CLIConnectionException if the transport
     *         is not connected or the write fails.
     */
    public suspend fun write(data: String)

    /**
     * Returns a cold [Flow] that emits raw JSON-line strings read from the
     * transport's output channel (stdout).
     *
     * The flow completes when the process exits or the stream is closed.
     * If the process exits with a non-zero code, the flow throws a
     * [me.matsumo.claude.agent.errors.ProcessException].
     */
    public fun readMessages(): Flow<String>

    /**
     * Close the transport and release all associated resources.
     *
     * After calling this method, the transport is no longer usable.
     */
    public suspend fun close()

    /**
     * Returns `true` if the transport is connected and ready for I/O.
     */
    public fun isReady(): Boolean

    /**
     * Signal end-of-input by closing the stdin channel.
     *
     * The process may continue running and producing output after this call.
     */
    public suspend fun endInput()
}
