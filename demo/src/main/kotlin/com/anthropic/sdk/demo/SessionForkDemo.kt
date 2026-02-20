/**
 * セッション分岐デモ - forkSession による会話の分岐
 *
 * resumeSession + forkSession = true で、元のセッションを変更せず
 * 新しいセッションIDで会話を分岐させるパターン。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.resumeSession
import com.anthropic.sdk.types.Model
import com.anthropic.sdk.types.ResultMessage
import com.anthropic.sdk.types.SystemMessage

suspend fun runSessionForkDemo() {
    println("=== セッション分岐デモ ===")
    println()

    println("""
        // 元のセッションIDを取得
        val originalSessionId = "..."

        // forkSession = true で分岐（元セッションは変更されない）
        resumeSession(originalSessionId) {
            model = Model.SONNET
            forkSession = true  // ← 新しいセッションIDが割り当てられる
        }.use { session ->
            session.connect()
            session.send("前の会話から分岐して、別の方向に進めて")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is SystemMessage -> {
                        if (msg.isInit) {
                            // 新しいセッションID（元とは異なる）
                            println("分岐後のセッションID: ${'$'}{msg.sessionId}")
                        }
                    }
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了]")
                    else -> {}
                }
            }
        }
    """.trimIndent())
    println()

    try {
        var originalId = ""
        createSession { model = Model.SONNET }.use { session ->
            session.connect()
            session.send("テスト")
            session.receiveResponse().collect { msg ->
                if (msg is SystemMessage && msg.isInit) originalId = msg.sessionId
            }
        }

        println("元セッション: $originalId")

        resumeSession(originalId) {
            model = Model.SONNET
            forkSession = true
        }.use { session ->
            session.connect()
            session.send("分岐テスト")
            session.receiveResponse().collect { msg ->
                if (msg is SystemMessage && msg.isInit) {
                    println("分岐セッション: ${msg.sessionId}")
                }
                if (msg is ResultMessage) println("[完了]")
            }
        }
    } catch (e: Exception) {
        println("※ CLI未接続のため実行スキップ: ${e.message}")
    }
}
