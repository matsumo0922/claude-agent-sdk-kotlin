package me.matsumo.claude.agent.types

import kotlinx.serialization.json.JsonElement

/**
 * The result of a `prompt()` call, containing the response text and metadata.
 *
 * @param result The final response text from Claude.
 * @param sessionId The session identifier.
 * @param totalCostUsd The estimated total cost of the session in USD.
 * @param inputTokens Total input tokens consumed.
 * @param outputTokens Total output tokens generated.
 * @param numTurns Number of agentic turns executed.
 * @param durationMs Total wall-clock time in milliseconds.
 * @param isError Whether the session ended with an error.
 * @param subtype The result subtype (e.g. `"success"`, `"error"`, `"max_turns"`).
 * @param rawStructuredOutput The raw structured output JSON, if an output schema was used.
 */
public data class PromptResult(
    val result: String?,
    val sessionId: String,
    val totalCostUsd: Double? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val numTurns: Int = 0,
    val durationMs: Int = 0,
    val isError: Boolean = false,
    val subtype: String = "success",
    val rawStructuredOutput: JsonElement? = null,
) {
    /**
     * Deserializes the structured output into the specified type [T].
     *
     * Requires that an output schema was configured via `outputSchema<T>()`
     * and that the response contains valid structured output JSON.
     *
     * @throws IllegalStateException if no structured output is available.
     */
    public inline fun <reified T> structuredOutput(
        json: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json,
    ): T {
        val output = rawStructuredOutput
            ?: error("No structured output available. Did you configure outputSchema<T>()?")
        return json.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), output)
    }
}
