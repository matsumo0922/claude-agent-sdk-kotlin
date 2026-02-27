package me.matsumo.claude.agent.types

/**
 * Sub-agent transcript path construction utilities.
 * Centralizes the path convention so consumers don't depend on internal CLI layout.
 */
public object SubAgentPaths {
    /**
     * Build the sub-agent transcript JSONL path from the parent transcript path and agent ID.
     *
     * @param parentTranscriptPath The parent session's transcript path (may or may not end with ".jsonl").
     * @param agentId The sub-agent identifier from SubagentStartHookInput.
     * @return Absolute path to the sub-agent's transcript JSONL file.
     */
    public fun subAgentTranscriptPath(parentTranscriptPath: String, agentId: String): String =
        "${parentTranscriptPath.removeSuffix(".jsonl")}/subagents/agent-$agentId.jsonl"
}
