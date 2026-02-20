/**
 * MCP Raw ツールデモ
 *
 * toolRaw() を使い、手動で JsonObject スキーマを指定するパターンを示す。
 * 型安全 DSL を使わずに、自由なスキーマ定義が必要な場合に有用。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.createSession
import com.anthropic.sdk.mcp.createSdkMcpServer
import com.anthropic.sdk.types.AssistantMessage
import com.anthropic.sdk.types.McpSdkServerConfig
import com.anthropic.sdk.types.Model
import com.anthropic.sdk.types.ResultMessage
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

suspend fun runMcpRawToolDemo() {
    println("=== MCP Raw ツールデモ ===")
    println()

    // 手動で JSON Schema を構築
    val calcSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "計算式 (例: '2 + 3')")
            }
        }
        putJsonArray("required") { add("expression") }
    }

    println("■ toolRaw() で手動スキーマのツールを定義:")
    println("  スキーマ: $calcSchema")
    println()

    val server = createSdkMcpServer("calculator") {
        toolRaw("calculate", "数式を計算する", calcSchema) { args ->
            val expression = args["expression"]?.jsonPrimitive?.content ?: "0"
            println("  [ツール実行] calculate(expression=$expression)")

            // 簡易計算 (デモ用)
            val result = try {
                expression.split("+").sumOf { it.trim().toDouble() }
            } catch (_: Exception) {
                0.0
            }

            buildJsonObject {
                put("result", result)
                put("expression", expression)
            }
        }
    }

    println("■ サーバー情報:")
    println("  名前: ${server.name}")
    println()

    // handleRequest で直接動作確認
    println("■ handleRequest で直接テスト:")
    val toolsListRequest = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", 1)
        put("method", "tools/list")
    }
    val toolsList = server.handleRequest(toolsListRequest)
    println("  tools/list 応答: $toolsList")
    println()

    val callRequest = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", 2)
        put("method", "tools/call")
        putJsonObject("params") {
            put("name", "calculate")
            putJsonObject("arguments") {
                put("expression", "10 + 20 + 30")
            }
        }
    }
    val callResult = server.handleRequest(callRequest)
    println("  tools/call 応答: $callResult")
    println()

    // セッション登録パターンも表示
    println("■ セッション登録:")
    try {
        createSession {
            model = Model.SONNET
            maxTurns = 2
            bypassPermissions()
            mcpServers { put("calculator", McpSdkServerConfig(server)) }
        }.use { session ->
            session.connect()
            session.send("10 + 20 + 30 を計算してください")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了]")
                    else -> {}
                }
            }
        }
    } catch (e: Exception) {
        println("  ※ CLI未接続のため実行スキップ: ${e.message}")
    }

    println()
}
