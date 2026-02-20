/**
 * 高度な設定デモ
 *
 * SessionOptionsBuilder DSL の全機能を網羅するショーケース:
 * SandboxSettings, SdkPluginConfig, AgentDefinition,
 * SettingSource, SdkBeta, env, extraArgs, addDirs など。
 */
package me.matsumo.claude.agent.demo

import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.types.AgentDefinition
import me.matsumo.claude.agent.types.Effort
import me.matsumo.claude.agent.types.McpHttpServerConfig
import me.matsumo.claude.agent.types.McpSSEServerConfig
import me.matsumo.claude.agent.types.Model
import me.matsumo.claude.agent.types.SandboxIgnoreViolations
import me.matsumo.claude.agent.types.SandboxNetworkConfig
import me.matsumo.claude.agent.types.SandboxSettings
import me.matsumo.claude.agent.types.SdkBeta
import me.matsumo.claude.agent.types.SdkPluginConfig
import me.matsumo.claude.agent.types.SettingSource
import me.matsumo.claude.agent.types.SystemPromptPreset
import me.matsumo.claude.agent.types.ThinkingConfig
import me.matsumo.claude.agent.types.ToolsPreset

suspend fun runAdvancedConfigDemo() {
    println("=== 高度な設定デモ ===")
    println()

    // 1. SandboxSettings
    println("■ SandboxSettings — Bash コマンドのサンドボックス設定:")
    val sandboxConfig = SandboxSettings(
        enabled = true,
        autoAllowBashIfSandboxed = true,
        excludedCommands = listOf("rm -rf", "sudo"),
        allowUnsandboxedCommands = false,
        network = SandboxNetworkConfig(
            allowLocalBinding = true,
            httpProxyPort = 8080,
        ),
        ignoreViolations = SandboxIgnoreViolations(
            file = listOf("/tmp/*"),
        ),
    )
    println("  $sandboxConfig")
    println()

    // 2. SdkPluginConfig
    println("■ SdkPluginConfig — プラグイン設定:")
    val plugin = SdkPluginConfig(type = "local", path = "/path/to/my-plugin")
    println("  $plugin")
    println()

    // 3. AgentDefinition
    println("■ AgentDefinition — カスタムエージェント:")
    val codeReviewer = AgentDefinition(
        description = "コードレビューを行うエージェント",
        prompt = "あなたはKotlinのコードレビュアーです。品質、可読性、パフォーマンスの観点でレビューしてください。",
        tools = listOf("Read", "Glob", "Grep"),
        model = "sonnet",
    )
    println("  $codeReviewer")
    println()

    // 4. SettingSource
    println("■ SettingSource — 設定の読み込み元:")
    SettingSource.entries.forEach { source ->
        val desc = when (source) {
            SettingSource.USER -> "ユーザー設定 (~/.config/claude/)"
            SettingSource.PROJECT -> "プロジェクト設定 (.claude/)"
            SettingSource.LOCAL -> "ローカル設定 (.claude.local/)"
        }
        println("  SettingSource.${source.name} → $desc")
    }
    println()

    // 5. SdkBeta
    println("■ SdkBeta — ベータ機能フラグ:")
    println("  SdkBeta.CONTEXT_1M → 1M コンテキストウィンドウ")
    println()

    // 6. フル DSL ビルダーショーケース
    println("■ フル DSL ビルダーの例:")
    println("  createSession {")
    println("      model = Model.SONNET")
    println("      fallbackModel = \"haiku\"")
    println("      maxTurns = 10")
    println("      maxBudgetUsd = 1.0")
    println("      thinking = ThinkingConfig.Adaptive")
    println("      effort = Effort.HIGH")
    println("      enableFileCheckpointing = true")
    println("      sandbox = SandboxSettings(enabled = true, ...)")
    println("      includePartialMessages = true")
    println("      bypassPermissions()")
    println("      allowTools(\"Read\", \"Glob\", \"Grep\", \"Bash\")")
    println("      disallowTools(\"Write\")")
    println("      betas(SdkBeta.CONTEXT_1M)")
    println("      plugin(SdkPluginConfig(path = \"...\"))")
    println("      settingSources(SettingSource.USER, SettingSource.PROJECT)")
    println("      addDirs(\"/extra/dir1\", \"/extra/dir2\")")
    println("      env { put(\"MY_API_KEY\", \"xxx\"); put(\"DEBUG\", \"true\") }")
    println("      extraArgs { put(\"--custom-flag\", \"value\") }")
    println("      agents { put(\"reviewer\", AgentDefinition(...)) }")
    println("      hooks { preToolUse(\"Bash\") { ... } }")
    println("      mcpServers { put(\"my-server\", McpSdkServerConfig(...)) }")
    println("      outputFormat(OutputFormat(type = \"json_schema\", schema = ...))")
    println("      canUseTool { tool, input, ctx -> PermissionResultAllow() }")
    println("  }")
    println()

    // 実際にビルドして構造を確認
    try {
        createSession {
            model = Model.SONNET
            fallbackModel = "haiku"
            maxTurns = 10
            maxBudgetUsd = 1.0
            thinking = ThinkingConfig.Adaptive
            effort = Effort.HIGH
            enableFileCheckpointing = true
            sandbox = sandboxConfig
            includePartialMessages = true
            bypassPermissions()
            allowTools("Read", "Glob", "Grep", "Bash")
            disallowTools("Write")
            betas(SdkBeta.CONTEXT_1M)
            plugin(plugin)
            settingSources(SettingSource.USER, SettingSource.PROJECT)
            addDirs("/extra/dir1", "/extra/dir2")
            env {
                put("MY_VAR", "demo_value")
            }
            extraArgs {
                put("--custom-flag", "value")
            }
            agents {
                put("reviewer", codeReviewer)
            }
        }.use {
            println("  セッションオブジェクト作成成功")
        }
    } catch (e: Exception) {
        println("  ※ セッション作成: ${e.message}")
    }

    // 7. McpSSEServerConfig / McpHttpServerConfig
    println("■ McpSSEServerConfig / McpHttpServerConfig:")
    val sseServer = McpSSEServerConfig(url = "https://example.com/sse")
    println("  SSE: $sseServer")
    val httpServer = McpHttpServerConfig(url = "https://example.com/mcp")
    println("  HTTP: $httpServer")
    println()

    // 8. エラー型階層
    println("■ エラー型階層:")
    println("  ClaudeSDKException (基底)")
    println("    ├── CLIConnectionException - CLI接続エラー")
    println("    ├── CLINotFoundException   - CLI未検出 (cliPath)")
    println("    ├── ProcessException       - プロセスエラー (exitCode, stderr)")
    println("    ├── CLIJsonDecodeException - JSON解析エラー (rawText)")
    println("    └── MessageParseException  - メッセージ解析エラー (rawMessage)")
    println()

    // 9. SystemPrompt / SystemPromptPreset
    println("■ SystemPrompt / SystemPromptPreset:")
    println("  // 方法1: 直接文字列指定")
    println("  systemPrompt = \"あなたはKotlinの専門家です\"")
    println()
    println("  // 方法2: プリセット使用")
    val preset = SystemPromptPreset(append = "追加の指示をここに記述")
    println("  systemPromptPreset = $preset")
    println()

    // 10. ToolsPreset
    println("■ ToolsPreset:")
    val toolsPreset = ToolsPreset(preset = "claude_code")
    println("  $toolsPreset")
    println()
}
