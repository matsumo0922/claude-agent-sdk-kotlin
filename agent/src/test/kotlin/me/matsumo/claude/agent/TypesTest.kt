package me.matsumo.claude.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.matsumo.claude.agent.types.AgentDefinition
import me.matsumo.claude.agent.types.AssistantMessageError
import me.matsumo.claude.agent.types.ContentBlock
import me.matsumo.claude.agent.types.Effort
import me.matsumo.claude.agent.types.HookEvent
import me.matsumo.claude.agent.types.McpHttpServerConfig
import me.matsumo.claude.agent.types.McpSSEServerConfig
import me.matsumo.claude.agent.types.McpServerConfig
import me.matsumo.claude.agent.types.McpStdioServerConfig
import me.matsumo.claude.agent.types.Model
import me.matsumo.claude.agent.types.PermissionBehavior
import me.matsumo.claude.agent.types.PermissionMode
import me.matsumo.claude.agent.types.PermissionRuleValue
import me.matsumo.claude.agent.types.PermissionUpdate
import me.matsumo.claude.agent.types.PermissionUpdateDestination
import me.matsumo.claude.agent.types.PermissionUpdateType
import me.matsumo.claude.agent.types.SandboxSettings
import me.matsumo.claude.agent.types.SessionOptionsBuilder
import me.matsumo.claude.agent.types.TextBlock
import me.matsumo.claude.agent.types.ThinkingBlock
import me.matsumo.claude.agent.types.ThinkingConfig
import me.matsumo.claude.agent.types.ToolResultBlock
import me.matsumo.claude.agent.types.ToolUseBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // --- Model enum ---

    @Test
    fun `Model enum has correct model IDs`() {
        assertEquals("sonnet", Model.SONNET.modelId)
        assertEquals("opus", Model.OPUS.modelId)
        assertEquals("haiku", Model.HAIKU.modelId)
    }

    // --- PermissionMode enum ---

    @Test
    fun `PermissionMode enum serializes correctly`() {
        assertEquals("\"default\"", json.encodeToString(PermissionMode.DEFAULT))
        assertEquals("\"bypassPermissions\"", json.encodeToString(PermissionMode.BYPASS_PERMISSIONS))
        assertEquals("\"acceptEdits\"", json.encodeToString(PermissionMode.ACCEPT_EDITS))
        assertEquals("\"plan\"", json.encodeToString(PermissionMode.PLAN))
    }

    @Test
    fun `PermissionMode enum deserializes correctly`() {
        assertEquals(PermissionMode.DEFAULT, json.decodeFromString<PermissionMode>("\"default\""))
        assertEquals(PermissionMode.BYPASS_PERMISSIONS, json.decodeFromString<PermissionMode>("\"bypassPermissions\""))
    }

    // --- ThinkingConfig ---

    @Test
    fun `ThinkingConfig Adaptive serializes`() {
        val config: ThinkingConfig = ThinkingConfig.Adaptive
        val serialized = json.encodeToString(ThinkingConfig.serializer(), config)
        assertTrue(serialized.contains("adaptive"))
    }

    @Test
    fun `ThinkingConfig Enabled serializes with budget`() {
        val config: ThinkingConfig = ThinkingConfig.Enabled(budgetTokens = 50000)
        val serialized = json.encodeToString(ThinkingConfig.serializer(), config)
        assertTrue(serialized.contains("50000"))
        assertTrue(serialized.contains("enabled"))
    }

    @Test
    fun `ThinkingConfig Disabled serializes`() {
        val config: ThinkingConfig = ThinkingConfig.Disabled
        val serialized = json.encodeToString(ThinkingConfig.serializer(), config)
        assertTrue(serialized.contains("disabled"))
    }

    // --- MCP Server Configs ---

    @Test
    fun `McpStdioServerConfig serializes correctly`() {
        val config: McpServerConfig = McpStdioServerConfig(
            command = "npx",
            args = listOf("-y", "@anthropic-ai/server"),
            env = mapOf("API_KEY" to "test"),
        )
        val serialized = json.encodeToString(McpServerConfig.serializer(), config)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("stdio", parsed["type"]!!.jsonPrimitive.content)
        assertEquals("npx", parsed["command"]!!.jsonPrimitive.content)
        assertEquals(2, parsed["args"]!!.jsonArray.size)
    }

    @Test
    fun `McpSSEServerConfig serializes correctly`() {
        val config: McpServerConfig = McpSSEServerConfig(
            url = "https://example.com/sse",
            headers = mapOf("Authorization" to "Bearer token"),
        )
        val serialized = json.encodeToString(McpServerConfig.serializer(), config)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("sse", parsed["type"]!!.jsonPrimitive.content)
        assertEquals("https://example.com/sse", parsed["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `McpHttpServerConfig serializes correctly`() {
        val config: McpServerConfig = McpHttpServerConfig(url = "https://example.com/http")
        val serialized = json.encodeToString(McpServerConfig.serializer(), config)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("http", parsed["type"]!!.jsonPrimitive.content)
    }

    // --- Content Blocks ---

    @Test
    fun `TextBlock serializes with type discriminator`() {
        val block: ContentBlock = TextBlock("Hello")
        val serialized = json.encodeToString(ContentBlock.serializer(), block)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("text", parsed["type"]!!.jsonPrimitive.content)
        assertEquals("Hello", parsed["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ThinkingBlock serializes with type discriminator`() {
        val block: ContentBlock = ThinkingBlock(thinking = "Deep thought", signature = "sig")
        val serialized = json.encodeToString(ContentBlock.serializer(), block)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("thinking", parsed["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ToolUseBlock serializes with type discriminator`() {
        val block: ContentBlock = ToolUseBlock(
            id = "tu_1",
            name = "Read",
            input = buildJsonObject { put("path", "/tmp/test") },
        )
        val serialized = json.encodeToString(ContentBlock.serializer(), block)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("tool_use", parsed["type"]!!.jsonPrimitive.content)
        assertEquals("tu_1", parsed["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ToolResultBlock serializes with type discriminator`() {
        val block: ContentBlock = ToolResultBlock(toolUseId = "tu_1", content = JsonPrimitive("file contents"), isError = false)
        val serialized = json.encodeToString(ContentBlock.serializer(), block)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("tool_result", parsed["type"]!!.jsonPrimitive.content)
        assertEquals("tu_1", parsed["tool_use_id"]!!.jsonPrimitive.content)
    }

    // --- AgentDefinition ---

    @Test
    fun `AgentDefinition serializes all fields`() {
        val agent = AgentDefinition(
            description = "A test agent",
            prompt = "You are a tester",
            tools = listOf("Read", "Write"),
            model = "sonnet",
        )
        val serialized = json.encodeToString(agent)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("A test agent", parsed["description"]!!.jsonPrimitive.content)
        assertEquals("You are a tester", parsed["prompt"]!!.jsonPrimitive.content)
        assertEquals(2, parsed["tools"]!!.jsonArray.size)
        assertEquals("sonnet", parsed["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun `AgentDefinition nullable fields default to null`() {
        val agent = AgentDefinition(description = "desc", prompt = "prompt")
        assertNull(agent.tools)
        assertNull(agent.model)
    }

    // --- SessionOptionsBuilder ---

    @Test
    fun `SessionOptionsBuilder produces correct options`() {
        val options = SessionOptionsBuilder().apply {
            model = Model.SONNET
            maxTurns = 5
            maxBudgetUsd = 1.0
            allowTools("Read", "Glob")
            disallowTools("Write")
            bypassPermissions()
        }.build()

        assertEquals("sonnet", options.model)
        assertEquals(5, options.maxTurns)
        assertEquals(1.0, options.maxBudgetUsd)
        assertEquals(listOf("Read", "Glob"), options.allowedTools)
        assertEquals(listOf("Write"), options.disallowedTools)
        assertEquals(PermissionMode.BYPASS_PERMISSIONS, options.permissionMode)
    }

    @Test
    fun `SessionOptionsBuilder mcpServers DSL`() {
        val options = SessionOptionsBuilder().apply {
            mcpServers {
                put("brave", McpStdioServerConfig(command = "npx", args = listOf("server")))
            }
        }.build()

        assertEquals(1, options.mcpServers.size)
        assertTrue(options.mcpServers.containsKey("brave"))
    }

    @Test
    fun `SessionOptionsBuilder env and extraArgs`() {
        val options = SessionOptionsBuilder().apply {
            env { put("KEY", "value") }
            extraArgs { put("custom-flag", "custom-value") }
        }.build()

        assertEquals("value", options.env["KEY"])
        assertEquals("custom-value", options.extraArgs["custom-flag"])
    }

    @Test
    fun `SessionOptionsBuilder resume sets option`() {
        val options = SessionOptionsBuilder().apply {
            resume = "sess-abc"
        }.build()

        assertEquals("sess-abc", options.resume)
    }

    // --- PermissionUpdate ---

    @Test
    fun `PermissionUpdate serializes`() {
        val update = PermissionUpdate(
            type = PermissionUpdateType.ADD_RULES,
            rules = listOf(PermissionRuleValue(toolName = "Read", ruleContent = "*.py")),
            behavior = PermissionBehavior.ALLOW,
            destination = PermissionUpdateDestination.SESSION,
        )
        val serialized = json.encodeToString(update)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("addRules", parsed["type"]!!.jsonPrimitive.content)
        assertEquals("allow", parsed["behavior"]!!.jsonPrimitive.content)
    }

    // --- SandboxSettings ---

    @Test
    fun `SandboxSettings serializes`() {
        val settings = SandboxSettings(
            enabled = true,
            autoAllowBashIfSandboxed = true,
            excludedCommands = listOf("docker", "git"),
        )
        val serialized = json.encodeToString(settings)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertTrue(parsed["enabled"]!!.jsonPrimitive.boolean)
        assertEquals(2, parsed["excludedCommands"]!!.jsonArray.size)
    }

    // --- HookEvent ---

    @Test
    fun `HookEvent serializes with correct names`() {
        assertEquals("\"PreToolUse\"", json.encodeToString(HookEvent.PRE_TOOL_USE))
        assertEquals("\"PostToolUse\"", json.encodeToString(HookEvent.POST_TOOL_USE))
        assertEquals("\"Stop\"", json.encodeToString(HookEvent.STOP))
    }

    // --- Effort enum ---

    @Test
    fun `Effort enum serializes correctly`() {
        assertEquals("\"low\"", json.encodeToString(Effort.LOW))
        assertEquals("\"max\"", json.encodeToString(Effort.MAX))
    }

    // --- CheckpointId ---

    @Test
    fun `CheckpointId wraps string value`() {
        val id = CheckpointId("uuid-123")
        assertEquals("uuid-123", id.value)
    }

    // --- AssistantMessageError ---

    @Test
    fun `AssistantMessageError deserializes`() {
        assertEquals(
            AssistantMessageError.RATE_LIMIT,
            json.decodeFromString<AssistantMessageError>("\"rate_limit\""),
        )
        assertEquals(
            AssistantMessageError.SERVER_ERROR,
            json.decodeFromString<AssistantMessageError>("\"server_error\""),
        )
    }
}
