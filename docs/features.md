# 対応機能一覧

Claude Agent SDK for Kotlin が対応している機能と、Python 版にはあるが未対応の機能の一覧です。

## 対応済み機能

### コア機能

| 機能 | 説明 | 状態 |
|---|---|---|
| ワンショットクエリ | `query()` で単純な質問応答 | 対応済み |
| メタデータ付きクエリ | `prompt()` でコスト・トークン数も取得 | 対応済み |
| マルチターンセッション | `createSession()` で双方向対話 | 対応済み |
| セッション再開 | `resumeSession()` で過去の会話を再開 | 対応済み |
| セッションフォーク | `forkSession` で分岐した新セッション作成 | 対応済み |
| モデル選択 | `Model.SONNET`, `OPUS`, `HAIKU` または任意の文字列 | 対応済み |
| フォールバックモデル | `fallbackModel` で代替モデル指定 | 対応済み |
| ターン数制限 | `maxTurns` で最大ターン数を設定 | 対応済み |
| コスト制限 | `maxBudgetUsd` で最大コストを設定 | 対応済み |

### メッセージ・ストリーミング

| 機能 | 説明 | 状態 |
|---|---|---|
| メッセージ型 sealed hierarchy | `SystemMessage`, `AssistantMessage`, `UserMessage`, `ResultMessage`, `StreamEvent` | 対応済み |
| コンテンツブロック | `TextBlock`, `ThinkingBlock`, `ToolUseBlock`, `ToolResultBlock` | 対応済み |
| Flow ベースストリーミング | `receive()` / `receiveResponse()` で `Flow<SDKMessage>` を取得 | 対応済み |
| 部分メッセージ | `includePartialMessages = true` で `StreamEvent` を受信 | 対応済み |
| `textContent()` ヘルパー | `AssistantMessage` のテキストブロックを結合 | 対応済み |

### ツール・パーミッション

| 機能 | 説明 | 状態 |
|---|---|---|
| ツール許可リスト | `allowTools("Read", "Glob", ...)` | 対応済み |
| ツール禁止リスト | `disallowTools("Write", "Edit", ...)` | 対応済み |
| パーミッションモード | `DEFAULT`, `ACCEPT_EDITS`, `PLAN`, `BYPASS_PERMISSIONS` | 対応済み |
| 動的パーミッション変更 | `setPermissionMode()` でセッション中に変更 | 対応済み |
| `canUseTool` コールバック | ツール使用時にプログラマティックに許可/拒否 | 対応済み |
| パーミッション提案 | CLI からの `permission_suggestions` をパースしてコールバックに渡す | 対応済み |
| 入力書き換え | `PermissionResultAllow.updatedInput` でツール入力を変更 | 対応済み |
| パーミッションルール更新 | `PermissionResultAllow.updatedPermissions` でルールを変更 | 対応済み |

### Hook システム

| 機能 | 説明 | 状態 |
|---|---|---|
| PreToolUse | ツール実行前のフック | 対応済み |
| PostToolUse | ツール実行成功後のフック | 対応済み |
| PostToolUseFailure | ツール実行失敗後のフック | 対応済み |
| UserPromptSubmit | プロンプト送信時のフック | 対応済み |
| Stop | セッション停止時のフック | 対応済み |
| SubagentStop | サブエージェント停止時のフック | 対応済み |
| PreCompact | コンテキスト圧縮前のフック | 対応済み |
| Notification | 通知受信時のフック | 対応済み |
| SubagentStart | サブエージェント開始時のフック | 対応済み |
| PermissionRequest | パーミッション要求時のフック | 対応済み |
| HooksBuilder DSL | `hooks { preToolUse("Bash") { ... } }` | 対応済み |
| ツール名マッチャー | 正規表現パターンでフック対象を絞り込み | 対応済み |
| タイムアウト設定 | `timeout` でフック実行のタイムアウトを指定 | 対応済み |
| HookOutput ヘルパー | `allow()`, `deny()`, `proceed()`, `modify()` | 対応済み |

詳細は [Hook システム](hooks.md) を参照してください。

### MCP (Model Context Protocol)

| 機能 | 説明 | 状態 |
|---|---|---|
| Stdio サーバー | `McpStdioServerConfig` で外部プロセスの MCP サーバーを指定 | 対応済み |
| SSE サーバー | `McpSSEServerConfig` で SSE ベースのサーバーを指定 | 対応済み |
| HTTP サーバー | `McpHttpServerConfig` で HTTP ベースのサーバーを指定 | 対応済み |
| SDK サーバー（インプロセス） | `McpSdkServerConfig` でインプロセスのカスタムツールを提供 | 対応済み |
| ツール DSL | `tool<Args, Result>("name", "desc") { ... }` | 対応済み |
| Raw ツール | `toolRaw("name", "desc", schema) { ... }` | 対応済み |
| JSON Schema 自動生成 | `@Serializable` クラスから JSON Schema を生成 | 対応済み |
| `@Description` アノテーション | スキーマに説明文を追加 | 対応済み |
| スタンドアロン実行 | `runMcpServer()` で独立した MCP サーバーとして起動 | 対応済み |

詳細は [MCP カスタムツール](mcp-tools.md) を参照してください。

### 構造化出力

| 機能 | 説明 | 状態 |
|---|---|---|
| JSON Schema 指定 | `outputFormat(OutputFormat(...))` で出力スキーマを指定 | 対応済み |
| 構造化出力の取得 | `ResultMessage.structuredOutput` で取得 | 対応済み |
| 型安全デシリアライズ | `PromptResult.structuredOutput<T>()` で `@Serializable` 型に変換 | 対応済み |

### Extended Thinking

| 機能 | 説明 | 状態 |
|---|---|---|
| Adaptive モード | `thinking = ThinkingConfig.Adaptive` | 対応済み |
| Budget 指定 | `thinking = ThinkingConfig.Enabled(budgetTokens = 8192)` | 対応済み |
| 無効化 | `thinking = ThinkingConfig.Disabled` | 対応済み |
| Effort レベル | `effort = Effort.LOW / MEDIUM / HIGH / MAX` | 対応済み |
| ThinkingBlock 受信 | `AssistantMessage.content` に `ThinkingBlock` が含まれる | 対応済み |

### セッション制御

| 機能 | 説明 | 状態 |
|---|---|---|
| インタラプト | `interrupt()` で実行を中断 | 対応済み |
| モデル変更 | `setModel()` でセッション中にモデルを切り替え | 対応済み |
| パーミッションモード変更 | `setPermissionMode()` で動的に変更 | 対応済み |
| MCP ステータス取得 | `getMcpStatus()` で接続状態を確認 | 対応済み |
| サーバー情報取得 | `getServerInfo()` で初期化情報を取得 | 対応済み |
| ファイルチェックポイント | `enableFileCheckpointing` + `rewindFiles()` | 対応済み |

### その他

| 機能 | 説明 | 状態 |
|---|---|---|
| サンドボックス | `SandboxSettings` で bash コマンドの分離 | 対応済み |
| プラグイン | `SdkPluginConfig` でローカルプラグインを設定 | 対応済み |
| カスタムエージェント | `AgentDefinition` でエージェントを定義 | 対応済み |
| 設定ソース | `SettingSource.USER / PROJECT / LOCAL` | 対応済み |
| ベータ機能 | `SdkBeta.CONTEXT_1M` (1M コンテキスト) | 対応済み |
| DSL ビルダー | `SessionOptionsBuilder` による型安全な設定 | 対応済み |
| リソース管理 | `Closeable` + `use {}` による自動解放 | 対応済み |

---

## 設計上の差異（非対応ではなく、別のアプローチを採用）

| 項目 | Python 版 | Kotlin 版 | 理由 |
|---|---|---|---|
| `AsyncHookJSONOutput` | 専用型あり | Hook コールバックでは `HookJSONOutput` のみ使用（`AsyncHookJSONOutput` クラスは存在するがコールバックの戻り値には使わない） | Kotlin の suspend 関数が非同期を自然に処理するため、分離が不要 |
| `@tool` デコレータ | Python デコレータ | `tool<A, R>()` ジェネリック関数 | Kotlin にはデコレータがないため、ジェネリクスと reified 型パラメータで代替 |
| `async with` コンテキストマネージャ | 自動接続 | `connect()` を明示呼び出し | Kotlin の `Closeable.use {}` はクリーンアップのみ担当する慣習のため |
| `AsyncIterator` | Python 非同期イテレータ | `Flow<SDKMessage>` | Kotlin のリアクティブストリーム標準 |
| snake_case | Python 標準 | camelCase | 各言語の命名規則に従う |

Python 版との差異の詳細は [Python 版との差異](python-differences.md) を参照してください。
