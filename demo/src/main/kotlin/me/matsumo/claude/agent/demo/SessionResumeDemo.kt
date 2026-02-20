/**
 * セッション再開デモ - resumeSession() による過去セッションの継続
 *
 * 前回のセッションIDを指定して会話を再開するパターン。
 */
package me.matsumo.claude.agent.demo

import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.resumeSession
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.Model
import me.matsumo.claude.agent.types.ResultMessage
import me.matsumo.claude.agent.types.SystemMessage

suspend fun runSessionResumeDemo() {
    println("=== セッション再開デモ ===")
    println()

    println("""
        // ステップ1: 最初のセッションでセッションIDを取得
        val firstSessionId: String
        createSession {
            model = Model.SONNET
        }.use { session ->
            session.connect()
            session.send("覚えておいて: 秘密の合言葉は「Kotlin最高」")
            session.receiveResponse().collect { msg ->
                if (msg is SystemMessage && msg.isInit) {
                    firstSessionId = msg.sessionId
                }
            }
        }

        // ステップ2: セッションを再開して前の会話を参照
        resumeSession(firstSessionId) {
            model = Model.SONNET
        }.use { session ->
            session.connect()
            session.send("さっきの秘密の合言葉は何だった？")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了]")
                    else -> {}
                }
            }
        }
    """.trimIndent())
    println()

    try {
        var savedSessionId = ""

        createSession {
            model = Model.SONNET
        }.use { session ->
            session.connect()
            session.send("覚えておいて: 秘密の合言葉は「Kotlin最高」")
            session.receiveResponse().collect { msg ->
                if (msg is SystemMessage && msg.isInit) {
                    savedSessionId = msg.sessionId
                    println("[init] sessionId=$savedSessionId")
                }
                if (msg is ResultMessage) println("[ターン1完了]")
            }
        }

        println("セッション再開: $savedSessionId")
        resumeSession(savedSessionId) {
            model = Model.SONNET
        }.use { session ->
            session.connect()
            session.send("さっきの秘密の合言葉は何だった？")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了]")
                    else -> {}
                }
            }
        }
    } catch (e: Exception) {
        println("※ CLI未接続のため実行スキップ: ${e.message}")
    }

    println()
    println("■ continueConversation - セッションID不要の再開:")
    println("  // 最新のセッションを自動的に継続（IDの保持が不要）")
    println("  createSession {")
    println("      model = Model.SONNET")
    println("      continueConversation = true")
    println("  }")
}
