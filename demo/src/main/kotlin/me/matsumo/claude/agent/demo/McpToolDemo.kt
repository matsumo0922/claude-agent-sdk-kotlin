/**
 * MCP ツール DSL デモ
 *
 * createSdkMcpServer の tool<A, R>() DSL を使い、
 * 型安全なインプロセス MCP ツールを定義してセッションに登録するパターンを示す。
 */
package me.matsumo.claude.agent.demo

import kotlinx.serialization.Serializable
import me.matsumo.claude.agent.annotations.Description
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.mcp.createSdkMcpServer
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.McpSdkServerConfig
import me.matsumo.claude.agent.types.Model
import me.matsumo.claude.agent.types.ResultMessage

// ── ツールの入出力型 ──────────────────────────────────

@Description("都市の天気を取得するための引数")
@Serializable
data class WeatherArgs(
    @Description("都市名 (例: '東京', 'London')")
    val city: String,
    @Description("温度の単位")
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

@Serializable
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

@Serializable
data class WeatherResult(
    val city: String,
    val temperature: Double,
    val unit: String,
    val condition: String,
)

// ── デモ本体 ──────────────────────────────────────────

suspend fun runMcpToolDemo() {
    println("=== MCP ツール DSL デモ ===")
    println()

    // 1. インプロセス MCP サーバーを作成
    println("■ createSdkMcpServer で型安全ツールを定義:")
    val weatherServer = createSdkMcpServer("weather", "1.0.0") {
        // tool<Args, Result> DSL — スキーマは @Serializable + @Description から自動生成
        tool<WeatherArgs, WeatherResult>("get_weather", "指定都市の現在の天気を取得") { args ->
            println("  [ツール実行] get_weather(city=${args.city}, unit=${args.unit})")
            WeatherResult(
                city = args.city,
                temperature = if (args.unit == TemperatureUnit.CELSIUS) 22.5 else 72.5,
                unit = args.unit.name,
                condition = "晴れ",
            )
        }
    }

    println("  サーバー名: ${weatherServer.name}")
    println()

    // 2. セッションに McpSdkServerConfig で登録
    println("■ McpSdkServerConfig でセッションに登録:")
    println("  createSession {")
    println("      mcpServers { put(\"weather\", McpSdkServerConfig(weatherServer)) }")
    println("  }")
    println()

    try {
        createSession {
            model = Model.SONNET
            maxTurns = 3
            bypassPermissions()
            mcpServers {
                put("weather", McpSdkServerConfig(weatherServer))
            }
        }.use { session ->
            session.connect()
            session.send("東京の天気を教えてください")
            session.receiveResponse().collect { msg ->
                when (msg) {
                    is AssistantMessage -> print(msg.textContent())
                    is ResultMessage -> println("\n[完了] コスト: \$${msg.totalCostUsd}")
                    else -> {}
                }
            }
        }
    } catch (e: Exception) {
        println("  ※ CLI未接続のため実行スキップ: ${e.message}")
    }

    println()
}
