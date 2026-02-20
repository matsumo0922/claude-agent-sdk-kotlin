/**
 * SDKMessage の全5タイプを網羅的に紹介するデモ。
 *
 * sealed interface による exhaustive when 式を示す。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.types.AssistantMessage
import com.anthropic.sdk.types.AssistantMessageError
import com.anthropic.sdk.types.ResultMessage
import com.anthropic.sdk.types.StreamEvent
import com.anthropic.sdk.types.SystemMessage
import com.anthropic.sdk.types.UserMessage

suspend fun runMessageTypesDemo() {
    println("=== SDKMessage タイプ一覧デモ ===")
    println()
    println("SDKMessage は sealed interface で、以下の5つのサブタイプを持ちます:")
    println("  1. SystemMessage   - セッション初期化やエラーなどのシステムイベント")
    println("  2. AssistantMessage - Claude からの応答（テキスト・ツール呼び出しなど）")
    println("  3. UserMessage      - ユーザー入力やツール実行結果")
    println("  4. ResultMessage    - ターン終了時のメタ情報（コスト・トークン数等）")
    println("  5. StreamEvent      - 部分的なストリーミングイベント")
    println()

    // exhaustive when 式のデモ
    println("--- exhaustive when 式 ---")
    println("""
    |  when (message) {
    |      is SystemMessage -> {
    |          // sessionId, subtype, data, isInit が利用可能
    |          if (message.isInit) println("セッション開始: ${'$'}{message.sessionId}")
    |      }
    |      is AssistantMessage -> {
    |          // content (List<ContentBlock>), model, parentToolUseId, error
    |          println("応答: ${'$'}{message.textContent()}")
    |          message.error?.let { println("エラー: ${'$'}it") }
    |      }
    |      is UserMessage -> {
    |          // content, uuid, parentToolUseId, toolUseResult
    |          println("ユーザー: ${'$'}{message.content}")
    |      }
    |      is ResultMessage -> {
    |          // subtype, durationMs, isError, numTurns, totalCostUsd, usage, result
    |          println("完了: ${'$'}{message.result} (コスト: ${'$'}{message.totalCostUsd} USD)")
    |      }
    |      is StreamEvent -> {
    |          // uuid, event (JsonObject), parentToolUseId
    |          println("ストリーム: ${'$'}{message.event}")
    |      }
    |  }
    """.trimMargin())
    println()

    // 実際の SDK 呼び出し
    println("--- 実際のセッションでメッセージタイプを確認 ---")
    try {
        val session = createSession {
            maxTurns = 1
            bypassPermissions()
        }
        session.use { s ->
            s.connect()
            s.send("こんにちは")
            s.receiveResponse().collect { message ->
                val typeName = when (message) {
                    is SystemMessage -> "SystemMessage (subtype=${message.subtype}, isInit=${message.isInit})"
                    is AssistantMessage -> "AssistantMessage (blocks=${message.content.size}, model=${message.model})"
                    is UserMessage -> "UserMessage (uuid=${message.uuid})"
                    is ResultMessage -> "ResultMessage (subtype=${message.subtype}, turns=${message.numTurns})"
                    is StreamEvent -> "StreamEvent (uuid=${message.uuid})"
                }
                println("  受信: $typeName")
            }
        }
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }

    println()
    println("=== AssistantMessageError の全バリアント ===")
    AssistantMessageError.entries.forEach { error ->
        println("  - $error")
    }
}
