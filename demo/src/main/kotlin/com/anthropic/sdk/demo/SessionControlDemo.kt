/**
 * セッション制御デモ
 *
 * ClaudeSDKClient の制御メソッド:
 * interrupt(), setModel(), getServerInfo(), getMcpStatus() の使い方を示す。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.mcp.createSdkMcpServer
import com.anthropic.sdk.types.AssistantMessage
import com.anthropic.sdk.types.McpSdkServerConfig
import com.anthropic.sdk.types.Model
import com.anthropic.sdk.types.ResultMessage
import kotlinx.serialization.Serializable

@Serializable
data class EchoArgs(val message: String)

@Serializable
data class EchoResult(val echo: String)

suspend fun runSessionControlDemo() {
    println("=== セッション制御デモ ===")
    println()

    val echoServer = createSdkMcpServer("echo") {
        tool<EchoArgs, EchoResult>("echo", "メッセージをエコーする") { args ->
            EchoResult(echo = args.message)
        }
    }

    println("■ interrupt() — 実行中のターンを中断:")
    println("  session.send(\"長い処理を実行...\")")
    println("  delay(1000)")
    println("  session.interrupt()  // 中断シグナルを送信")
    println()

    println("■ setModel() — セッション中にモデルを変更:")
    println("  session.setModel(\"opus\")    // Opus に切り替え")
    println("  session.setModel(\"sonnet\")  // Sonnet に戻す")
    println("  session.setModel(null)       // デフォルトにリセット")
    println()

    println("■ getServerInfo() — CLI のサーバー情報を取得:")
    println("  val info = session.getServerInfo()")
    println("  // → {\"version\": \"2.x.x\", \"capabilities\": {...}}")
    println()

    println("■ getMcpStatus() — MCP サーバーの接続状態を確認:")
    println("  val status = session.getMcpStatus()")
    println("  // → {\"servers\": [{\"name\": \"echo\", \"status\": \"connected\"}]}")
    println()

    // 実際の実行
    try {
        createSession {
            model = Model.SONNET
            maxTurns = 2
            bypassPermissions()
            mcpServers {
                put("echo", McpSdkServerConfig(echoServer))
            }
        }.use { session ->
            session.connect()

            // getServerInfo
            val info = session.getServerInfo()
            println("  getServerInfo() = $info")

            // getMcpStatus
            val status = session.getMcpStatus()
            println("  getMcpStatus() = $status")

            // setModel
            session.setModel("sonnet")
            println("  setModel(\"sonnet\") 完了")

            // send + receive
            session.send("Hello")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> print("  応答: ${msg.textContent().take(100)}")
                    is ResultMessage -> println("\n  [完了]")
                    else -> {}
                }
            }

            // interrupt の例 (2ターン目)
            session.send("非常に長い文章を書いてください")
            session.interrupt()
            println("  interrupt() 送信済み")
        }
    } catch (e: Exception) {
        println("  ※ CLI未接続のため実行スキップ: ${e.message}")
    }

    // disconnect() の説明
    println("■ disconnect() - 明示的な切断:")
    println("  // use{} を使わず手動でライフサイクル管理する場合")
    println("  val session = createSession { model = Model.SONNET }")
    println("  session.connect()")
    println("  session.send(\"質問\")")
    println("  session.receiveResponse().collect { ... }")
    println("  session.disconnect()  // 明示的に切断")
    println()
}
