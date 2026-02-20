/**
 * canUseTool コールバックによるツール許可制御のデモ。
 *
 * PermissionResultAllow (updatedInput, updatedPermissions 付き) と
 * PermissionResultDeny の使い分けを示す。
 */
package me.matsumo.claude.agent.demo

import kotlinx.coroutines.flow.filterIsInstance
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.PermissionResultAllow
import me.matsumo.claude.agent.types.PermissionResultDeny

suspend fun runCanUseToolDemo() {
    println("=== canUseTool コールバックデモ ===")
    println()

    println("canUseTool はツール実行前に呼ばれ、Allow/Deny を返すコールバックです。")
    println()

    // --- PermissionResultAllow ---
    println("--- 1. PermissionResultAllow ---")
    println("""
    |  createSession {
    |      canUseTool { toolName, toolInput, context ->
    |          // 単純な許可
    |          PermissionResultAllow()
    |
    |          // 入力を書き換えて許可
    |          PermissionResultAllow(
    |              updatedInput = buildJsonObject {
    |                  put("path", JsonPrimitive("/safe/path"))
    |              }
    |          )
    |
    |          // パーミッション更新を付与して許可
    |          PermissionResultAllow(
    |              updatedPermissions = listOf(
    |                  PermissionUpdate(
    |                      type = PermissionUpdateType.ADD_RULES,
    |                      rules = listOf(PermissionRuleValue(toolName = "Read")),
    |                      behavior = PermissionBehavior.ALLOW,
    |                      destination = PermissionUpdateDestination.SESSION,
    |                  )
    |              )
    |          )
    |      }
    |  }
    """.trimMargin())
    println()

    // --- PermissionResultDeny ---
    println("--- 2. PermissionResultDeny ---")
    println("""
    |  canUseTool { toolName, toolInput, context ->
    |      // 拒否（理由メッセージ付き）
    |      PermissionResultDeny(message = "このツールは使用禁止です")
    |
    |      // 拒否 + セッション中断
    |      PermissionResultDeny(
    |          message = "セキュリティ違反を検出しました",
    |          interrupt = true,
    |      )
    |  }
    """.trimMargin())
    println()

    // --- ToolPermissionContext ---
    println("--- 3. ToolPermissionContext ---")
    println("""
    |  canUseTool { toolName, toolInput, context ->
    |      // context.suggestions にはCLIからの推奨パーミッション設定が含まれる
    |      println("提案数: ${'$'}{context.suggestions.size}")
    |      context.suggestions.forEach { update ->
    |          println("  type=${'$'}{update.type}, rules=${'$'}{update.rules}")
    |      }
    |      PermissionResultAllow()
    |  }
    """.trimMargin())
    println()

    // 実際のセッション
    println("--- 実際の実行: Bash を拒否する canUseTool ---")
    try {
        val session = createSession {
            maxTurns = 2
            canUseTool { toolName, toolInput, context ->
                if (toolName == "Bash") {
                    println("  [canUseTool] Bash を拒否しました")
                    PermissionResultDeny(message = "Bash は許可されていません")
                } else {
                    println("  [canUseTool] $toolName を許可しました")
                    PermissionResultAllow()
                }
            }
        }
        session.use { s ->
            s.connect()
            s.send("ls コマンドを実行してください")
            s.receiveResponse()
                .filterIsInstance<AssistantMessage>()
                .collect { println("  応答: ${it.textContent().take(120)}") }
        }
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }
}
