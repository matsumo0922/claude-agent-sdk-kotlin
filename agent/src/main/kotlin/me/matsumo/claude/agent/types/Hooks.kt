package me.matsumo.claude.agent.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Hook events that can be intercepted during a session.
 */
@Serializable
public enum class HookEvent {
    @SerialName("PreToolUse")
    PRE_TOOL_USE,

    @SerialName("PostToolUse")
    POST_TOOL_USE,

    @SerialName("PostToolUseFailure")
    POST_TOOL_USE_FAILURE,

    @SerialName("UserPromptSubmit")
    USER_PROMPT_SUBMIT,

    @SerialName("Stop")
    STOP,

    @SerialName("SubagentStop")
    SUBAGENT_STOP,

    @SerialName("PreCompact")
    PRE_COMPACT,

    @SerialName("Notification")
    NOTIFICATION,

    @SerialName("SubagentStart")
    SUBAGENT_START,

    @SerialName("PermissionRequest")
    PERMISSION_REQUEST,
}

// --- Hook Input Types ---

/**
 * Base interface for all hook input types.
 */
public interface BaseHookInput {
    public val sessionId: String
    public val transcriptPath: String
    public val cwd: String
    public val permissionMode: String?
}

/**
 * Input data for [HookEvent.PRE_TOOL_USE] hook events.
 */
public data class PreToolUseHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val toolName: String,
    val toolInput: JsonObject,
    val toolUseId: String,
) : BaseHookInput

/**
 * Input data for [HookEvent.POST_TOOL_USE] hook events.
 */
public data class PostToolUseHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val toolName: String,
    val toolInput: JsonObject,
    val toolResponse: JsonElement? = null,
    val toolUseId: String,
) : BaseHookInput

/**
 * Input data for [HookEvent.POST_TOOL_USE_FAILURE] hook events.
 */
public data class PostToolUseFailureHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val toolName: String,
    val toolInput: JsonObject,
    val toolUseId: String,
    val error: String,
    val isInterrupt: Boolean? = null,
) : BaseHookInput

/**
 * Input data for [HookEvent.USER_PROMPT_SUBMIT] hook events.
 */
public data class UserPromptSubmitHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val prompt: String,
) : BaseHookInput

/**
 * Input data for [HookEvent.STOP] hook events.
 */
public data class StopHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val stopHookActive: Boolean,
) : BaseHookInput

/**
 * Input data for [HookEvent.SUBAGENT_STOP] hook events.
 */
public data class SubagentStopHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val stopHookActive: Boolean,
    val agentId: String,
    val agentTranscriptPath: String,
    val agentType: String,
) : BaseHookInput

/**
 * Input data for [HookEvent.PRE_COMPACT] hook events.
 */
public data class PreCompactHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val trigger: String,
    val customInstructions: String?,
) : BaseHookInput

/**
 * Input data for [HookEvent.NOTIFICATION] hook events.
 */
public data class NotificationHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val message: String,
    val title: String? = null,
    val notificationType: String,
) : BaseHookInput

/**
 * Input data for [HookEvent.SUBAGENT_START] hook events.
 */
public data class SubagentStartHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val agentId: String,
    val agentType: String,
) : BaseHookInput

/**
 * Input data for [HookEvent.PERMISSION_REQUEST] hook events.
 */
public data class PermissionRequestHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String? = null,
    val toolName: String,
    val toolInput: JsonObject,
    val permissionSuggestions: List<JsonElement>? = null,
) : BaseHookInput

/**
 * Union type alias for all hook inputs.
 */
public typealias HookInput = BaseHookInput

// --- Hook Specific Output Types ---

/**
 * Hook-specific output for [HookEvent.PRE_TOOL_USE] events.
 */
@Serializable
public data class PreToolUseHookSpecificOutput(
    val hookEventName: String = "PreToolUse",
    val permissionDecision: String? = null,
    val permissionDecisionReason: String? = null,
    val updatedInput: JsonObject? = null,
    val additionalContext: String? = null,
)

/**
 * Hook-specific output for [HookEvent.POST_TOOL_USE] events.
 */
@Serializable
public data class PostToolUseHookSpecificOutput(
    val hookEventName: String = "PostToolUse",
    val additionalContext: String? = null,
    val updatedMCPToolOutput: JsonElement? = null,
)

/**
 * Hook-specific output for [HookEvent.PERMISSION_REQUEST] events.
 */
@Serializable
public data class PermissionRequestHookSpecificOutput(
    val hookEventName: String = "PermissionRequest",
    val decision: JsonObject,
)

// --- Hook JSON Output ---

/**
 * Synchronous hook output with control and decision fields.
 *
 * Returned by hook callbacks to control execution flow and provide
 * feedback to Claude.
 */
@Serializable
public data class HookJSONOutput(
    /** Whether Claude should proceed after hook execution. Default: true. */
    @SerialName("continue")
    val shouldContinue: Boolean? = null,
    /** Hide stdout from transcript mode. */
    val suppressOutput: Boolean? = null,
    /** Message shown when shouldContinue is false. */
    val stopReason: String? = null,
    /** Set to "block" to indicate blocking behavior. */
    val decision: String? = null,
    /** Warning message displayed to the user. */
    val systemMessage: String? = null,
    /** Feedback message for Claude about the decision. */
    val reason: String? = null,
    /** Event-specific controls. */
    val hookSpecificOutput: JsonObject? = null,
)

/**
 * Async hook output that defers hook execution.
 */
@Serializable
public data class AsyncHookJSONOutput(
    /** Set to true to defer hook execution. */
    @SerialName("async")
    val async: Boolean = true,
    /** Timeout in milliseconds for the async operation. */
    val asyncTimeout: Int? = null,
)

// --- Hook Context & Callback ---

/**
 * Context information provided to hook callbacks.
 */
public data class HookContext(
    val signal: Any? = null,
)

/**
 * A hook callback is a suspending function that receives hook input
 * and returns a [HookJSONOutput] controlling execution.
 */
public typealias HookCallback = suspend (input: HookInput, toolUseId: String?, context: HookContext) -> HookJSONOutput

// --- Hook Matcher ---

/**
 * Associates a regex pattern with a list of hook callbacks.
 *
 * The [matcher] is a regex pattern tested against tool names. If `null`,
 * the hooks run for every tool invocation.
 *
 * @param matcher A regex pattern matching tool names, or `null` for a catch-all.
 * @param hooks The callbacks to invoke when the pattern matches.
 * @param timeout Timeout in seconds for all hooks in this matcher.
 */
public data class HookMatcher(
    val matcher: String? = null,
    val hooks: List<HookCallback> = emptyList(),
    val timeout: Double? = null,
)

// --- Hook Output (high-level DSL) ---

/**
 * High-level hook output types used in the DSL builder.
 *
 * These map to [HookJSONOutput] values internally.
 */
public sealed class HookOutput {
    public companion object {
        /** Allow the tool to proceed. */
        public fun allow(): HookJSONOutput = HookJSONOutput()

        /** Deny the tool with a reason message. */
        public fun deny(reason: String): HookJSONOutput = HookJSONOutput(
            shouldContinue = true,
            decision = "block",
            reason = reason,
            hookSpecificOutput = null,
        )

        /** Proceed after a post-tool hook (no-op). */
        public fun proceed(): HookJSONOutput = HookJSONOutput()

        /** Modify the tool input before execution. */
        public fun modify(newInput: JsonObject): HookJSONOutput {
            val specificOutput = kotlinx.serialization.json.buildJsonObject {
                put("hookEventName", kotlinx.serialization.json.JsonPrimitive("PreToolUse"))
                put("updatedInput", newInput)
            }
            return HookJSONOutput(
                hookSpecificOutput = specificOutput,
            )
        }
    }
}

// --- Hooks DSL Builder ---

/**
 * DSL builder for configuring hooks on a session.
 */
public class HooksBuilder {
    private val hooks = mutableMapOf<HookEvent, MutableList<HookMatcher>>()

    /**
     * Register a [HookEvent.PRE_TOOL_USE] hook with an optional tool name matcher pattern.
     */
    public fun preToolUse(
        matcher: String? = null,
        timeout: Double? = null,
        block: suspend (input: HookInput, toolUseId: String?, context: HookContext) -> HookJSONOutput,
    ) {
        hooks.getOrPut(HookEvent.PRE_TOOL_USE) { mutableListOf() }
            .add(HookMatcher(matcher, listOf(block), timeout))
    }

    /**
     * Register a [HookEvent.POST_TOOL_USE] hook with an optional tool name matcher pattern.
     */
    public fun postToolUse(
        matcher: String? = null,
        timeout: Double? = null,
        block: suspend (input: HookInput, toolUseId: String?, context: HookContext) -> HookJSONOutput,
    ) {
        hooks.getOrPut(HookEvent.POST_TOOL_USE) { mutableListOf() }
            .add(HookMatcher(matcher, listOf(block), timeout))
    }

    /**
     * Register a [HookEvent.POST_TOOL_USE_FAILURE] hook with an optional tool name matcher pattern.
     */
    public fun postToolUseFailure(
        matcher: String? = null,
        timeout: Double? = null,
        block: suspend (input: HookInput, toolUseId: String?, context: HookContext) -> HookJSONOutput,
    ) {
        hooks.getOrPut(HookEvent.POST_TOOL_USE_FAILURE) { mutableListOf() }
            .add(HookMatcher(matcher, listOf(block), timeout))
    }

    /**
     * Register a [HookEvent.USER_PROMPT_SUBMIT] hook.
     */
    public fun userPromptSubmit(
        timeout: Double? = null,
        block: suspend (input: HookInput, toolUseId: String?, context: HookContext) -> HookJSONOutput,
    ) {
        hooks.getOrPut(HookEvent.USER_PROMPT_SUBMIT) { mutableListOf() }
            .add(HookMatcher(null, listOf(block), timeout))
    }

    /**
     * Register a [HookEvent.STOP] hook.
     */
    public fun stop(
        timeout: Double? = null,
        block: suspend (input: HookInput, toolUseId: String?, context: HookContext) -> HookJSONOutput,
    ) {
        hooks.getOrPut(HookEvent.STOP) { mutableListOf() }
            .add(HookMatcher(null, listOf(block), timeout))
    }

    /**
     * Register a [HookEvent.NOTIFICATION] hook.
     */
    public fun notification(
        timeout: Double? = null,
        block: suspend (input: HookInput, toolUseId: String?, context: HookContext) -> HookJSONOutput,
    ) {
        hooks.getOrPut(HookEvent.NOTIFICATION) { mutableListOf() }
            .add(HookMatcher(null, listOf(block), timeout))
    }

    /**
     * Register a hook for an arbitrary [HookEvent].
     */
    public fun on(
        event: HookEvent,
        matcher: String? = null,
        timeout: Double? = null,
        block: suspend (input: HookInput, toolUseId: String?, context: HookContext) -> HookJSONOutput,
    ) {
        hooks.getOrPut(event) { mutableListOf() }
            .add(HookMatcher(matcher, listOf(block), timeout))
    }

    internal fun build(): MutableMap<HookEvent, MutableList<HookMatcher>> = hooks
}
