/**
 * 全10種類の HookEvent を hooks{} DSL で登録するデモ。
 *
 * 各イベントに対応する HookInput タイプも合わせて示す。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.types.HookEvent
import com.anthropic.sdk.types.HookOutput

suspend fun runHookEventsDemo() {
    println("=== 全 HookEvent 登録デモ ===")
    println()

    println("--- HookEvent 一覧と対応する HookInput ---")
    println("  PRE_TOOL_USE         -> PreToolUseHookInput (toolName, toolInput, toolUseId)")
    println("  POST_TOOL_USE        -> PostToolUseHookInput (toolName, toolInput, toolResponse, toolUseId)")
    println("  POST_TOOL_USE_FAILURE-> PostToolUseFailureHookInput (toolName, toolInput, toolUseId, error)")
    println("  USER_PROMPT_SUBMIT   -> UserPromptSubmitHookInput (prompt)")
    println("  STOP                 -> StopHookInput (stopHookActive)")
    println("  SUBAGENT_STOP        -> SubagentStopHookInput (stopHookActive, agentId, agentType)")
    println("  PRE_COMPACT          -> PreCompactHookInput (trigger, customInstructions)")
    println("  NOTIFICATION         -> NotificationHookInput (message, title, notificationType)")
    println("  SUBAGENT_START       -> SubagentStartHookInput (agentId, agentType)")
    println("  PERMISSION_REQUEST   -> PermissionRequestHookInput (toolName, toolInput, permissionSuggestions)")
    println()

    println("--- 全イベントを hooks{} DSL で登録 ---")
    println("""
    |  createSession {
    |      hooks {
    |          // 専用メソッドがあるイベント
    |          preToolUse(matcher = ".*") { input, toolUseId, _ ->
    |              val i = input as PreToolUseHookInput
    |              println("PreToolUse: ${'$'}{i.toolName}")
    |              HookOutput.allow()
    |          }
    |
    |          postToolUse { input, _, _ ->
    |              val i = input as PostToolUseHookInput
    |              println("PostToolUse: ${'$'}{i.toolName}, response=${'$'}{i.toolResponse}")
    |              HookOutput.proceed()
    |          }
    |
    |          postToolUseFailure { input, _, _ ->
    |              val i = input as PostToolUseFailureHookInput
    |              println("PostToolUseFailure: ${'$'}{i.toolName}, error=${'$'}{i.error}")
    |              HookOutput.proceed()
    |          }
    |
    |          userPromptSubmit { input, _, _ ->
    |              val i = input as UserPromptSubmitHookInput
    |              println("UserPromptSubmit: ${'$'}{i.prompt}")
    |              HookOutput.allow()
    |          }
    |
    |          stop { input, _, _ ->
    |              val i = input as StopHookInput
    |              println("Stop: active=${'$'}{i.stopHookActive}")
    |              HookOutput.allow()
    |          }
    |
    |          notification { input, _, _ ->
    |              val i = input as NotificationHookInput
    |              println("Notification: ${'$'}{i.title} - ${'$'}{i.message}")
    |              HookOutput.allow()
    |          }
    |
    |          // on() で任意のイベントを登録
    |          on(HookEvent.SUBAGENT_STOP) { input, _, _ ->
    |              val i = input as SubagentStopHookInput
    |              println("SubagentStop: agent=${'$'}{i.agentId}, type=${'$'}{i.agentType}")
    |              HookOutput.allow()
    |          }
    |
    |          on(HookEvent.PRE_COMPACT) { input, _, _ ->
    |              val i = input as PreCompactHookInput
    |              println("PreCompact: trigger=${'$'}{i.trigger}")
    |              HookOutput.allow()
    |          }
    |
    |          on(HookEvent.SUBAGENT_START) { input, _, _ ->
    |              val i = input as SubagentStartHookInput
    |              println("SubagentStart: agent=${'$'}{i.agentId}")
    |              HookOutput.allow()
    |          }
    |
    |          on(HookEvent.PERMISSION_REQUEST) { input, _, _ ->
    |              val i = input as PermissionRequestHookInput
    |              println("PermissionRequest: ${'$'}{i.toolName}")
    |              HookOutput.allow()
    |          }
    |      }
    |  }
    """.trimMargin())
    println()

    // 実際にビルドして登録数を確認
    println("--- 実際にセッションオプションを構築 ---")
    try {
        val session = createSession {
            maxTurns = 1
            bypassPermissions()
            hooks {
                preToolUse { _, _, _ -> HookOutput.allow() }
                postToolUse { _, _, _ -> HookOutput.proceed() }
                postToolUseFailure { _, _, _ -> HookOutput.proceed() }
                userPromptSubmit { _, _, _ -> HookOutput.allow() }
                stop { _, _, _ -> HookOutput.allow() }
                notification { _, _, _ -> HookOutput.allow() }
                on(HookEvent.SUBAGENT_STOP) { _, _, _ -> HookOutput.allow() }
                on(HookEvent.PRE_COMPACT) { _, _, _ -> HookOutput.allow() }
                on(HookEvent.SUBAGENT_START) { _, _, _ -> HookOutput.allow() }
                on(HookEvent.PERMISSION_REQUEST) { _, _, _ -> HookOutput.allow() }
            }
        }
        println("  セッション作成成功 (全10イベント登録済み)")
        session.close()
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }

    println()
    println("■ Hook タイムアウト設定:")
    println("  hooks {")
    println("      preToolUse(\"Bash\", timeout = 30.0) { input, toolUseId, ctx ->")
    println("          // 30秒以内に処理しなければタイムアウト")
    println("          HookOutput.allow()")
    println("      }")
    println("  }")
}
