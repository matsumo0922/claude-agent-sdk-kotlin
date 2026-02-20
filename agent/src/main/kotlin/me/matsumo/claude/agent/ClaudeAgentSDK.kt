@file:JvmName("ClaudeAgentSDK")

package me.matsumo.claude.agent

import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.matsumo.claude.agent.internal.InternalClient
import me.matsumo.claude.agent.internal.transport.SubprocessTransport
import me.matsumo.claude.agent.types.PromptResult
import me.matsumo.claude.agent.types.ResultMessage
import me.matsumo.claude.agent.types.SessionOptionsBuilder
import me.matsumo.claude.agent.types.SystemMessage

/**
 * Send a one-shot query to Claude and return the response text.
 *
 * This is the simplest entry point into the SDK:
 * ```kotlin
 * val answer = query("What is the capital of France?")
 * ```
 *
 * @param prompt The text prompt to send to Claude.
 * @param configure Optional DSL block for session configuration.
 * @return The final response text, or an empty string if no result is available.
 */
public suspend fun query(
    prompt: String,
    configure: SessionOptionsBuilder.() -> Unit = {},
): String {
    val result = prompt(prompt, configure)
    return result.result ?: ""
}

/**
 * Send a prompt to Claude and return the full [PromptResult] with metadata.
 *
 * ```kotlin
 * val result = prompt("Explain quantum computing") {
 *     model = Model.SONNET
 *     maxTurns = 5
 * }
 * println("Response: ${result.result}")
 * println("Cost: $${result.totalCostUsd}")
 * ```
 *
 * @param prompt The text prompt to send to Claude.
 * @param configure Optional DSL block for session configuration.
 * @return A [PromptResult] containing the response, cost, token counts, and session info.
 */
public suspend fun prompt(
    prompt: String,
    configure: SessionOptionsBuilder.() -> Unit = {},
): PromptResult = coroutineScope {
    val options = SessionOptionsBuilder().apply(configure).build()
    val client = InternalClient(options) { SubprocessTransport(it) }

    try {
        val controller = client.connect(this)
        client.sendPromptAndClose(prompt)

        var resultMessage: ResultMessage? = null
        var sessionId = ""

        client.messages().collect { msg ->
            when (msg) {
                is SystemMessage -> if (msg.isInit) sessionId = msg.sessionId
                is ResultMessage -> resultMessage = msg
                else -> {}
            }
        }

        val rm = resultMessage
        if (rm != null) {
            PromptResult(
                result = rm.result,
                sessionId = rm.sessionId,
                totalCostUsd = rm.totalCostUsd,
                inputTokens = rm.usage?.get("input_tokens")?.jsonPrimitive?.longOrNull ?: 0L,
                outputTokens = rm.usage?.get("output_tokens")?.jsonPrimitive?.longOrNull ?: 0L,
                numTurns = rm.numTurns,
                durationMs = rm.durationMs,
                isError = rm.isError,
                subtype = rm.subtype,
                rawStructuredOutput = rm.structuredOutput,
            )
        } else {
            PromptResult(result = null, sessionId = sessionId)
        }
    } finally {
        client.close()
    }
}

/**
 * Create a new interactive session with Claude Code.
 *
 * Returns a [ClaudeSDKClient] that must be connected before use.
 * Use Kotlin's `use {}` for automatic resource cleanup:
 *
 * ```kotlin
 * createSession {
 *     model = Model.SONNET
 *     allowTools("Read", "Glob", "Grep")
 *     bypassPermissions()
 * }.use { session ->
 *     session.connect()
 *     session.send("Find all TODO comments")
 *     session.receive()
 *         .filterIsInstance<AssistantMessage>()
 *         .collect { print(it.textContent()) }
 * }
 * ```
 *
 * @param configure DSL block for session configuration.
 * @return A [ClaudeSDKClient] ready for [ClaudeSDKClient.connect].
 */
public fun createSession(
    configure: SessionOptionsBuilder.() -> Unit = {},
): ClaudeSDKClient {
    val options = SessionOptionsBuilder().apply(configure).build()
    return ClaudeSDKClient(options) { SubprocessTransport(it) }
}

/**
 * Resume a previous session by its session ID.
 *
 * The resumed session has access to the full conversation history from
 * the previous session:
 *
 * ```kotlin
 * resumeSession(previousSessionId) {
 *     model = Model.SONNET
 * }.use { session ->
 *     session.send("Continue where we left off")
 *     session.receive().collect { ... }
 * }
 * ```
 *
 * @param sessionId The session ID from a previous session (obtained from [SystemMessage]).
 * @param configure DSL block for session configuration.
 * @return A [ClaudeSDKClient] ready for [ClaudeSDKClient.connect].
 */
public fun resumeSession(
    sessionId: String,
    configure: SessionOptionsBuilder.() -> Unit = {},
): ClaudeSDKClient {
    val options = SessionOptionsBuilder().apply {
        configure()
        resume = sessionId
    }.build()
    return ClaudeSDKClient(options) { SubprocessTransport(it) }
}
