/**
 * モデル選択デモ - Model enum / modelId 文字列 / fallbackModel の使い分け
 *
 * モデル指定には3つの方法がある:
 * 1. Model enum (SONNET, OPUS, HAIKU)
 * 2. modelId 文字列 (任意のモデルID)
 * 3. fallbackModel (プライマリが使えない場合の代替)
 */
package me.matsumo.claude.agent.demo

import me.matsumo.claude.agent.query
import me.matsumo.claude.agent.types.Model

suspend fun runModelSelectionDemo() {
    println("=== モデル選択デモ ===")
    println()

    // --- パターン1: Model enum ---
    println("// パターン1: Model enum を使用")
    println("""
        query("テスト") {
            model = Model.SONNET   // Model.OPUS, Model.HAIKU も可
        }
    """.trimIndent())
    println()

    // --- パターン2: modelId 文字列 ---
    println("// パターン2: modelId で任意のモデルIDを指定")
    println("""
        query("テスト") {
            modelId = "claude-sonnet-4-20250514"  // 特定バージョン指定
        }
    """.trimIndent())
    println()

    // --- パターン3: fallbackModel ---
    println("// パターン3: fallbackModel で代替モデルを設定")
    println("""
        query("テスト") {
            model = Model.OPUS          // まずこれを試す
            fallbackModel = "sonnet"    // OPUS が使えなければ sonnet
        }
    """.trimIndent())
    println()

    // --- 注意: modelId が model より優先される ---
    println("※ modelId と model を両方設定した場合、modelId が優先されます")
    println()

    // 利用可能なModel enum一覧
    println("利用可能な Model enum:")
    Model.entries.forEach { m ->
        println("  Model.${m.name} → \"${m.modelId}\"")
    }

    println()
    try {
        val answer = query("1+1=?") { model = Model.HAIKU; maxTurns = 1 }
        println("実行結果 (HAIKU): $answer")
    } catch (e: Exception) {
        println("※ CLI未接続のため実行スキップ: ${e.message}")
    }
}
