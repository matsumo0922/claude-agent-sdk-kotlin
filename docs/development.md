# 開発ガイド

## プロジェクト構成

```
claude-agent-sdk-kotlin/
├── build.gradle.kts                    # ビルド設定
├── settings.gradle.kts                 # プロジェクト設定
├── gradle/                             # Gradle Wrapper
├── CLAUDE.md                           # AI エージェント向けプロジェクトコンテキスト
├── DesignDoc.md                        # SDK 設計ドキュメント
├── docs/                               # ドキュメント（本フォルダ）
└── src/
    ├── main/kotlin/com/anthropic/sdk/
    │   ├── ClaudeAgentSDK.kt           # トップレベル関数 (query, prompt, createSession, resumeSession)
    │   ├── ClaudeSDKClient.kt          # 双方向セッションクライアント
    │   ├── annotations/
    │   │   └── Description.kt          # @Description アノテーション
    │   ├── errors/
    │   │   └── Errors.kt              # 例外階層
    │   ├── internal/
    │   │   ├── InternalClient.kt       # Transport + QueryController のファサード
    │   │   ├── MessageParser.kt        # JSON → SDKMessage パーサー
    │   │   ├── QueryController.kt      # 双方向制御プロトコル
    │   │   └── transport/
    │   │       ├── Transport.kt        # Transport インターフェース
    │   │       └── SubprocessTransport.kt  # CLI サブプロセス実装
    │   ├── mcp/
    │   │   ├── McpServer.kt            # SdkMcpServer, createSdkMcpServer(), runMcpServer()
    │   │   └── JsonSchemaGenerator.kt  # JSON Schema 生成
    │   └── types/
    │       ├── Agents.kt              # AgentDefinition
    │       ├── ContentBlocks.kt       # ContentBlock sealed hierarchy
    │       ├── Hooks.kt               # Hook 型, HooksBuilder DSL
    │       ├── MCP.kt                 # McpServerConfig sealed hierarchy
    │       ├── Messages.kt            # SDKMessage sealed hierarchy
    │       ├── Options.kt             # ClaudeAgentOptions, SessionOptionsBuilder, enum 型
    │       └── Results.kt             # PromptResult
    └── test/kotlin/com/anthropic/sdk/
        ├── TypesTest.kt               # 型のシリアライズ/デシリアライズテスト (20 tests)
        ├── MessageParserTest.kt        # メッセージパーサーテスト (17 tests)
        ├── JsonSchemaGeneratorTest.kt  # JSON Schema 生成テスト (13 tests)
        ├── McpServerTest.kt           # MCP サーバーテスト (7 tests)
        ├── HooksTest.kt              # Hook システムテスト (9 tests)
        └── TransportTest.kt          # CLI コマンド構築テスト (20 tests)
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
./gradlew test --tests "com.anthropic.sdk.TypesTest"

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
| `TypesTest` | 20 | enum シリアライズ、ThinkingConfig、McpServerConfig、ContentBlock、パーミッション型 |
| `MessageParserTest` | 17 | System/Assistant/User/Result/StreamEvent メッセージのパース |
| `JsonSchemaGeneratorTest` | 13 | プリミティブ型、enum、List、Map、ネスト、nullable、sealed class、@Description |
| `McpServerTest` | 7 | サーバー初期化、ツール一覧、ツール実行、エラーハンドリング、JSON-RPC ディスパッチ |
| `HooksTest` | 9 | HookOutput ヘルパー、HooksBuilder、マッチャー、タイムアウト、DSL 統合 |
| `TransportTest` | 20 | CLI コマンド構築（全フラグ組み合わせ）、MCP 設定 JSON、環境変数 |

合計: **86 テスト**

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
| `kotlinx-coroutines-core` | 1.8.1 | コルーチン、Flow、Channel、Mutex |
| `kotlinx-serialization-json` | 1.7.3 | JSON シリアライズ/デシリアライズ |

### テスト

| ライブラリ | バージョン | 用途 |
|---|---|---|
| `kotlin-test` | (Kotlin バージョンに従う) | JUnit 5 統合、アサーション |
| `kotlinx-coroutines-test` | 1.8.1 | `runTest`, テストディスパッチャ |
| `mockk` | 1.13.13 | モック/スタブ |

### Kotlin & プラグイン

| 項目 | バージョン |
|---|---|
| Kotlin | 2.0.21 |
| kotlinx.serialization プラグイン | 2.0.21 |
| JVM ツールチェイン | 17 |

---

## コーディング規約

### 命名

- **クラス/インターフェース**: PascalCase (`ClaudeSDKClient`, `SDKMessage`)
- **関数/プロパティ**: camelCase (`sendPrompt`, `sessionId`)
- **定数**: SCREAMING_SNAKE (`SDK_VERSION`)
- **パッケージ**: 小文字ドット区切り (`com.anthropic.sdk.types`)

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

```
CLIConnectionException: Claude Code version X.Y.Z is below minimum 2.0.0
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
