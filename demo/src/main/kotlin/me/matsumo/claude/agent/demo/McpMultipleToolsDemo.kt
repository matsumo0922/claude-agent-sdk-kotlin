/**
 * MCP 複数ツール・複数サーバーデモ
 *
 * 1つのサーバーに複数ツールを登録するパターンと、
 * SDK サーバーと Stdio サーバーを組み合わせるパターンを示す。
 */
package me.matsumo.claude.agent.demo

import kotlinx.serialization.Serializable
import me.matsumo.claude.agent.annotations.Description
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.mcp.createSdkMcpServer
import me.matsumo.claude.agent.types.AssistantMessage
import me.matsumo.claude.agent.types.McpSdkServerConfig
import me.matsumo.claude.agent.types.McpStdioServerConfig
import me.matsumo.claude.agent.types.Model
import me.matsumo.claude.agent.types.ResultMessage

// ── ツール型定義 ─────────────────────────────────────

@Serializable
data class TranslateArgs(
    @Description("翻訳する文章") val text: String,
    @Description("翻訳先の言語コード (例: 'en', 'ja')") val targetLang: String,
)

@Serializable
data class TranslateResult(val translated: String, val sourceLang: String, val targetLang: String)

@Serializable
data class SummarizeArgs(@Description("要約する文章") val text: String, @Description("最大文字数") val maxLength: Int = 100)

@Serializable
data class SummarizeResult(val summary: String, val originalLength: Int)

@Serializable
data class SentimentArgs(@Description("感情分析する文章") val text: String)

@Serializable
data class SentimentResult(val sentiment: String, val confidence: Double)

// ── デモ本体 ──────────────────────────────────────────

suspend fun runMcpMultipleToolsDemo() {
    println("=== MCP 複数ツール・複数サーバーデモ ===")
    println()

    // 1. 複数ツールを1つのサーバーに登録
    println("■ 1つのサーバーに複数ツールを登録:")
    val nlpServer = createSdkMcpServer("nlp-tools", "2.0.0") {
        tool<TranslateArgs, TranslateResult>("translate", "テキストを翻訳する") { args ->
            TranslateResult(
                translated = "[翻訳結果: ${args.text}]",
                sourceLang = "auto",
                targetLang = args.targetLang,
            )
        }

        tool<SummarizeArgs, SummarizeResult>("summarize", "テキストを要約する") { args ->
            val summary = if (args.text.length > args.maxLength) {
                args.text.take(args.maxLength) + "..."
            } else args.text
            SummarizeResult(summary = summary, originalLength = args.text.length)
        }

        tool<SentimentArgs, SentimentResult>("sentiment", "感情分析を行う") { _ ->
            SentimentResult(sentiment = "positive", confidence = 0.85)
        }
    }

    println("  サーバー: ${nlpServer.name} (v${nlpServer.version})")
    println("  ツール: translate, summarize, sentiment")
    println()

    // 2. SDK サーバー + Stdio サーバーの組み合わせ
    println("■ SDK サーバーと Stdio サーバーの組み合わせ:")
    println("  mcpServers {")
    println("      // インプロセス SDK サーバー")
    println("      put(\"nlp\", McpSdkServerConfig(nlpServer))")
    println("      // 外部 Stdio サーバー (別プロセスで起動)")
    println("      put(\"filesystem\", McpStdioServerConfig(")
    println("          command = \"npx\",")
    println("          args = listOf(\"-y\", \"@anthropic/mcp-server-filesystem\", \"/tmp\"),")
    println("          env = mapOf(\"NODE_ENV\" to \"production\")")
    println("      ))")
    println("  }")
    println()

    try {
        createSession {
            model = Model.SONNET
            maxTurns = 3
            bypassPermissions()
            mcpServers {
                // インプロセス SDK サーバー
                put("nlp", McpSdkServerConfig(nlpServer))
                // 外部 Stdio サーバー
                put("filesystem", McpStdioServerConfig(
                    command = "npx",
                    args = listOf("-y", "@anthropic/mcp-server-filesystem", "/tmp"),
                    env = mapOf("NODE_ENV" to "production"),
                ))
            }
        }.use { session ->
            session.connect()
            session.send("「今日は素晴らしい天気です」を英語に翻訳し、感情分析してください")
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
