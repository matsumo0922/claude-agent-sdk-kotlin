/**
 * ツールの許可/拒否設定と PermissionMode のデモ。
 *
 * allowTools / disallowTools および全4つの PermissionMode 値を示す。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.types.AssistantMessage
import com.anthropic.sdk.types.PermissionMode
import kotlinx.coroutines.flow.filterIsInstance

suspend fun runToolPermissionDemo() {
    println("=== ツール許可/拒否 & PermissionMode デモ ===")
    println()

    // --- allowTools / disallowTools ---
    println("--- 1. allowTools / disallowTools ---")
    println("""
    |  createSession {
    |      // 特定ツールのみ許可
    |      allowTools("Read", "Glob", "Grep")
    |
    |      // 特定ツールを明示的に禁止
    |      disallowTools("Bash", "Write")
    |  }
    """.trimMargin())
    println()

    // --- PermissionMode ---
    println("--- 2. PermissionMode の全バリアント ---")
    PermissionMode.entries.forEach { mode ->
        val desc = when (mode) {
            PermissionMode.DEFAULT -> "デフォルト: ツール使用時にユーザー確認を求める"
            PermissionMode.ACCEPT_EDITS -> "ファイル編集を自動承認（読み取りは常に許可）"
            PermissionMode.PLAN -> "計画モード: ツール実行なし、回答のみ"
            PermissionMode.BYPASS_PERMISSIONS -> "全ツールを無条件で許可（CI/自動化向け）"
        }
        println("  $mode - $desc")
    }
    println()

    println("--- 3. PermissionMode の設定例 ---")
    println("""
    |  // DSL で設定
    |  createSession {
    |      permissionMode = PermissionMode.ACCEPT_EDITS
    |  }
    |
    |  // bypassPermissions() ショートカット
    |  createSession {
    |      bypassPermissions()  // = PermissionMode.BYPASS_PERMISSIONS
    |  }
    """.trimMargin())
    println()

    // 実際のセッション
    println("--- 実際の実行: 読み取り専用セッション ---")
    try {
        val session = createSession {
            allowTools("Read", "Glob", "Grep")
            disallowTools("Bash", "Write")
            permissionMode = PermissionMode.ACCEPT_EDITS
            maxTurns = 1
        }
        session.use { s ->
            s.connect()
            s.send("現在のディレクトリを教えてください")
            s.receiveResponse()
                .filterIsInstance<AssistantMessage>()
                .collect { println("  応答: ${it.textContent().take(120)}") }
        }
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }
}
