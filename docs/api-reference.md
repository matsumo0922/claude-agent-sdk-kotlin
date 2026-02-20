# API リファレンス

## トップレベル関数

### `query()`

ワンショットのプロンプトを送り、応答テキストを返します。

```kotlin
public suspend fun query(
    prompt: String,
    configure: SessionOptionsBuilder.() -> Unit = {},
): String
```

| パラメータ | 型 | 説明 |
|---|---|---|
| `prompt` | `String` | 送信するテキストプロンプト |
| `configure` | `SessionOptionsBuilder.() -> Unit` | セッション設定 DSL（省略可） |
| **戻り値** | `String` | 応答テキスト。結果がない場合は空文字列 |

### `prompt()`

プロンプトを送り、メタデータ付きの `PromptResult` を返します。

```kotlin
public suspend fun prompt(
    prompt: String,
    configure: SessionOptionsBuilder.() -> Unit = {},
): PromptResult
```

| パラメータ | 型 | 説明 |
|---|---|---|
| `prompt` | `String` | 送信するテキストプロンプト |
| `configure` | `SessionOptionsBuilder.() -> Unit` | セッション設定 DSL（省略可） |
| **戻り値** | `PromptResult` | 応答テキスト、コスト、トークン数などのメタデータ |

### `createSession()`

対話的セッションを作成します。

```kotlin
public fun createSession(
    configure: SessionOptionsBuilder.() -> Unit = {},
): ClaudeSDKClient
```

返却される `ClaudeSDKClient` は `connect()` を呼んでから使用してください。

### `resumeSession()`

以前のセッションを再開します。

```kotlin
public fun resumeSession(
    sessionId: String,
    configure: SessionOptionsBuilder.() -> Unit = {},
): ClaudeSDKClient
```

---

## ClaudeSDKClient

双方向の対話セッションを管理するクライアントです。`Closeable` を実装しています。

### プロパティ

| プロパティ | 型 | 説明 |
|---|---|---|
| `sessionId` | `String?` | セッション ID。初回の `SystemMessage`（subtype=init）受信後に設定される |

### メソッド

| メソッド | シグネチャ | 説明 |
|---|---|---|
| `connect()` | `suspend fun connect()` | CLI に接続し、制御プロトコルを初期化する |
| `send()` | `suspend fun send(prompt: String)` | プロンプトを送信する |
| `receive()` | `fun receive(): Flow<SDKMessage>` | 現在のターンのメッセージ Flow を返す |
| `receiveResponse()` | `fun receiveResponse(): Flow<SDKMessage>` | 最初の `ResultMessage` まで（含む）の Flow を返す |
| `interrupt()` | `suspend fun interrupt()` | CLI にインタラプトシグナルを送る |
| `setPermissionMode()` | `suspend fun setPermissionMode(mode: PermissionMode)` | セッション中にパーミッションモードを変更する |
| `setModel()` | `suspend fun setModel(model: String?)` | セッション中にモデルを変更する。`null` でデフォルトにリセット |
| `getServerInfo()` | `fun getServerInfo(): JsonObject?` | CLI 初期化ハンドシェイクのサーバー情報を返す |
| `rewindFiles()` | `suspend fun rewindFiles(checkpointId: CheckpointId)` | 指定チェックポイントのファイル状態に巻き戻す |
| `getMcpStatus()` | `suspend fun getMcpStatus(): JsonObject` | MCP サーバーの接続状態を取得する |
| `close()` | `fun close()` | リソースを解放する（`Closeable` 実装） |
| `disconnect()` | `suspend fun disconnect()` | suspend 版の close。コルーチンコンテキストから呼ぶ場合に推奨 |

---

## メッセージ型

すべてのメッセージは `SDKMessage` sealed interface を実装しています。

### SDKMessage

```kotlin
public sealed interface SDKMessage {
    val sessionId: String
}
```

### SystemMessage

セッション初期化やエラーなどのシステムイベント。

```kotlin
public data class SystemMessage(
    override val sessionId: String,
    val subtype: String,       // "init", "error" など
    val data: JsonObject,      // 生の JSON データ
) : SDKMessage {
    val isInit: Boolean        // subtype == "init" のとき true
}
```

### AssistantMessage

Claude からの応答。コンテンツブロックのリストを含みます。

```kotlin
public data class AssistantMessage(
    override val sessionId: String,
    val content: List<ContentBlock>,
    val model: String,
    val parentToolUseId: String?,       // サブターンの場合の親ツール ID
    val error: AssistantMessageError?,  // エラーがある場合
) : SDKMessage {
    public fun textContent(): String    // TextBlock を結合したテキスト
}
```

`AssistantMessageError` enum:
- `AUTHENTICATION_FAILED`, `BILLING_ERROR`, `RATE_LIMIT`, `INVALID_REQUEST`, `SERVER_ERROR`, `UNKNOWN`

### UserMessage

ツール結果などのユーザーメッセージ。

```kotlin
public data class UserMessage(
    override val sessionId: String,
    val content: JsonElement?,
    val uuid: String?,                  // チェックポイント ID
    val parentToolUseId: String?,
    val toolUseResult: JsonObject?,
) : SDKMessage
```

### ResultMessage

ターンの完了を示すメッセージ。コスト・トークン情報を含みます。

```kotlin
public data class ResultMessage(
    override val sessionId: String,
    val subtype: String,             // "success", "error", "max_turns", "interrupted"
    val durationMs: Int,
    val durationApiMs: Int,
    val isError: Boolean,
    val numTurns: Int,
    val totalCostUsd: Double?,
    val usage: JsonObject?,          // { "input_tokens": N, "output_tokens": N }
    val result: String?,             // 応答テキスト
    val structuredOutput: JsonElement?,  // 構造化出力（スキーマ指定時）
) : SDKMessage
```

### StreamEvent

ストリーミング中の部分的メッセージ。`includePartialMessages = true` のときのみ発行されます。

```kotlin
public data class StreamEvent(
    override val sessionId: String,
    val uuid: String,
    val event: JsonObject,           // 生の Anthropic API ストリームイベント
    val parentToolUseId: String?,
) : SDKMessage
```

---

## コンテンツブロック

`AssistantMessage.content` に含まれるブロック型です。

| 型 | フィールド | 説明 |
|---|---|---|
| `TextBlock` | `text: String` | テキスト応答 |
| `ThinkingBlock` | `thinking: String`, `signature: String` | Extended Thinking の出力 |
| `ToolUseBlock` | `id: String`, `name: String`, `input: JsonObject` | ツール呼び出しリクエスト |
| `ToolResultBlock` | `toolUseId: String`, `content: JsonElement?`, `isError: Boolean?` | ツール実行結果 |

---

## セッション設定

### ClaudeAgentOptions

全設定を保持するデータクラスです。通常は `SessionOptionsBuilder` DSL 経由で構築します。

| フィールド | 型 | デフォルト | 説明 |
|---|---|---|---|
| `model` | `String?` | `null` | モデル ID |
| `fallbackModel` | `String?` | `null` | フォールバックモデル |
| `maxTurns` | `Int?` | `null` | 最大ターン数 |
| `maxBudgetUsd` | `Double?` | `null` | 最大予算（USD） |
| `allowedTools` | `List<String>` | `[]` | 許可ツール名のリスト |
| `disallowedTools` | `List<String>` | `[]` | 禁止ツール名のリスト |
| `permissionMode` | `PermissionMode?` | `null` | パーミッションモード |
| `systemPrompt` | `Any?` | `null` | システムプロンプト（文字列または `SystemPromptPreset`） |
| `mcpServers` | `Map<String, McpServerConfig>` | `{}` | MCP サーバー設定 |
| `continueConversation` | `Boolean` | `false` | 前回の会話を続行するか |
| `resume` | `String?` | `null` | 再開するセッション ID |
| `forkSession` | `Boolean` | `false` | 再開時に新しいセッション ID を作成するか |
| `betas` | `List<SdkBeta>` | `[]` | ベータ機能フラグ |
| `cwd` | `String?` | `null` | CLI の作業ディレクトリ |
| `cliPath` | `String?` | `null` | CLI バイナリのパス |
| `settings` | `String?` | `null` | 設定ファイルのパス |
| `addDirs` | `List<String>` | `[]` | 追加ディレクトリ |
| `env` | `Map<String, String>` | `{}` | 環境変数 |
| `extraArgs` | `Map<String, String?>` | `{}` | 追加 CLI 引数 |
| `maxBufferSize` | `Int?` | `null` | 最大バッファサイズ（バイト） |
| `stderr` | `((String) -> Unit)?` | `null` | stderr コールバック |
| `tools` | `Any?` | `null` | ツールセット設定（ツール名リストまたは `ToolsPreset`） |
| `canUseTool` | `suspend (String, Map<String, Any?>, ToolPermissionContext) -> PermissionResult` | `null` | ツールパーミッションコールバック |
| `permissionPromptToolName` | `String?` | `null` | カスタムパーミッションプロンプトツール名 |
| `hooks` | `Map<HookEvent, List<HookMatcher>>?` | `null` | Hook 設定 |
| `user` | `String?` | `null` | ユーザー識別子 |
| `includePartialMessages` | `Boolean` | `false` | 部分メッセージを含めるか |
| `agents` | `Map<String, AgentDefinition>?` | `null` | カスタムエージェント定義 |
| `settingSources` | `List<SettingSource>?` | `null` | 設定ソース |
| `sandbox` | `SandboxSettings?` | `null` | サンドボックス設定 |
| `plugins` | `List<SdkPluginConfig>` | `[]` | プラグイン設定 |
| `thinking` | `ThinkingConfig?` | `null` | Extended Thinking 設定 |
| `effort` | `Effort?` | `null` | 思考の深さ |
| `outputFormat` | `OutputFormat?` | `null` | 構造化出力フォーマット |
| `enableFileCheckpointing` | `Boolean` | `false` | ファイルチェックポイントの有効化 |

### SessionOptionsBuilder DSL メソッド

| メソッド | 説明 |
|---|---|
| `allowTools(vararg tools)` | ツールを許可リストに追加 |
| `disallowTools(vararg tools)` | ツールを禁止リストに追加 |
| `bypassPermissions()` | 全パーミッションをバイパス |
| `mcpServers { }` | MCP サーバーを設定 |
| `env { }` | 環境変数を設定 |
| `extraArgs { }` | 追加 CLI 引数を設定 |
| `addDirs(vararg dirs)` | 追加ディレクトリを指定 |
| `betas(vararg beta)` | ベータ機能を有効化 |
| `plugin(config)` | プラグインを追加 |
| `hooks { }` | Hook を設定（[Hook システム](hooks.md)参照） |
| `outputFormat(format)` | 構造化出力フォーマットを設定 |
| `canUseTool(callback)` | ツールパーミッションコールバックを設定 |
| `agents { }` | カスタムエージェントを定義 |
| `settingSources(vararg sources)` | 設定ソースを指定 |

---

## Enum 型

### Model

```kotlin
enum class Model(val modelId: String) {
    SONNET("sonnet"), OPUS("opus"), HAIKU("haiku")
}
```

### PermissionMode

```kotlin
enum class PermissionMode {
    DEFAULT, ACCEPT_EDITS, PLAN, BYPASS_PERMISSIONS
}
```

### Effort

```kotlin
enum class Effort {
    LOW, MEDIUM, HIGH, MAX
}
```

### ThinkingConfig

```kotlin
sealed interface ThinkingConfig {
    data object Adaptive : ThinkingConfig
    data class Enabled(val budgetTokens: Int) : ThinkingConfig
    data object Disabled : ThinkingConfig
}
```

---

## 結果型

### PromptResult

```kotlin
public data class PromptResult(
    val result: String?,
    val sessionId: String,
    val totalCostUsd: Double?,
    val inputTokens: Long,
    val outputTokens: Long,
    val numTurns: Int,
    val durationMs: Int,
    val isError: Boolean,
    val subtype: String,
    val rawStructuredOutput: JsonElement?,
)
```

| メソッド | 説明 |
|---|---|
| `structuredOutput<T>()` | `rawStructuredOutput` を指定した `@Serializable` 型にデシリアライズ |

### CheckpointId

```kotlin
@JvmInline
public value class CheckpointId(public val value: String)
```

ファイルチェックポイントの ID をラップする value class です。

---

## 例外型

```
ClaudeSDKException (base)
├── CLIConnectionException
│   └── CLINotFoundException
├── ProcessException
├── CLIJsonDecodeException
└── MessageParseException
```

| 例外 | 説明 |
|---|---|
| `ClaudeSDKException` | SDK の基底例外 |
| `CLIConnectionException` | CLI への接続に失敗 |
| `CLINotFoundException` | CLI バイナリが見つからない。`cliPath` プロパティあり |
| `ProcessException` | CLI プロセスがエラー終了。`exitCode`, `stderr` プロパティあり |
| `CLIJsonDecodeException` | CLI からの JSON パース失敗。`rawText` プロパティあり |
| `MessageParseException` | メッセージのパース失敗。`rawMessage` プロパティあり |
