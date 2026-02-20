/**
 * PreToolUse / PostToolUse フックと HookOutput DSL のデモ。
 *
 * HookOutput.allow(), deny(), modify() の使い分けを示す。
 */
package me.matsumo.claude.agent.demo

import kotlinx.coroutines.flow.filterIsInstance
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.HookOutput
import me.matsumo.claude.agent.types.PostToolUseHookInput
import me.matsumo.claude.agent.types.PreToolUseHookInput

suspend fun runHooksDemo() {
    println("=== Hooks (PreToolUse / PostToolUse) デモ ===")
    println()

    // --- HookOutput の種類 ---
    println("--- HookOutput の4つの応答 ---")
    println("  HookOutput.allow()        - ツール実行を許可（デフォルト）")
    println("  HookOutput.deny(reason)   - ツール実行を拒否 (理由付き)")
    println("  HookOutput.proceed()      - PostToolUse 後に続行（allow と同等）")
    println("  HookOutput.modify(input)  - 入力を書き換えて実行")
    println()

    // --- PreToolUse フック ---
    println("--- 1. PreToolUse フック ---")
    println("""
    |  createSession {
    |      hooks {
    |          // 全ツールに対するフック
    |          preToolUse { input, toolUseId, context ->
    |              val preInput = input as PreToolUseHookInput
    |              println("ツール実行前: ${'$'}{preInput.toolName}")
    |              HookOutput.allow()
    |          }
    |
    |          // 特定ツールのみ (matcher = 正規表現)
    |          preToolUse(matcher = "Bash") { input, _, _ ->
    |              HookOutput.deny("Bash は禁止です")
    |          }
    |
    |          // 入力を書き換え
    |          preToolUse(matcher = "Write") { input, _, _ ->
    |              val preInput = input as PreToolUseHookInput
    |              HookOutput.modify(buildJsonObject {
    |                  put("path", JsonPrimitive("/safe/output.txt"))
    |                  put("content", preInput.toolInput["content"] ?: JsonPrimitive(""))
    |              })
    |          }
    |      }
    |  }
    """.trimMargin())
    println()

    // --- PostToolUse フック ---
    println("--- 2. PostToolUse フック ---")
    println("""
    |  createSession {
    |      hooks {
    |          postToolUse(matcher = "Read") { input, toolUseId, context ->
    |              val postInput = input as PostToolUseHookInput
    |              println("Read 完了: ${'$'}{postInput.toolResponse}")
    |              HookOutput.proceed()
    |          }
    |      }
    |  }
    """.trimMargin())
    println()

    // 実際のセッション
    println("--- 実際の実行: ツール使用をログするフック ---")
    try {
        val session = createSession {
            maxTurns = 1
            bypassPermissions()
            hooks {
                preToolUse { input, toolUseId, _ ->
                    val preInput = input as PreToolUseHookInput
                    println("  [PreToolUse] ${preInput.toolName} (id=$toolUseId)")
                    HookOutput.allow()
                }
                postToolUse { input, toolUseId, _ ->
                    val postInput = input as PostToolUseHookInput
                    println("  [PostToolUse] ${postInput.toolName} 完了")
                    HookOutput.proceed()
                }
            }
        }
        session.use { s ->
            s.connect()
            s.send("現在時刻を教えてください")
            s.receiveResponse()
                .filterIsInstance<AssistantMessage>()
                .collect { println("  応答: ${it.textContent().take(100)}") }
        }
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }
}
