/**
 * ContentBlock の全4タイプを網羅的に紹介するデモ。
 *
 * sealed interface による exhaustive when 式を示す。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.types.AssistantMessage
import com.anthropic.sdk.types.TextBlock
import com.anthropic.sdk.types.ThinkingBlock
import com.anthropic.sdk.types.ToolResultBlock
import com.anthropic.sdk.types.ToolUseBlock
import kotlinx.coroutines.flow.filterIsInstance

suspend fun runContentBlocksDemo() {
    println("=== ContentBlock タイプ一覧デモ ===")
    println()
    println("ContentBlock は sealed interface で、以下の4つのサブタイプを持ちます:")
    println("  1. TextBlock       - テキスト応答")
    println("  2. ThinkingBlock   - 拡張思考 (Extended Thinking) の出力")
    println("  3. ToolUseBlock    - ツール呼び出し要求")
    println("  4. ToolResultBlock - ツール実行結果")
    println()

    // exhaustive when 式のデモ
    println("--- exhaustive when 式 ---")
    println("""
    |  fun processBlock(block: ContentBlock) {
    |      when (block) {
    |          is TextBlock -> {
    |              // テキスト内容
    |              println("テキスト: ${'$'}{block.text}")
    |          }
    |          is ThinkingBlock -> {
    |              // 思考内容 + 署名
    |              println("思考: ${'$'}{block.thinking}")
    |              println("署名: ${'$'}{block.signature}")
    |          }
    |          is ToolUseBlock -> {
    |              // ツール名, ID, 入力パラメータ (JsonObject)
    |              println("ツール呼び出し: ${'$'}{block.name} (id=${'$'}{block.id})")
    |              println("入力: ${'$'}{block.input}")
    |          }
    |          is ToolResultBlock -> {
    |              // ツール結果 (toolUseId で対応する ToolUseBlock と紐付く)
    |              println("結果 (toolUseId=${'$'}{block.toolUseId}): ${'$'}{block.content}")
    |              if (block.isError == true) println("  -> エラーあり")
    |          }
    |      }
    |  }
    """.trimMargin())
    println()

    // 実際のセッションで ContentBlock を確認
    println("--- 実際のセッションで ContentBlock を確認 ---")
    try {
        val session = createSession {
            maxTurns = 1
            bypassPermissions()
        }
        session.use { s ->
            s.connect()
            s.send("1+1は？")
            s.receiveResponse()
                .filterIsInstance<AssistantMessage>()
                .collect { msg ->
                    msg.content.forEach { block ->
                        val desc = when (block) {
                            is TextBlock -> {
                                val t = block.text
                                "TextBlock: \"${if (t.length > 80) t.take(80) + "..." else t}\""
                            }
                            is ThinkingBlock -> {
                                val t = block.thinking
                                "ThinkingBlock: \"${if (t.length > 60) t.take(60) + "..." else t}\""
                            }
                            is ToolUseBlock -> "ToolUseBlock: ${block.name}(${block.input})"
                            is ToolResultBlock -> "ToolResultBlock: toolUseId=${block.toolUseId}"
                        }
                        println("  $desc")
                    }
                }
        }
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }
}
