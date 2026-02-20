/**
 * Flow ベースのストリーミングデモ。
 *
 * receive() / receiveResponse() の使い方と、
 * includePartialMessages=true による StreamEvent の取得を示す。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.types.AssistantMessage
import com.anthropic.sdk.types.ResultMessage
import com.anthropic.sdk.types.StreamEvent
import com.anthropic.sdk.types.SystemMessage
import com.anthropic.sdk.types.UserMessage

suspend fun runStreamingDemo() {
    println("=== ストリーミングデモ ===")
    println()

    // --- receiveResponse() ---
    println("--- 1. receiveResponse() ---")
    println("ResultMessage まで自動的に collect する便利メソッドです。")
    println("""
    |  session.send("質問")
    |  session.receiveResponse().collect { msg ->
    |      when (msg) {
    |          is SystemMessage    -> println("初期化: ${'$'}{msg.sessionId}")
    |          is AssistantMessage -> print(msg.textContent())
    |          is ResultMessage    -> println("\n完了 (${'$'}{msg.durationMs}ms)")
    |          else -> {}
    |      }
    |  }
    """.trimMargin())
    println()

    // --- receive() + Flow 演算子 ---
    println("--- 2. receive() + Flow 演算子 ---")
    println("標準の Flow 演算子を自由に組み合わせられます。")
    println("""
    |  // AssistantMessage だけをフィルタしてテキストを取得
    |  session.receive()
    |      .filterIsInstance<AssistantMessage>()
    |      .map { it.textContent() }
    |      .filter { it.isNotBlank() }
    |      .collect { print(it) }
    """.trimMargin())
    println()

    // --- includePartialMessages + StreamEvent ---
    println("--- 3. StreamEvent (部分メッセージ) ---")
    println("includePartialMessages = true を設定すると StreamEvent が取得できます。")
    println("""
    |  val session = createSession {
    |      includePartialMessages = true
    |  }
    |  session.use { s ->
    |      s.connect()
    |      s.send("長い回答をしてください")
    |      s.receiveResponse().collect { msg ->
    |          when (msg) {
    |              is StreamEvent -> {
    |                  // APIストリームの生イベント (JsonObject)
    |                  println("event: ${'$'}{msg.event}")
    |              }
    |              is AssistantMessage -> print(msg.textContent())
    |              else -> {}
    |          }
    |      }
    |  }
    """.trimMargin())
    println()

    // 実際のセッション
    println("--- 実際のストリーミング実行 ---")
    try {
        val session = createSession {
            maxTurns = 1
            includePartialMessages = true
            bypassPermissions()
        }
        session.use { s ->
            s.connect()
            s.send("Kotlinの特徴を一文で述べてください")

            var streamEventCount = 0
            s.receiveResponse().collect { msg ->
                when (msg) {
                    is SystemMessage -> println("  [System] sessionId=${msg.sessionId}")
                    is StreamEvent -> streamEventCount++
                    is AssistantMessage -> println("  [Assistant] ${msg.textContent().take(100)}")
                    is ResultMessage -> {
                        println("  [Result] ${msg.subtype} (${msg.durationMs}ms)")
                        println("  StreamEvent 受信数: $streamEventCount")
                    }
                    is UserMessage -> {}
                }
            }
        }
    } catch (e: Exception) {
        println("  [スキップ] CLI が利用できません: ${e.message}")
    }
}
