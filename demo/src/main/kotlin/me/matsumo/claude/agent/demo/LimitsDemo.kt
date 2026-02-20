/**
 * 制限設定デモ - maxTurns / maxBudgetUsd によるセッション制御
 *
 * コスト管理やループ防止のための制限パラメータの設定パターン。
 */
package me.matsumo.claude.agent.demo

import me.matsumo.claude.agent.prompt
import me.matsumo.claude.agent.types.Model

suspend fun runLimitsDemo() {
    println("=== 制限設定デモ ===")
    println()

    // --- maxTurns ---
    println("// maxTurns: エージェントのターン数を制限")
    println("""
        val result = prompt("複雑なタスク") {
            model = Model.SONNET
            maxTurns = 3  // 最大3ターンで終了
        }
        // result.subtype が "max_turns" なら上限到達
        println("サブタイプ: ${'$'}{result.subtype}")
    """.trimIndent())
    println()

    // --- maxBudgetUsd ---
    println("// maxBudgetUsd: 費用の上限をUSDで設定")
    println("""
        val result = prompt("大規模な分析タスク") {
            model = Model.OPUS
            maxBudgetUsd = 0.50  // 50セントまで
        }
        println("実際のコスト: ${'$'}${'$'}{result.totalCostUsd}")
    """.trimIndent())
    println()

    // --- 両方を組み合わせ ---
    println("// 両方を組み合わせて安全に実行")
    println("""
        val result = prompt("リファクタリングして") {
            model = Model.SONNET
            maxTurns = 10
            maxBudgetUsd = 1.00
            allowTools("Read", "Edit", "Glob")
            bypassPermissions()
        }
    """.trimIndent())
    println()

    try {
        val result = prompt("1+1は？一言で答えて") {
            model = Model.HAIKU
            maxTurns = 1
            maxBudgetUsd = 0.01
        }
        println("--- 結果 ---")
        println("応答: ${result.result}")
        println("ターン数: ${result.numTurns}")
        println("コスト: $${result.totalCostUsd} USD")
        println("サブタイプ: ${result.subtype}")
    } catch (e: Exception) {
        println("※ CLI未接続のため実行スキップ: ${e.message}")
    }
}
