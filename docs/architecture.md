# アーキテクチャ

## レイヤー構成

SDK は以下の4層で構成されています。上位層が下位層に依存します。

```
┌──────────────────────────────────────────────────────────┐
│  User Code                                               │
│  query(), prompt(), createSession(), resumeSession()     │
├──────────────────────────────────────────────────────────┤
│  Public API Layer                                        │
│  ClaudeSDKClient, ClaudeAgentSDK (top-level functions)   │
├──────────────────────────────────────────────────────────┤
│  Internal Layer                                          │
│  InternalClient, QueryController, MessageParser          │
├──────────────────────────────────────────────────────────┤
│  Transport Layer                                         │
│  Transport (interface), SubprocessTransport              │
├──────────────────────────────────────────────────────────┤
│  Claude Code CLI (外部プロセス)                            │
│  stdin (JSON Lines) / stdout (JSON Lines)                │
└──────────────────────────────────────────────────────────┘
```

### 各レイヤーの責務

| レイヤー | 主なクラス | 責務 |
|---|---|---|
| Public API | `ClaudeSDKClient`, top-level functions | ユーザー向け API。セッション管理、send/receive |
| Internal | `InternalClient` | Transport と QueryController の生成と接続 |
| Internal | `QueryController` | 双方向制御プロトコルの管理、Hook/MCP/Permission のディスパッチ |
| Internal | `MessageParser` | CLI からの JSON Lines を `SDKMessage` に変換 |
| Transport | `SubprocessTransport` | CLI サブプロセスの起動、stdin/stdout の読み書き |

---

## 通信プロトコル

SDK と Claude Code CLI は **JSON Lines** (改行区切り JSON) で通信します。

### 起動シーケンス

```
SDK                                      CLI
 │                                        │
 │── spawn subprocess ──────────────────→ │
 │                                        │
 │── initialize request (stdin) ────────→ │
 │   { type, tools, hooks, ... }          │
 │                                        │
 │←── system message (stdout) ──────────  │
 │   { type: "system", subtype: "init" }  │
 │                                        │
 │── prompt (stdin) ────────────────────→ │
 │   { type: "user", message: {...} }     │
 │                                        │
 │←── assistant messages (stdout) ──────  │
 │←── control requests (stdout) ────────  │
 │── control responses (stdin) ─────────→ │
 │                                        │
 │←── result message (stdout) ──────────  │
 │   { type: "result", subtype: "success"}│
```

### メッセージの方向

| 方向 | 形式 | 内容 |
|---|---|---|
| SDK → CLI (stdin) | JSON Lines | 初期化リクエスト、ユーザープロンプト、制御レスポンス |
| CLI → SDK (stdout) | JSON Lines | システムメッセージ、アシスタントメッセージ、制御リクエスト、結果 |

### 制御プロトコル

SDK と CLI の間には、通常のメッセージとは別に **制御プロトコル** が存在します。これにより Hook コールバック、ツールパーミッション確認、MCP メッセージルーティングが実現されます。

**制御リクエスト** (CLI → SDK):
```json
{
  "type": "control_response",
  "response": {
    "subtype": "control_request",
    "request_id": "req_abc123",
    "request": {
      "subtype": "can_use_tool",
      "tool_name": "Bash",
      "input": { "command": "ls" }
    }
  }
}
```

**制御レスポンス** (SDK → CLI):
```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "req_abc123",
    "response": {
      "behavior": "allow"
    }
  }
}
```

制御リクエストのサブタイプ:

| subtype | 説明 |
|---|---|
| `can_use_tool` | ツール使用の許可確認（`canUseTool` コールバック） |
| `hook_callback` | Hook の実行要求 |
| `mcp_message` | インプロセス MCP サーバーへのメッセージルーティング |

---

## データフロー

### ワンショットクエリ (`query()`)

```
query("prompt")
  → InternalClient.connect()
    → SubprocessTransport.connect()
      → Process 起動 + CLI バージョン確認
    → QueryController.initialize()
      → initialize リクエスト送信 + SystemMessage 待受
  → InternalClient.sendPromptAndClose()
    → stdin にプロンプト書き込み + stdin 閉じる
  → Flow<SDKMessage> を collect
    → MessageParser で JSON → SDKMessage 変換
    → 制御リクエストは QueryController が処理
    → ResultMessage で完了
  → InternalClient.close()
    → Process 破棄
```

### マルチターンセッション (`createSession()`)

```
createSession { ... }
  → ClaudeSDKClient 生成

session.connect()
  → InternalClient.connect()
    → SubprocessTransport.connect()
    → QueryController.initialize()

session.send("prompt")
  → QueryController.sendPrompt()
    → stdin に user message 書き込み

session.receiveResponse()
  → Flow<SDKMessage> (ResultMessage まで)
    → 途中で制御リクエスト処理
    → ResultMessage で Flow 完了

session.send("next prompt")  // 会話継続
  → ... (同上)

session.close()
  → InternalClient.close()
    → Transport.close()
      → Process.destroy()
```

---

## 主要コンポーネントの詳細

### SubprocessTransport

CLI サブプロセスのライフサイクルを管理します。

- **CLI 検出**: `cliPath` 指定、`which claude`、標準パス検索の順で探索
- **バージョン検証**: `claude --version` で 2.0.0 以上を確認
- **コマンド構築**: `ClaudeAgentOptions` → CLI フラグの変換
- **読み取り**: stdout からの JSON Lines を `Flow<String>` で提供
- **書き込み**: `Mutex` による排他的 stdin 書き込み
- **バッファサイズ**: デフォルト 1MB、`maxBufferSize` で設定可能
- **環境変数**: `CLAUDE_CODE_ENTRYPOINT=sdk-kt`, `CLAUDE_AGENT_SDK_VERSION` を自動設定

### QueryController

双方向制御プロトコルのハブです。

- **メッセージ分離**: `MessageParser.ParseResult` の `Message` と `Control` を振り分け
- **リクエスト ID 管理**: `ConcurrentHashMap<String, CompletableDeferred>` でリクエスト・レスポンスをマッチ
- **Hook ディスパッチ**: コールバック ID → Hook 関数のマッピング
- **MCP ルーティング**: サーバー名 → `SdkMcpServer.handleRequest()`
- **チャネル**: `Channel.UNLIMITED` でバックプレッシャーによるデッドロックを防止
- **初期化**: `firstResultDeferred` で最初の ResultMessage を検知し、双方向入力フェーズの終了を通知

### MessageParser

CLI stdout の JSON を型安全な `SDKMessage` に変換します。

- **型判定**: `type` フィールドで `system`/`assistant`/`user`/`result`/`stream_event` を識別
- **Content Block**: `TextBlock`, `ThinkingBlock`, `ToolUseBlock`, `ToolResultBlock` を構築
- **エラーハンドリング**: 不明な型やパース失敗は `MessageParseException` で報告
- **制御メッセージ**: `ParseResult.Control` として別途返却

### InternalClient

Transport と QueryController のファサードです。

- **SDK MCP サーバー抽出**: `McpSdkServerConfig` をオプションから抽出し、QueryController に渡す
- **`canUseTool` 自動設定**: コールバック指定時に `permissionPromptToolName = "stdio"` を自動設定
- **ライフサイクル管理**: `connect()` → 使用 → `close()` の一貫した管理

---

## スレッドセーフティ

以下の設計により、マルチスレッド環境での安全性を確保しています。

| 対策 | 適用箇所 |
|---|---|
| `@Volatile` | `SubprocessTransport.process/stdinWriter/stdoutReader`, `QueryController.readJob/scope/initialized`, `InternalClient.transport/queryController`, `ClaudeSDKClient.connected/sessionId` |
| `Mutex` | `SubprocessTransport` の stdin 書き込み |
| `ConcurrentHashMap` | `QueryController.pendingResponses` |
| `Channel.UNLIMITED` | メッセージチャネル（バックプレッシャーによるデッドロック防止） |
| `CompletableDeferred` | 初期化完了・最初の ResultMessage の待機 |
| `SupervisorJob` | `ClaudeSDKClient` のスコープ（子コルーチンの失敗が他に伝播しない） |
