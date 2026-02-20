/**
 * query() デモ - SDK の最もシンプルな呼び出しパターン
 *
 * query() はワンライナーでプロンプトを送信し、応答テキストを返す。
 */
package me.matsumo.claude.agent.demo

import me.matsumo.claude.agent.query
import me.matsumo.claude.agent.types.Model

suspend fun runQueryDemo() {
    println("=== query() デモ ===")
    println()

    // --- パターン1: 最小構成 ---
    println("// パターン1: 引数はプロンプト文字列だけ")
    println("""val answer = query("日本の首都は？")""")
    println()

    try {
        val answer = query("日本の首都は？")
        println("応答: $answer")
    } catch (e: Exception) {
        println("※ CLI未接続のため実行スキップ: ${e.message}")
    }

    println()

    // --- パターン2: オプション付き ---
    println("// パターン2: DSL ブロックでオプション指定")
    println("""
        val answer = query("Kotlinの特徴を一言で") {
            model = Model.HAIKU
            maxTurns = 1
        }
    """.trimIndent())
    println()

    try {
        val answer = query("Kotlinの特徴を一言で") {
            model = Model.HAIKU
            maxTurns = 1
        }
        println("応答: $answer")
    } catch (e: Exception) {
        println("※ CLI未接続のため実行スキップ: ${e.message}")
    }
}
