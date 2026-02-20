/**
 * セッション中に setPermissionMode() で動的に権限を変更するデモ。
 */
package me.matsumo.claude.agent.demo

import kotlinx.coroutines.flow.filterIsInstance
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.PermissionMode

suspend fun runDynamicPermissionDemo() {
    println("=== 動的パーミッション変更デモ ===")
    println()

    println("setPermissionMode() でセッション中に権限モードを切り替えられます。")
    println()
    println("""
    |  session.use { s ->
    |      s.connect()
    |
    |      // 最初は計画モード (ツール実行なし)
    |      s.setPermissionMode(PermissionMode.PLAN)
    |      s.send("プロジェクト構成を分析してください")
    |      s.receiveResponse().collect { /* 計画のみ受信 */ }
    |
    |      // 計画に同意したら、編集許可モードに切り替え
    |      s.setPermissionMode(PermissionMode.ACCEPT_EDITS)
    |      s.send("計画どおりにリファクタリングしてください")
    |      s.receiveResponse().collect { /* ファイル編集を含む応答 */ }
    |
    |      // 全ツール許可に切り替え
    |      s.setPermissionMode(PermissionMode.BYPASS_PERMISSIONS)
    |      s.send("テストを実行してください")
    |      s.receiveResponse().collect { /* Bash 実行を含む応答 */ }
    |  }
    """.trimMargin())
    println()

    // 実際のセッション
    println("--- 実際の実行: PLAN -> ACCEPT_EDITS ---")
    try {
        val session = createSession {
            maxTurns = 1
            permissionMode = PermissionMode.PLAN
        }
        session.use { s ->
            s.connect()

            println("  現在のモード: PLAN")
            s.send("Hello")
            s.receiveResponse()
                .filterIsInstance<AssistantMessage>()
                .collect { println("  応答: ${it.textContent().take(100)}") }

            println("  モードを ACCEPT_EDITS に変更...")
            s.setPermissionMode(PermissionMode.ACCEPT_EDITS)
            println("  変更完了")
        }
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }
}
