/**
 * prompt() デモ - 応答テキストに加えてメタデータを取得するパターン
 *
 * PromptResult にはコスト、トークン数、ターン数、所要時間などが含まれる。
 */
package me.matsumo.claude.agent.demo

import me.matsumo.claude.agent.prompt
import me.matsumo.claude.agent.types.Model

suspend fun runPromptDemo() {
    println("=== prompt() デモ ===")
    println()

    println("""
        val result = prompt("量子コンピューティングを簡潔に説明して") {
            model = Model.SONNET
            maxTurns = 3
        }
        println("応答: ${'$'}{result.result}")
        println("セッションID: ${'$'}{result.sessionId}")
        println("コスト: ${'$'}${'$'}{result.totalCostUsd} USD")
        println("入力トークン: ${'$'}{result.inputTokens}")
        println("出力トークン: ${'$'}{result.outputTokens}")
        println("ターン数: ${'$'}{result.numTurns}")
        println("所要時間: ${'$'}{result.durationMs} ms")
        println("エラー: ${'$'}{result.isError}")
        println("サブタイプ: ${'$'}{result.subtype}")
    """.trimIndent())
    println()

    try {
        val result = prompt("量子コンピューティングを簡潔に説明して") {
            model = Model.SONNET
            maxTurns = 3
        }
        println("--- 結果 ---")
        println("応答: ${result.result}")
        println("セッションID: ${result.sessionId}")
        println("コスト: $${result.totalCostUsd} USD")
        println("入力トークン: ${result.inputTokens}")
        println("出力トークン: ${result.outputTokens}")
        println("ターン数: ${result.numTurns}")
        println("所要時間: ${result.durationMs} ms")
        println("エラー: ${result.isError}")
        println("サブタイプ: ${result.subtype}")
    } catch (e: Exception) {
        println("※ CLI未接続のため実行スキップ: ${e.message}")
    }
}
