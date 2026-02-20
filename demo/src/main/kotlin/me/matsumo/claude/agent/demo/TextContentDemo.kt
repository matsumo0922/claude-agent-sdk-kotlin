/**
 * AssistantMessage.textContent() ヘルパーの使い方デモ。
 *
 * 複数の ContentBlock からテキストだけを簡単に抽出する方法を示す。
 */
package me.matsumo.claude.agent.demo

import kotlinx.coroutines.flow.filterIsInstance
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.types.AssistantMessage

suspend fun runTextContentDemo() {
    println("=== textContent() ヘルパーデモ ===")
    println()

    println("AssistantMessage.textContent() は全 TextBlock のテキストを結合して返します。")
    println("ToolUseBlock や ThinkingBlock は無視されるため、テキスト応答だけが欲しい場合に便利です。")
    println()
    println("""
    |  // 内部実装:
    |  fun textContent(): String =
    |      content.filterIsInstance<TextBlock>().joinToString("") { it.text }
    """.trimMargin())
    println()

    println("--- 使用例 ---")
    println("""
    |  // 方法1: textContent() でテキスト全体を取得
    |  session.receiveResponse()
    |      .filterIsInstance<AssistantMessage>()
    |      .collect { msg ->
    |          val text = msg.textContent()
    |          if (text.isNotBlank()) print(text)
    |      }
    |
    |  // 方法2: 個別の ContentBlock を処理
    |  session.receiveResponse()
    |      .filterIsInstance<AssistantMessage>()
    |      .collect { msg ->
    |          msg.content.forEach { block ->
    |              when (block) {
    |                  is TextBlock -> print(block.text)
    |                  is ThinkingBlock -> println("[思考中...]")
    |                  is ToolUseBlock -> println("[ツール: ${'$'}{block.name}]")
    |                  is ToolResultBlock -> {} // ユーザーメッセージ側で返る
    |              }
    |          }
    |      }
    """.trimMargin())
    println()

    // 実際のセッション
    println("--- 実際の textContent() 実行 ---")
    try {
        val session = createSession {
            maxTurns = 1
            bypassPermissions()
        }
        session.use { s ->
            s.connect()
            s.send("Kotlinとは何ですか？一言で。")
            s.receiveResponse()
                .filterIsInstance<AssistantMessage>()
                .collect { msg ->
                    val text = msg.textContent()
                    if (text.isNotBlank()) {
                        println("  textContent() の結果: $text")
                    }
                }
        }
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }
}
