/**
 * MCP スタンドアロンサーバーデモ
 *
 * runMcpServer() を使って MCP サーバーを独立プロセスとして実行するパターンを示す。
 * 実際の実行は stdin/stdout で JSON-RPC を処理するため、
 * このデモではコードパターンの紹介に留める。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.annotations.Description
import com.anthropic.sdk.mcp.createSdkMcpServer
import kotlinx.serialization.Serializable

// ── ツール型 ─────────────────────────────────────────

@Serializable
data class FileSearchArgs(
    @Description("検索パターン (glob)") val pattern: String,
    @Description("検索ディレクトリ") val directory: String = ".",
)

@Serializable
data class FileSearchResult(val files: List<String>, val count: Int)

// ── デモ本体 ──────────────────────────────────────────

suspend fun runMcpStandaloneDemo() {
    println("=== MCP スタンドアロンサーバーデモ ===")
    println()

    println("■ スタンドアロン MCP サーバーの構成パターン:")
    println()
    println("  // 1. サーバーを定義")
    println("  val server = createSdkMcpServer(\"file-search\") {")
    println("      tool<FileSearchArgs, FileSearchResult>(\"search\", \"ファイル検索\") { args ->")
    println("          // 実際の検索ロジック")
    println("          FileSearchResult(files = listOf(\"a.kt\", \"b.kt\"), count = 2)")
    println("      }")
    println("  }")
    println()
    println("  // 2. main() からスタンドアロン実行")
    println("  fun main() = runMcpServer(server)")
    println()

    // 実際にサーバーオブジェクトは作成して構造を確認
    val server = createSdkMcpServer("file-search", "1.0.0") {
        tool<FileSearchArgs, FileSearchResult>("search", "ファイルをglobパターンで検索") { args ->
            FileSearchResult(
                files = listOf("src/main.kt", "src/utils.kt"),
                count = 2,
            )
        }
    }

    println("■ 作成されたサーバー:")
    println("  名前: ${server.name}, バージョン: ${server.version}")
    println("  ツール: search")
    println()

    println("■ runMcpServer(server) の動作:")
    println("  - stdin から JSON-RPC リクエストを1行ずつ読み取り")
    println("  - server.handleRequest() で処理")
    println("  - stdout に JSON-RPC レスポンスを出力")
    println("  - EOF まで繰り返す")
    println()

    println("■ Claude Code から Stdio サーバーとして呼び出す場合:")
    println("  mcpServers {")
    println("      put(\"file-search\", McpStdioServerConfig(")
    println("          command = \"java\",")
    println("          args = listOf(\"-jar\", \"my-mcp-server.jar\")")
    println("      ))")
    println("  }")
    println()

    // 注: runMcpServer(server) は実際には呼ばない (stdin をブロックするため)
    println("  ※ runMcpServer() は stdin/stdout を占有するため、このデモでは実行しません")
    println()
}
