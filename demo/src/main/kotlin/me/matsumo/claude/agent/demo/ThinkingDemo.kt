/**
 * 拡張思考 (Thinking) デモ
 *
 * ThinkingConfig の全バリアント (Adaptive, Enabled, Disabled) と
 * Effort の全レベルの使い方、ThinkingBlock の取得方法を示す。
 */
package me.matsumo.claude.agent.demo

import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.Effort
import me.matsumo.claude.agent.types.Model
import me.matsumo.claude.agent.types.ResultMessage
import me.matsumo.claude.agent.types.TextBlock
import me.matsumo.claude.agent.types.ThinkingBlock
import me.matsumo.claude.agent.types.ThinkingConfig

suspend fun runThinkingDemo() {
    println("=== 拡張思考 (Thinking) デモ ===")
    println()

    // 1. ThinkingConfig の全バリアント
    println("■ ThinkingConfig のバリアント:")
    println("  - Adaptive   : モデルが自動的に思考量を調整")
    println("  - Enabled(n) : budgetTokens で思考トークン数を指定")
    println("  - Disabled   : 拡張思考を無効化")
    println()

    val configs = mapOf(
        "Adaptive" to ThinkingConfig.Adaptive,
        "Enabled(10000)" to ThinkingConfig.Enabled(budgetTokens = 10000),
        "Disabled" to ThinkingConfig.Disabled,
    )

    configs.forEach { (label, config) ->
        println("  thinking = ThinkingConfig.$label")
        println("    → $config")
    }
    println()

    // 2. Effort の全レベル
    println("■ Effort レベル:")
    Effort.entries.forEach { effort ->
        val desc = when (effort) {
            Effort.LOW -> "最小限の思考で高速応答"
            Effort.MEDIUM -> "バランスの取れた思考量"
            Effort.HIGH -> "深い思考で高品質な応答"
            Effort.MAX -> "最大限の思考を使用"
        }
        println("  Effort.${effort.name.padEnd(6)} → $desc")
    }
    println()

    // 3. Thinking + Effort の組み合わせ
    println("■ 推奨の組み合わせ:")
    println("  - 高速応答:   thinking = Disabled, effort = LOW")
    println("  - 標準:       thinking = Adaptive, effort = MEDIUM")
    println("  - 高品質:     thinking = Enabled(10000), effort = HIGH")
    println("  - 最高品質:   thinking = Enabled(50000), effort = MAX")
    println()

    // 4. ThinkingBlock を取得するデモ
    println("■ ThinkingBlock の取得:")
    try {
        createSession {
            model = Model.SONNET
            thinking = ThinkingConfig.Enabled(budgetTokens = 10000)
            effort = Effort.HIGH
            maxTurns = 1
            bypassPermissions()
        }.use { session ->
            session.connect()
            session.send("素数が無限に存在することを証明してください")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> {
                        msg.content.forEach { block ->
                            when (block) {
                                is ThinkingBlock -> {
                                    val t = block.thinking
                                    println("  [思考] ${if (t.length > 100) t.take(100) + "..." else t}")
                                    val s = block.signature
                                    println("  [署名] ${if (s.length > 20) s.take(20) + "..." else s}")
                                }
                                is TextBlock -> {
                                    val t = block.text
                                    println("  [応答] ${if (t.length > 200) t.take(200) + "..." else t}")
                                }
                                else -> {}
                            }
                        }
                    }
                    is ResultMessage -> println("  [完了] ターン数: ${msg.numTurns}")
                    else -> {}
                }
            }
        }
    } catch (e: Exception) {
        println("  ※ CLI未接続のため実行スキップ: ${e.message}")
        println()
        println("  // ThinkingBlock はこのように取得できます:")
        println("  msg.content.forEach { block ->")
        println("      when (block) {")
        println("          is ThinkingBlock -> println(block.thinking)")
        println("          is TextBlock -> println(block.text)")
        println("          else -> {}")
        println("      }")
        println("  }")
    }

    println()
}
