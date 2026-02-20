/**
 * マルチターンセッションデモ - createSession() による対話型セッション
 *
 * createSession → connect → send/receiveResponse を繰り返すパターン。
 * use {} でリソースを自動解放する。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.types.AssistantMessage
import com.anthropic.sdk.types.Model
import com.anthropic.sdk.types.ResultMessage
import com.anthropic.sdk.types.SystemMessage

suspend fun runMultiTurnSessionDemo() {
    println("=== マルチターンセッションデモ ===")
    println()

    println("""
        createSession {
            model = Model.SONNET
            maxTurns = 5
            allowTools("Read", "Glob", "Grep")
            bypassPermissions()
        }.use { session ->
            session.connect()

            // --- ターン1 ---
            session.send("このプロジェクトの構造を教えて")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is SystemMessage -> println("[init] sessionId=${'$'}{msg.sessionId}")
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了] ${'$'}{msg.subtype}")
                    else -> {}
                }
            }

            // --- ターン2 (前のコンテキストを保持) ---
            session.send("その中で一番重要なファイルは？")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了] コスト: ${'$'}${'$'}{msg.totalCostUsd}")
                    else -> {}
                }
            }
        }
    """.trimIndent())
    println()

    try {
        createSession {
            model = Model.SONNET
            maxTurns = 5
            allowTools("Read", "Glob", "Grep")
            bypassPermissions()
        }.use { session ->
            session.connect()

            session.send("こんにちは。Kotlinについて一言で教えて")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is SystemMessage -> println("[init] sessionId=${msg.sessionId}")
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了] ${msg.subtype}")
                    else -> {}
                }
            }

            session.send("もう少し詳しく教えて")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了] コスト: $${msg.totalCostUsd}")
                    else -> {}
                }
            }
        }
    } catch (e: Exception) {
        println("※ CLI未接続のため実行スキップ: ${e.message}")
    }
}
