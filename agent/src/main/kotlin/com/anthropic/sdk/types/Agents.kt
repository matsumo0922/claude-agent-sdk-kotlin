package com.anthropic.sdk.types

import kotlinx.serialization.Serializable

/**
 * Definition of a custom agent that can be used within a session.
 *
 * @param description A human-readable description of what this agent does.
 * @param prompt The system prompt for this agent.
 * @param tools List of tool names this agent is allowed to use, or `null` for default tools.
 * @param model The model to use for this agent: `"sonnet"`, `"opus"`, `"haiku"`, or `"inherit"`.
 */
@Serializable
public data class AgentDefinition(
    val description: String,
    val prompt: String,
    val tools: List<String>? = null,
    val model: String? = null,
)
