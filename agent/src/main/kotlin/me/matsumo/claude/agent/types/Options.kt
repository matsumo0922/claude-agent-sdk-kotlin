package me.matsumo.claude.agent.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Claude model identifiers.
 *
 * Use [modelId] to get the string sent to the CLI, or use the raw [String]
 * overload in [ClaudeAgentOptions] for models not listed here.
 */
@Serializable
public enum class Model(
    public val modelId: String,
    public val displayName: String,
    public val description: String,
) {
    @SerialName("sonnet")
    SONNET("sonnet", "Sonnet", "Balanced speed and intelligence"),

    @SerialName("opus")
    OPUS("opus", "Opus", "Most intelligent, best for complex tasks"),

    @SerialName("haiku")
    HAIKU("haiku", "Haiku", "Fastest, best for simple tasks"),
}

/**
 * Permission mode governing how tool use is authorized.
 */
@Serializable
public enum class PermissionMode(
    public val modeId: String,
    public val displayName: String,
    public val description: String,
) {
    @SerialName("default")
    DEFAULT("default", "Auto", "Asks for permission on each action"),

    @SerialName("acceptEdits")
    ACCEPT_EDITS("acceptEdits", "Accept Edits", "Automatically accepts file edits"),

    @SerialName("plan")
    PLAN("plan", "Plan Mode", "Requires plan approval before execution"),

    @SerialName("bypassPermissions")
    BYPASS_PERMISSIONS("bypassPermissions", "Bypass Permissions", "Skips all permission prompts"),
}

/**
 * Beta feature flags.
 */
@Serializable
public enum class SdkBeta {
    @SerialName("context-1m-2025-08-07")
    CONTEXT_1M,
}

/**
 * Sources from which settings can be loaded.
 */
@Serializable
public enum class SettingSource {
    @SerialName("user")
    USER,

    @SerialName("project")
    PROJECT,

    @SerialName("local")
    LOCAL,
}

/**
 * Effort level for extended thinking depth.
 */
@Serializable
public enum class Effort {
    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,

    @SerialName("max")
    MAX,
}

// --- Thinking Config ---

/**
 * Configuration for Claude's extended thinking feature.
 */
@Serializable
public sealed interface ThinkingConfig {
    @Serializable
    @SerialName("adaptive")
    public data object Adaptive : ThinkingConfig

    @Serializable
    @SerialName("enabled")
    public data class Enabled(
        @SerialName("budget_tokens")
        val budgetTokens: Int,
    ) : ThinkingConfig

    @Serializable
    @SerialName("disabled")
    public data object Disabled : ThinkingConfig
}

// --- System Prompt ---

/**
 * A system prompt preset referencing a built-in prompt with optional appended text.
 */
@Serializable
public data class SystemPromptPreset(
    val type: String = "preset",
    val preset: String = "claude_code",
    val append: String? = null,
)

// --- Tools Preset ---

/**
 * A tools preset referencing the built-in Claude Code toolset.
 */
@Serializable
public data class ToolsPreset(
    val type: String = "preset",
    val preset: String = "claude_code",
)

// --- Sandbox ---

/**
 * Network configuration for the sandbox.
 */
@Serializable
public data class SandboxNetworkConfig(
    val allowUnixSockets: List<String>? = null,
    val allowAllUnixSockets: Boolean? = null,
    val allowLocalBinding: Boolean? = null,
    val httpProxyPort: Int? = null,
    val socksProxyPort: Int? = null,
)

/**
 * Violations to ignore in the sandbox.
 */
@Serializable
public data class SandboxIgnoreViolations(
    val file: List<String>? = null,
    val network: List<String>? = null,
)

/**
 * Sandbox settings controlling bash command isolation.
 *
 * Filesystem and network restrictions are configured via permission rules,
 * not via these sandbox settings.
 */
@Serializable
public data class SandboxSettings(
    val enabled: Boolean? = null,
    val autoAllowBashIfSandboxed: Boolean? = null,
    val excludedCommands: List<String>? = null,
    val allowUnsandboxedCommands: Boolean? = null,
    val network: SandboxNetworkConfig? = null,
    val ignoreViolations: SandboxIgnoreViolations? = null,
    val enableWeakerNestedSandbox: Boolean? = null,
)

// --- SDK Plugin ---

/**
 * SDK plugin configuration. Currently only local plugins are supported.
 */
@Serializable
public data class SdkPluginConfig(
    val type: String = "local",
    val path: String,
)

// --- Permission types ---

/**
 * Destination for permission rule updates.
 */
@Serializable
public enum class PermissionUpdateDestination {
    @SerialName("userSettings")
    USER_SETTINGS,

    @SerialName("projectSettings")
    PROJECT_SETTINGS,

    @SerialName("localSettings")
    LOCAL_SETTINGS,

    @SerialName("session")
    SESSION,
}

/**
 * Permission behavior (allow, deny, or ask).
 */
@Serializable
public enum class PermissionBehavior {
    @SerialName("allow")
    ALLOW,

    @SerialName("deny")
    DENY,

    @SerialName("ask")
    ASK,
}

/**
 * A single permission rule value.
 */
@Serializable
public data class PermissionRuleValue(
    @SerialName("toolName")
    val toolName: String,
    @SerialName("ruleContent")
    val ruleContent: String? = null,
)

/**
 * Type of permission update operation.
 */
@Serializable
public enum class PermissionUpdateType {
    @SerialName("addRules")
    ADD_RULES,

    @SerialName("replaceRules")
    REPLACE_RULES,

    @SerialName("removeRules")
    REMOVE_RULES,

    @SerialName("setMode")
    SET_MODE,

    @SerialName("addDirectories")
    ADD_DIRECTORIES,

    @SerialName("removeDirectories")
    REMOVE_DIRECTORIES,
}

/**
 * A permission update to send to the CLI.
 */
@Serializable
public data class PermissionUpdate(
    val type: PermissionUpdateType,
    val rules: List<PermissionRuleValue>? = null,
    val behavior: PermissionBehavior? = null,
    val mode: PermissionMode? = null,
    val directories: List<String>? = null,
    val destination: PermissionUpdateDestination? = null,
)

// --- Permission Results ---

/**
 * Result of a tool permission check.
 */
@Serializable
public sealed interface PermissionResult

/**
 * Allow the tool to execute, optionally with modified input or updated permissions.
 */
@Serializable
@SerialName("allow")
public data class PermissionResultAllow(
    val behavior: String = "allow",
    @SerialName("updatedInput")
    val updatedInput: JsonObject? = null,
    @SerialName("updatedPermissions")
    val updatedPermissions: List<PermissionUpdate>? = null,
) : PermissionResult

/**
 * Deny the tool from executing.
 */
@Serializable
@SerialName("deny")
public data class PermissionResultDeny(
    val behavior: String = "deny",
    val message: String = "",
    val interrupt: Boolean = false,
) : PermissionResult

/**
 * Context provided to tool permission callbacks.
 */
public data class ToolPermissionContext(
    val signal: Any? = null,
    val suggestions: List<PermissionUpdate> = emptyList(),
)

// --- Output Format ---

/**
 * Output format specification for structured outputs.
 *
 * Example: `OutputFormat(type = "json_schema", schema = jsonSchemaObject)`
 */
@Serializable
public data class OutputFormat(
    val type: String,
    val schema: JsonObject? = null,
)

// --- Main Options ---

/**
 * Configuration options for Claude Agent SDK sessions.
 *
 * This is the primary configuration type passed to `query()`, `prompt()`,
 * and `createSession()`. Use the DSL builder for a more readable syntax:
 *
 * ```kotlin
 * createSession {
 *     model = Model.SONNET
 *     maxTurns = 5
 *     allowTools("Read", "Glob")
 *     bypassPermissions()
 * }
 * ```
 */
public data class ClaudeAgentOptions(
    /** The model to use. Either a [Model] enum or a raw model string. */
    val model: String? = null,
    /** Fallback model if the primary model is unavailable. */
    val fallbackModel: String? = null,
    /** Maximum number of agentic turns. */
    val maxTurns: Int? = null,
    /** Maximum budget in USD. */
    val maxBudgetUsd: Double? = null,
    /** Tool set configuration: list of tool names or a [ToolsPreset]. */
    val tools: Any? = null,
    /** Explicitly allowed tools by name. */
    val allowedTools: List<String> = emptyList(),
    /** Explicitly disallowed tools by name. */
    val disallowedTools: List<String> = emptyList(),
    /** Permission mode for tool authorization. */
    val permissionMode: PermissionMode? = null,
    /** System prompt: either a raw string or a [SystemPromptPreset]. */
    val systemPrompt: Any? = null,
    /** MCP server configurations keyed by server name. */
    val mcpServers: Map<String, McpServerConfig> = emptyMap(),
    /** Whether to continue a previous conversation. */
    val continueConversation: Boolean = false,
    /** Session ID to resume. */
    val resume: String? = null,
    /** Whether resumed sessions should fork to a new session ID. */
    val forkSession: Boolean = false,
    /** Beta feature flags. */
    val betas: List<SdkBeta> = emptyList(),
    /** Custom permission prompt tool name. */
    val permissionPromptToolName: String? = null,
    /** Working directory for the CLI process. */
    val cwd: String? = null,
    /** Path to the Claude Code CLI binary. */
    val cliPath: String? = null,
    /** Path to a settings file to load. */
    val settings: String? = null,
    /** Additional directories to add to the session. */
    val addDirs: List<String> = emptyList(),
    /** Environment variables for the CLI process. */
    val env: Map<String, String> = emptyMap(),
    /** Extra CLI arguments as key-value pairs. */
    val extraArgs: Map<String, String?> = emptyMap(),
    /** Maximum buffer size in bytes for CLI stdout. */
    val maxBufferSize: Int? = null,
    /** Callback for stderr output from the CLI. */
    val stderr: ((String) -> Unit)? = null,
    /** Callback for tool permission checks. */
    val canUseTool: (suspend (String, Map<String, Any?>, ToolPermissionContext) -> PermissionResult)? = null,
    /** Hook configurations keyed by [HookEvent]. */
    val hooks: Map<HookEvent, List<HookMatcher>>? = null,
    /** User identifier for attribution. */
    val user: String? = null,
    /** Whether to include partial (streaming) messages. */
    val includePartialMessages: Boolean = false,
    /** Agent definitions for custom agents. */
    val agents: Map<String, AgentDefinition>? = null,
    /** Setting sources to load. */
    val settingSources: List<SettingSource>? = null,
    /** Sandbox configuration for bash command isolation. */
    val sandbox: SandboxSettings? = null,
    /** Plugin configurations. */
    val plugins: List<SdkPluginConfig> = emptyList(),
    /** @deprecated Use [thinking] instead. Max tokens for thinking blocks. */
    @Deprecated("Use thinking instead", replaceWith = ReplaceWith("thinking"))
    val maxThinkingTokens: Int? = null,
    /** Controls extended thinking behavior. Takes precedence over maxThinkingTokens. */
    val thinking: ThinkingConfig? = null,
    /** Effort level for thinking depth. */
    val effort: Effort? = null,
    /** Output format for structured outputs. */
    val outputFormat: OutputFormat? = null,
    /** Enable file checkpointing to track file changes during the session. */
    val enableFileCheckpointing: Boolean = false,
)

/**
 * DSL builder for constructing [ClaudeAgentOptions].
 */
public class SessionOptionsBuilder {
    public var model: Model? = null
    public var modelId: String? = null
    public var fallbackModel: String? = null
    public var maxTurns: Int? = null
    public var maxBudgetUsd: Double? = null
    public var permissionMode: PermissionMode? = null
    public var systemPrompt: String? = null
    public var systemPromptPreset: SystemPromptPreset? = null
    public var resume: String? = null
    public var forkSession: Boolean = false
    public var continueConversation: Boolean = false
    public var cwd: String? = null
    public var cliPath: String? = null
    public var settings: String? = null
    public var user: String? = null
    public var includePartialMessages: Boolean = false
    public var maxBufferSize: Int? = null
    public var thinking: ThinkingConfig? = null
    public var effort: Effort? = null
    public var enableFileCheckpointing: Boolean = false
    public var stderr: ((String) -> Unit)? = null
    public var sandbox: SandboxSettings? = null

    private val allowedTools = mutableListOf<String>()
    private val disallowedTools = mutableListOf<String>()
    private val mcpServers = mutableMapOf<String, McpServerConfig>()
    private val envVars = mutableMapOf<String, String>()
    private val extraArgs = mutableMapOf<String, String?>()
    private val addDirs = mutableListOf<String>()
    private val betas = mutableListOf<SdkBeta>()
    private val plugins = mutableListOf<SdkPluginConfig>()
    private var agents: MutableMap<String, AgentDefinition>? = null
    private var settingSources: MutableList<SettingSource>? = null
    private var hooks: MutableMap<HookEvent, MutableList<HookMatcher>>? = null
    private var outputFormat: OutputFormat? = null
    private var canUseTool: (suspend (String, Map<String, Any?>, ToolPermissionContext) -> PermissionResult)? = null

    /** Allow the specified tools by name. */
    public fun allowTools(vararg tools: String) {
        allowedTools.addAll(tools)
    }

    /** Disallow the specified tools by name. */
    public fun disallowTools(vararg tools: String) {
        disallowedTools.addAll(tools)
    }

    /** Set the permission mode to bypass all permissions. */
    public fun bypassPermissions() {
        permissionMode = PermissionMode.BYPASS_PERMISSIONS
    }

    /** Configure MCP servers. */
    public fun mcpServers(block: MutableMap<String, McpServerConfig>.() -> Unit) {
        mcpServers.apply(block)
    }

    /** Configure environment variables for the CLI process. */
    public fun env(block: MutableMap<String, String>.() -> Unit) {
        envVars.apply(block)
    }

    /** Add extra CLI arguments. */
    public fun extraArgs(block: MutableMap<String, String?>.() -> Unit) {
        extraArgs.apply(block)
    }

    /** Add additional directories. */
    public fun addDirs(vararg dirs: String) {
        addDirs.addAll(dirs)
    }

    /** Enable beta features. */
    public fun betas(vararg beta: SdkBeta) {
        betas.addAll(beta)
    }

    /** Add a plugin configuration. */
    public fun plugin(config: SdkPluginConfig) {
        plugins.add(config)
    }

    /** Configure hooks using a DSL builder. */
    public fun hooks(block: HooksBuilder.() -> Unit) {
        val builder = HooksBuilder()
        builder.block()
        hooks = builder.build()
    }

    /** Set a structured output format. */
    public fun outputFormat(format: OutputFormat) {
        outputFormat = format
    }

    /** Set the tool permission callback. */
    public fun canUseTool(callback: suspend (String, Map<String, Any?>, ToolPermissionContext) -> PermissionResult) {
        canUseTool = callback
    }

    /** Define custom agents. */
    public fun agents(block: MutableMap<String, AgentDefinition>.() -> Unit) {
        val map = mutableMapOf<String, AgentDefinition>()
        map.block()
        agents = map
    }

    /** Specify setting sources. */
    public fun settingSources(vararg sources: SettingSource) {
        settingSources = sources.toMutableList()
    }

    /** Build the [ClaudeAgentOptions]. */
    public fun build(): ClaudeAgentOptions = ClaudeAgentOptions(
        model = modelId ?: model?.modelId,
        fallbackModel = fallbackModel,
        maxTurns = maxTurns,
        maxBudgetUsd = maxBudgetUsd,
        permissionMode = permissionMode,
        systemPrompt = systemPromptPreset ?: systemPrompt,
        allowedTools = allowedTools.toList(),
        disallowedTools = disallowedTools.toList(),
        mcpServers = mcpServers.toMap(),
        continueConversation = continueConversation,
        resume = resume,
        forkSession = forkSession,
        betas = betas.toList(),
        cwd = cwd,
        cliPath = cliPath,
        settings = settings,
        addDirs = addDirs.toList(),
        env = envVars.toMap(),
        extraArgs = extraArgs.toMap(),
        maxBufferSize = maxBufferSize,
        stderr = stderr,
        canUseTool = canUseTool,
        hooks = hooks?.mapValues { it.value.toList() },
        user = user,
        includePartialMessages = includePartialMessages,
        agents = agents?.toMap(),
        settingSources = settingSources?.toList(),
        sandbox = sandbox,
        plugins = plugins.toList(),
        thinking = thinking,
        effort = effort,
        outputFormat = outputFormat,
        enableFileCheckpointing = enableFileCheckpointing,
    )
}
