# 開発ガイド

## プロジェクト構成

```
claude-agent-sdk-kotlin/
├── build.gradle.kts                    # ルートビルド設定
├── settings.gradle.kts                 # プロジェクト設定 (agent, demo モジュール)
├── gradle/
│   └── libs.versions.toml             # バージョンカタログ
├── CLAUDE.md                           # AI エージェント向けプロジェクトコンテキスト
├── docs/                               # ドキュメント（本フォルダ）
├── agent/                              # SDK 本体モジュール
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/me/matsumo/claude/agent/
│       │   ├── ClaudeAgentSDK.kt           # トップレベル関数 (query, prompt, createSession, resumeSession)
│       │   ├── ClaudeSDKClient.kt          # 双方向セッションクライアント
│       │   ├── annotations/
│       │   │   └── Description.kt          # @Description アノテーション
│       │   ├── errors/
│       │   │   └── Errors.kt              # 例外階層
│       │   ├── internal/
│       │   │   ├── InternalClient.kt       # Transport + QueryController のファサード
│       │   │   ├── MessageParser.kt        # JSON → SDKMessage パーサー
│       │   │   ├── QueryController.kt      # 双方向制御プロトコル
│       │   │   └── transport/
│       │   │       ├── Transport.kt        # Transport インターフェース
│       │   │       └── SubprocessTransport.kt  # CLI サブプロセス実装
│       │   ├── mcp/
│       │   │   ├── McpServer.kt            # SdkMcpServer, createSdkMcpServer(), runMcpServer()
│       │   │   └── JsonSchemaGenerator.kt  # JSON Schema 生成
│       │   └── types/
│       │       ├── Agents.kt              # AgentDefinition
│       │       ├── ContentBlocks.kt       # ContentBlock sealed hierarchy
│       │       ├── Hooks.kt               # Hook 型, HooksBuilder DSL
│       │       ├── MCP.kt                 # McpServerConfig sealed hierarchy
│       │       ├── Messages.kt            # SDKMessage sealed hierarchy
│       │       ├── Options.kt             # ClaudeAgentOptions, SessionOptionsBuilder, enum 型
│       │       ├── Results.kt             # PromptResult
│       │       ├── ApiStreamEvents.kt     # Anthropic API streaming event types
│       │       ├── ContentBlockBuilder.kt # Content block builder DSL
│       │       ├── SubAgentIdResolver.kt  # Sub-agent ID 解決 (hookToolUseId ↔ parentToolUseId)
│       │       └── SubAgentPaths.kt       # Sub-agent transcript path utilities
│       └── test/kotlin/me/matsumo/claude/agent/
│           ├── TypesTest.kt               # 型のシリアライズ/デシリアライズテスト (25 tests)
│           ├── MessageParserTest.kt        # メッセージパーサーテスト (18 tests)
│           ├── JsonSchemaGeneratorTest.kt  # JSON Schema 生成テスト (11 tests)
│           ├── McpServerTest.kt           # MCP サーバーテスト (8 tests)
│           ├── HooksTest.kt              # Hook システムテスト (11 tests)
│           ├── TransportTest.kt          # CLI コマンド構築テスト (23 tests)
│           ├── ApiStreamEventTest.kt      # API streaming event テスト (9 tests)
│           ├── ContentBlockBuilderTest.kt # Content block builder テスト (6 tests)
│           ├── SubAgentIdResolverTest.kt  # Sub-agent ID 解決テスト (6 tests)
│           └── SubAgentPathsTest.kt       # Sub-agent path テスト (3 tests)
└── demo/                               # デモアプリケーション (20+ demos)
    ├── build.gradle.kts
    └── src/main/kotlin/me/matsumo/claude/agent/demo/
        └── Main.kt                     # デモランナー
```

---

## ビルドとテスト

### 前提条件

- **JDK 17** 以上
- Gradle Wrapper 同梱のため、Gradle のグローバルインストールは不要

### ビルド

```bash
./gradlew build
```

### テスト実行

```bash
# 全テスト実行
./gradlew test

# 特定のテストクラスを実行
./gradlew test --tests "me.matsumo.claude.agent.TypesTest"

# テスト名でフィルタ
./gradlew test --tests "*JsonSchemaGenerator*"
```

### クリーンビルド

```bash
./gradlew clean build
```

---

## テスト

### テスト構成

すべてのテストは **単体テスト** です。CLI のサブプロセス起動は行わず、以下を検証しています:

| テストファイル | テスト数 | 検証内容 |
|---|---|---|
| `TypesTest` | 25 | enum シリアライズ、ThinkingConfig、McpServerConfig、ContentBlock、パーミッション型 |
| `MessageParserTest` | 18 | System/Assistant/User/Result/StreamEvent メッセージのパース |
| `JsonSchemaGeneratorTest` | 11 | プリミティブ型、enum、List、Map、ネスト、nullable、sealed class、@Description |
| `McpServerTest` | 8 | サーバー初期化、ツール一覧、ツール実行、エラーハンドリング、JSON-RPC ディスパッチ |
| `HooksTest` | 11 | HookOutput ヘルパー、HooksBuilder、マッチャー、タイムアウト、DSL 統合 |
| `TransportTest` | 23 | CLI コマンド構築（全フラグ組み合わせ）、MCP 設定 JSON、環境変数 |
| `ApiStreamEventTest` | 9 | API streaming event の JSON パース |
| `ContentBlockBuilderTest` | 6 | Content block DSL ビルダー |
| `SubAgentIdResolverTest` | 6 | hookToolUseId ↔ parentToolUseId の FIFO 解決 |
| `SubAgentPathsTest` | 3 | Sub-agent transcript path 構築 |

合計: **120 テスト**

### テストフレームワーク

- **JUnit 5** (`useJUnitPlatform()`)
- **kotlinx-coroutines-test** (`runTest` でコルーチンテスト)
- **MockK** (モック/スタブ)

### テストの書き方

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class MyFeatureTest {
    @Test
    fun `descriptive test name in backticks`() = runTest {
        // Given
        val input = ...

        // When
        val result = myFunction(input)

        // Then
        assertEquals(expected, result)
    }
}
```

---

## 依存関係

### 本体

| ライブラリ | バージョン | 用途 |
|---|---|---|
| `kotlinx-coroutines-core` | 1.10.1 | コルーチン、Flow、Channel、Mutex |
| `kotlinx-serialization-json` | 1.8.1 | JSON シリアライズ/デシリアライズ |

### テスト

| ライブラリ | バージョン | 用途 |
|---|---|---|
| `kotlin-test` | (Kotlin バージョンに従う) | JUnit 5 統合、アサーション |
| `kotlinx-coroutines-test` | 1.10.1 | `runTest`, テストディスパッチャ |
| `mockk` | 1.14.9 | モック/スタブ |

### Kotlin & プラグイン

依存バージョンは `gradle/libs.versions.toml` で一元管理されています。

| 項目 | バージョン |
|---|---|
| Kotlin | 2.3.0 |
| kotlinx.serialization プラグイン | 2.3.0 |
| JVM ツールチェイン | 17 |

---

## コーディング規約

### 命名

- **クラス/インターフェース**: PascalCase (`ClaudeSDKClient`, `SDKMessage`)
- **関数/プロパティ**: camelCase (`sendPrompt`, `sessionId`)
- **定数**: SCREAMING_SNAKE (`SDK_VERSION`)
- **パッケージ**: 小文字ドット区切り (`me.matsumo.claude.agent.types`)

### 可視性

- Public API: `public` を明示
- 内部実装: `internal` (`SubprocessTransport`, `InternalClient`, `QueryController`, `MessageParser`)
- テスト: `internal` クラスは同パッケージからアクセス可能

### シリアライズ

- `@Serializable` をデータクラスに付与
- ワイヤーフォーマットの名前は `@SerialName` で指定
- sealed interface にはカスタムシリアライザまたは `@SerialName` で型判別

### スレッドセーフティ

- 共有可変フィールドには `@Volatile`
- 排他制御が必要な箇所は `Mutex`
- ロックフリーのマップは `ConcurrentHashMap`
- バックプレッシャーによるデッドロックを防ぐため `Channel.UNLIMITED`

---

## アーキテクチャの拡張ポイント

### カスタム Transport

`Transport` インターフェースを実装することで、CLI サブプロセス以外のトランスポートを使用できます。

```kotlin
interface Transport {
    suspend fun connect()
    suspend fun write(data: String)
    fun readMessages(): Flow<String>
    suspend fun close()
    fun isReady(): Boolean
    suspend fun endInput()
}
```

例: テスト用のモック Transport、WebSocket ベースの Transport など。

### カスタム MCP ツール

`McpServerBuilder` の `tool<A, R>()` または `toolRaw()` で任意のカスタムツールを追加できます。データベースアクセス、API 呼び出し、ファイル操作など、任意のロジックをツールとして公開できます。

---

## トラブルシューティング

### CLI が見つからない

```
CLINotFoundException: Claude Code not found
```

→ Claude Code CLI がインストールされているか確認:
```bash
claude --version
```

→ カスタムパスを指定:
```kotlin
createSession {
    cliPath = "/path/to/claude"
}
```

### バージョンが古い

バージョンが `2.0.0` 未満の場合、SDK は stderr に以下の警告を出力します（例外はスローされません）:

```
Warning: Claude Code version X.Y.Z is unsupported in the Agent SDK.
Minimum required version is 2.0.0. Some features may not work correctly.
```

→ CLI をアップデート:
```bash
npm update -g @anthropic-ai/claude-code
```

### JSON パースエラー

```
CLIJsonDecodeException: ...
```

→ `maxBufferSize` を増やす:
```kotlin
createSession {
    maxBufferSize = 4 * 1024 * 1024  // 4MB
}
```

→ `stderr` コールバックで CLI のエラー出力を確認:
```kotlin
createSession {
    stderr = { line -> println("[STDERR] $line") }
}
```

### Not connected エラー

```
CLIConnectionException: Not connected. Call connect() first.
```

→ `send()` / `receive()` の前に `connect()` を呼ぶ:
```kotlin
session.connect()  // これを忘れずに
session.send("Hello")
```
