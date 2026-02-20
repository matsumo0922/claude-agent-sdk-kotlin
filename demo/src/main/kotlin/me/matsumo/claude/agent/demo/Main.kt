/**
 * Claude Agent SDK for Kotlin デモアプリケーション
 *
 * SDK の各機能を対話的に試すためのメニュー型デモ。
 * カテゴリごとに分類されたデモを番号選択で実行できる。
 */
package me.matsumo.claude.agent.demo

import kotlinx.coroutines.runBlocking

/** デモ項目の定義 */
data class DemoEntry(val name: String, val action: suspend () -> Unit)

/** カテゴリとデモ項目のマッピング */
val demoCategories: List<Pair<String, List<DemoEntry>>> = listOf(
    "コア機能" to listOf(
        DemoEntry("query() - ワンライナー問い合わせ") { runQueryDemo() },
        DemoEntry("prompt() - 詳細結果取得") { runPromptDemo() },
        DemoEntry("createSession() - マルチターン対話") { runMultiTurnSessionDemo() },
        DemoEntry("resumeSession() - セッション再開") { runSessionResumeDemo() },
        DemoEntry("forkSession - セッション分岐") { runSessionForkDemo() },
        DemoEntry("Model 選択 - enum / modelId / fallback") { runModelSelectionDemo() },
        DemoEntry("制限設定 - maxTurns / maxBudgetUsd") { runLimitsDemo() },
    ),
    "メッセージ・ストリーミング" to listOf(
        DemoEntry("メッセージ型の分岐 (sealed when)") { runMessageTypesDemo() },
        DemoEntry("ContentBlock の種類") { runContentBlocksDemo() },
        DemoEntry("ストリーミング受信") { runStreamingDemo() },
        DemoEntry("textContent() ヘルパー") { runTextContentDemo() },
    ),
    "ツール・パーミッション" to listOf(
        DemoEntry("allowTools / disallowTools") { runToolPermissionDemo() },
        DemoEntry("canUseTool コールバック") { runCanUseToolDemo() },
        DemoEntry("動的パーミッション変更") { runDynamicPermissionDemo() },
    ),
    "Hook システム" to listOf(
        DemoEntry("hooks DSL - 基本フック設定") { runHooksDemo() },
        DemoEntry("hooks DSL - 全イベントタイプ") { runHookEventsDemo() },
    ),
    "MCP" to listOf(
        DemoEntry("MCP ツール DSL (型安全)") { runMcpToolDemo() },
        DemoEntry("MCP Raw ツール (手動スキーマ)") { runMcpRawToolDemo() },
        DemoEntry("MCP 複数ツール・複数サーバー") { runMcpMultipleToolsDemo() },
        DemoEntry("MCP スタンドアロンサーバー") { runMcpStandaloneDemo() },
        DemoEntry("JSON Schema 生成") { runJsonSchemaDemo() },
    ),
    "構造化出力・Thinking" to listOf(
        DemoEntry("structuredOutput<T>() パターン") { runStructuredOutputDemo() },
        DemoEntry("Extended Thinking / Effort 設定") { runThinkingDemo() },
    ),
    "セッション制御" to listOf(
        DemoEntry("interrupt / setModel / serverInfo") { runSessionControlDemo() },
        DemoEntry("checkpoint / rewindFiles") { runFileCheckpointDemo() },
    ),
    "高度な設定" to listOf(
        DemoEntry("DSL ビルダー総合 (Sandbox, Plugin, Agent 等)") { runAdvancedConfigDemo() },
    ),
)

fun main() = runBlocking {
    println("=".repeat(60))
    println("  Claude Agent SDK for Kotlin - デモアプリケーション")
    println("=".repeat(60))
    println()

    while (true) {
        var globalIndex = 1
        val indexMap = mutableMapOf<Int, DemoEntry>()

        for ((category, demos) in demoCategories) {
            println("--- $category ---")
            for (demo in demos) {
                println("  $globalIndex. ${demo.name}")
                indexMap[globalIndex] = demo
                globalIndex++
            }
            println()
        }

        println("  0. 終了")
        println()
        print("番号を入力してください > ")

        val input = readlnOrNull()?.trim() ?: break
        if (input == "0" || input.equals("q", ignoreCase = true)) {
            println("デモを終了します。")
            break
        }

        val number = input.toIntOrNull()
        val entry = number?.let { indexMap[it] }

        if (entry == null) {
            println("無効な番号です: $input")
            println()
            continue
        }

        println()
        println("=== ${entry.name} ===")
        println()

        try {
            entry.action()
        } catch (e: Exception) {
            println("[実行エラー] ${e::class.simpleName}: ${e.message}")
            println("※ Claude CLI が利用できない環境ではエラーになります")
        }

        println()
        println("-".repeat(60))
        println()
    }
}
