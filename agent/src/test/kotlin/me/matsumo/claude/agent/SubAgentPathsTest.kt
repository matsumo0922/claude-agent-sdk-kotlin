package me.matsumo.claude.agent

import me.matsumo.claude.agent.types.SubAgentPaths
import kotlin.test.Test
import kotlin.test.assertEquals

class SubAgentPathsTest {

    @Test
    fun `builds path from transcript path without jsonl suffix`() {
        val result = SubAgentPaths.subAgentTranscriptPath(
            parentTranscriptPath = "/home/user/.claude/sessions/abc123",
            agentId = "agent-42",
        )
        assertEquals("/home/user/.claude/sessions/abc123/subagents/agent-agent-42.jsonl", result)
    }

    @Test
    fun `builds path from transcript path with jsonl suffix`() {
        val result = SubAgentPaths.subAgentTranscriptPath(
            parentTranscriptPath = "/home/user/.claude/sessions/abc123.jsonl",
            agentId = "agent-42",
        )
        assertEquals("/home/user/.claude/sessions/abc123/subagents/agent-agent-42.jsonl", result)
    }

    @Test
    fun `handles simple agentId`() {
        val result = SubAgentPaths.subAgentTranscriptPath(
            parentTranscriptPath = "/tmp/transcript.jsonl",
            agentId = "xyz",
        )
        assertEquals("/tmp/transcript/subagents/agent-xyz.jsonl", result)
    }
}
