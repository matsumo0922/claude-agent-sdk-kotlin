package com.anthropic.sdk

import com.anthropic.sdk.internal.transport.SubprocessTransport
import com.anthropic.sdk.types.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for SubprocessTransport command building.
 *
 * These tests use reflection to access the private buildCommand() method
 * since we want to verify the CLI command construction without actually
 * spawning processes.
 */
class TransportTest {

    private fun buildCommand(options: ClaudeAgentOptions): List<String> {
        // Use reflection to access private buildCommand
        val transport = SubprocessTransport(options.copy(cliPath = "/usr/bin/claude"))
        val method = SubprocessTransport::class.java.getDeclaredMethod("buildCommand")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(transport) as List<String>
    }

    @Test
    fun `basic command includes required flags`() {
        val cmd = buildCommand(ClaudeAgentOptions())
        assertTrue(cmd.contains("--output-format"))
        assertTrue(cmd.contains("stream-json"))
        assertTrue(cmd.contains("--verbose"))
        assertTrue(cmd.contains("--input-format"))
    }

    @Test
    fun `model flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(model = "sonnet"))
        assertTrue(cmd.contains("--model"))
        assertTrue(cmd.contains("sonnet"))
    }

    @Test
    fun `maxTurns flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(maxTurns = 5))
        assertTrue(cmd.contains("--max-turns"))
        assertTrue(cmd.contains("5"))
    }

    @Test
    fun `maxBudgetUsd flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(maxBudgetUsd = 1.5))
        assertTrue(cmd.contains("--max-budget-usd"))
        assertTrue(cmd.contains("1.5"))
    }

    @Test
    fun `allowedTools flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(allowedTools = listOf("Read", "Glob")))
        assertTrue(cmd.contains("--allowedTools"))
        assertTrue(cmd.contains("Read,Glob"))
    }

    @Test
    fun `disallowedTools flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(disallowedTools = listOf("Write")))
        assertTrue(cmd.contains("--disallowedTools"))
        assertTrue(cmd.contains("Write"))
    }

    @Test
    fun `permission mode flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(permissionMode = PermissionMode.BYPASS_PERMISSIONS))
        assertTrue(cmd.contains("--permission-mode"))
        assertTrue(cmd.contains("bypassPermissions"))
    }

    @Test
    fun `system prompt flag is added for string`() {
        val cmd = buildCommand(ClaudeAgentOptions(systemPrompt = "You are a helpful assistant"))
        assertTrue(cmd.contains("--system-prompt"))
        assertTrue(cmd.contains("You are a helpful assistant"))
    }

    @Test
    fun `continue flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(continueConversation = true))
        assertTrue(cmd.contains("--continue"))
    }

    @Test
    fun `resume flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(resume = "sess-123"))
        assertTrue(cmd.contains("--resume"))
        assertTrue(cmd.contains("sess-123"))
    }

    @Test
    fun `fallback model flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(fallbackModel = "haiku"))
        assertTrue(cmd.contains("--fallback-model"))
        assertTrue(cmd.contains("haiku"))
    }

    @Test
    fun `include partial messages flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(includePartialMessages = true))
        assertTrue(cmd.contains("--include-partial-messages"))
    }

    @Test
    fun `fork session flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(forkSession = true))
        assertTrue(cmd.contains("--fork-session"))
    }

    @Test
    fun `extra args are added`() {
        val cmd = buildCommand(ClaudeAgentOptions(extraArgs = mapOf("debug-to-stderr" to null, "custom-flag" to "value")))
        assertTrue(cmd.contains("--debug-to-stderr"))
        assertTrue(cmd.contains("--custom-flag"))
        assertTrue(cmd.contains("value"))
    }

    @Test
    fun `add-dir flags are added`() {
        val cmd = buildCommand(ClaudeAgentOptions(addDirs = listOf("/path/a", "/path/b")))
        val addDirCount = cmd.count { it == "--add-dir" }
        assertTrue(addDirCount == 2)
    }

    @Test
    fun `MCP servers config produces mcp-config flag`() {
        val cmd = buildCommand(ClaudeAgentOptions(
            mcpServers = mapOf("brave" to McpStdioServerConfig(command = "npx", args = listOf("server-brave")))
        ))
        assertTrue(cmd.contains("--mcp-config"))
    }

    @Test
    fun `thinking config enabled sets max-thinking-tokens`() {
        val cmd = buildCommand(ClaudeAgentOptions(thinking = ThinkingConfig.Enabled(budgetTokens = 50000)))
        assertTrue(cmd.contains("--max-thinking-tokens"))
        assertTrue(cmd.contains("50000"))
    }

    @Test
    fun `thinking config disabled sets max-thinking-tokens to 0`() {
        val cmd = buildCommand(ClaudeAgentOptions(thinking = ThinkingConfig.Disabled))
        assertTrue(cmd.contains("--max-thinking-tokens"))
        assertTrue(cmd.contains("0"))
    }

    @Test
    fun `effort flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(effort = Effort.HIGH))
        assertTrue(cmd.contains("--effort"))
        assertTrue(cmd.contains("high"))
    }

    @Test
    fun `betas flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(betas = listOf(SdkBeta.CONTEXT_1M)))
        assertTrue(cmd.contains("--betas"))
        assertTrue(cmd.contains("context-1m-2025-08-07"))
    }

    @Test
    fun `permissionPromptToolName flag is added`() {
        val cmd = buildCommand(ClaudeAgentOptions(permissionPromptToolName = "stdio"))
        assertTrue(cmd.contains("--permission-prompt-tool"))
        assertTrue(cmd.contains("stdio"))
    }

    @Test
    fun `setting-sources flag is always present`() {
        val cmd = buildCommand(ClaudeAgentOptions())
        assertTrue(cmd.contains("--setting-sources"))
    }

    @Test
    fun `plugins add plugin-dir flags`() {
        val cmd = buildCommand(ClaudeAgentOptions(
            plugins = listOf(SdkPluginConfig(path = "/my/plugin"))
        ))
        assertTrue(cmd.contains("--plugin-dir"))
        assertTrue(cmd.contains("/my/plugin"))
    }
}
