# Python 版との差異

Claude Agent SDK for Kotlin は Python 版を参考に設計されていますが、Kotlin のイディオムに合わせた設計変更を行っています。本ドキュメントではその差異をまとめます。

## API 設計の違い

### セッション管理

| 項目 | Python | Kotlin |
|---|---|---|
| ワンショット | `async for msg in query(...)` | `query(prompt)` / `prompt(prompt)` |
| セッション作成 | `ClaudeSDKClient(options)` | `createSession { ... }` |
| 接続 | `async with client:` で自動接続 | `session.connect()` を明示呼び出し |
| リソース管理 | `async with` コンテキストマネージャ | `Closeable.use {}` |
| メッセージ受信 | `AsyncIterator[Message]` | `Flow<SDKMessage>` |

**Python:**
```python
async with ClaudeSDKClient(options) as client:
    await client.query("Hello")
    async for msg in client.receive_response():
        ...
```

**Kotlin:**
```kotlin
createSession { ... }.use { session ->
    session.connect()
    session.send("Hello")
    session.receiveResponse().collect { msg -> ... }
}
```

Kotlin 版では `connect()` が明示的です。これは Kotlin の `Closeable` が Java の `AutoCloseable` に対応し、リソースの解放（`close()`）のみを保証するインターフェースであるためです。

### ワンショットクエリ

Python 版の `query()` は `AsyncIterator[Message]` を返し、メッセージを逐次処理する設計です。Kotlin 版は2つに分かれています:

- `query()`: 応答テキストだけを返す最もシンプルな API
- `prompt()`: メタデータ（コスト、トークン数など）付きの `PromptResult` を返す

**Python:**
```python
async for message in query(prompt="Hello", options=options):
    if isinstance(message, ResultMessage):
        print(message.result)
```

**Kotlin:**
```kotlin
// シンプル
val text = query("Hello")

// メタデータ付き
val result = prompt("Hello") { model = Model.SONNET }
println("${result.result} (cost: ${result.totalCostUsd})")
```

---

## 型システムの違い

### メッセージ型

| Python | Kotlin | 備考 |
|---|---|---|
| `Message` (Union type) | `SDKMessage` (sealed interface) | Kotlin は sealed で網羅性を保証 |
| `isinstance()` チェック | `when (msg) { is ... }` | コンパイラが網羅性を検証 |

**Python:**
```python
if isinstance(msg, AssistantMessage):
    print(msg.content[0].text)
elif isinstance(msg, ResultMessage):
    print(msg.result)
```

**Kotlin:**
```kotlin
when (msg) {
    is AssistantMessage -> print(msg.textContent())
    is ResultMessage -> println(msg.result)
    is SystemMessage -> { /* ... */ }
    is UserMessage -> { /* ... */ }
    is StreamEvent -> { /* ... */ }
}
```

### コンテンツブロック

同様に `ContentBlock` も sealed interface です。

**Python:**
```python
for block in msg.content:
    if isinstance(block, TextBlock):
        print(block.text)
    elif isinstance(block, ToolUseBlock):
        print(f"Tool: {block.name}")
```

**Kotlin:**
```kotlin
for (block in msg.content) {
    when (block) {
        is TextBlock -> print(block.text)
        is ToolUseBlock -> println("Tool: ${block.name}")
        is ThinkingBlock -> { /* ... */ }
        is ToolResultBlock -> { /* ... */ }
    }
}
```

### オプション設定

Python 版はデータクラスのコンストラクタで設定します。Kotlin 版は DSL ビルダーパターンを採用しています。

**Python:**
```python
options = ClaudeAgentOptions(
    model="sonnet",
    max_turns=5,
    allowed_tools=["Read", "Glob"],
    permission_mode="bypassPermissions",
    mcp_servers={"tools": server},
)
```

**Kotlin:**
```kotlin
createSession {
    model = Model.SONNET
    maxTurns = 5
    allowTools("Read", "Glob")
    bypassPermissions()
    mcpServers {
        put("tools", McpSdkServerConfig(server))
    }
}
```

---

## 非同期モデルの違い

| Python | Kotlin |
|---|---|
| `anyio` (asyncio/trio バックエンド) | kotlinx.coroutines |
| `async def` / `await` | `suspend fun` |
| `AsyncIterator` | `Flow` |
| `async with` | `use {}` + `connect()` |
| `TaskGroup` | `CoroutineScope` + `SupervisorJob` |

Kotlin のコルーチンは構造化並行性（Structured Concurrency）を前提としているため、`CoroutineScope` のキャンセルが子コルーチンに自動伝播します。

---

## MCP カスタムツールの違い

### ツール定義

**Python** はデコレータ方式:
```python
@tool("greet", "挨拶する", {"name": str})
async def greet(args):
    return {"content": [{"type": "text", "text": f"Hello, {args['name']}!"}]}
```

**Kotlin** はジェネリクス + reified 型パラメータ方式:
```kotlin
@Serializable
data class GreetArgs(val name: String)

@Serializable
data class GreetResult(val greeting: String)

tool<GreetArgs, GreetResult>("greet", "挨拶する") { args ->
    GreetResult(greeting = "Hello, ${args.name}!")
}
```

主な違い:

| 項目 | Python | Kotlin |
|---|---|---|
| 入力型定義 | `dict` マッピングまたは TypedDict | `@Serializable data class` |
| スキーマ生成 | 簡易型変換 | `SerialDescriptor` → JSON Schema |
| 型安全性 | 実行時チェック | コンパイル時チェック |
| 戻り値 | MCP content 配列を手動構築 | `@Serializable` 型を自動シリアライズ |
| 説明文 | Python docstring | `@Description` アノテーション |

### JSON Schema 生成

Python 版は簡易的な型マッピング（`str` → `string` など）を使用します。Kotlin 版は `kotlinx.serialization` の `SerialDescriptor` を再帰的に解析し、ネスト型、sealed class の `oneOf`、`@Description` アノテーションなどに対応した完全な JSON Schema を生成します。

---

## Hook システムの違い

### AsyncHookJSONOutput

Python 版には `AsyncHookJSONOutput` と `SyncHookJSONOutput` の2つの出力型があります:

```python
# Python - 非同期フック出力
AsyncHookJSONOutput(async_=True, asyncTimeout=30000)

# Python - 同期フック出力
SyncHookJSONOutput(continue_=True, reason="allowed")
```

Kotlin 版では Hook コールバックの戻り値は `HookJSONOutput` のみを使用します。`AsyncHookJSONOutput` クラスはシリアライズ型として定義されていますが、`HookCallback` の戻り値としては使用しません。Kotlin の `suspend` 関数は非同期実行を自然に表現でき、Python のように `async_` フラグで同期/非同期を分ける必要がないためです。

### フィールド名の変換

Python 版は Python の予約語との衝突を避けるため、一部フィールドにアンダースコアを付けています:

| Python | Kotlin | CLI（ワイヤーフォーマット） |
|---|---|---|
| `async_` | N/A（統合） | `async` |
| `continue_` | `shouldContinue` | `continue` |

Python SDK は `_convert_hook_output_for_cli()` で変換を行います。Kotlin SDK は `kotlinx.serialization` の `@SerialName` で直接ワイヤーフォーマットにマッピングしています。

### DSL ビルダー

Python 版は辞書ベースで Hook を設定しますが、Kotlin 版は型安全な DSL を提供します:

**Python:**
```python
options = ClaudeAgentOptions(
    hooks={
        "PreToolUse": [
            HookMatcher(matcher="Bash", hooks=[my_hook_fn], timeout=5.0)
        ]
    }
)
```

**Kotlin:**
```kotlin
createSession {
    hooks {
        preToolUse("Bash", timeout = 5.0) { input, toolUseId, context ->
            HookOutput.allow()
        }
    }
}
```

---

## 命名規則の違い

| Python | Kotlin | 例 |
|---|---|---|
| snake_case | camelCase | `max_turns` → `maxTurns` |
| SCREAMING_SNAKE | SCREAMING_SNAKE | `BYPASS_PERMISSIONS` → `BYPASS_PERMISSIONS` |
| dataclass | data class | `@dataclass class` → `data class` |
| Union type | sealed interface | `Message = Union[...]` → `sealed interface SDKMessage` |
| `TypedDict` | `@Serializable data class` | 構造体定義 |
| `__init__.py` | パッケージ構成 | モジュール初期化 |

### JSON シリアライズ名

ワイヤーフォーマット（CLI との通信）では同じ名前を使用しています:

```json
{
  "session_id": "...",
  "total_cost_usd": 0.05,
  "num_turns": 3
}
```

Kotlin 側では `@SerialName` で対応:
```kotlin
@SerialName("session_id") val sessionId: String
@SerialName("total_cost_usd") val totalCostUsd: Double
```

---

## エラーハンドリングの違い

| Python | Kotlin |
|---|---|
| `ClaudeSDKError` (base) | `ClaudeSDKException` (base) |
| `CLIConnectionError` | `CLIConnectionException` |
| `CLINotFoundError` | `CLINotFoundException` |
| `ProcessError` | `ProcessException` |
| `CLIJSONDecodeError` | `CLIJsonDecodeException` |
| N/A | `MessageParseException` |

Python は `Error` サフィックス、Kotlin は `Exception` サフィックスを使用しています（各言語の慣例に従う）。

Kotlin 版には `MessageParseException` が追加されており、メッセージのパースに失敗した場合の詳細情報（`rawMessage`）を提供します。

---

## 依存ライブラリの違い

| Python | Kotlin |
|---|---|
| `anyio` | `kotlinx-coroutines-core` |
| `pydantic` / dataclasses | `kotlinx-serialization-json` |
| `mcp` (MCP ライブラリ) | 自前実装（JSON-RPC ハンドラ） |
| `typing` / `typing_extensions` | Kotlin 型システム（reified generics） |

Kotlin 版は **外部依存が2つだけ**（kotlinx-coroutines と kotlinx-serialization）で、MCP の JSON-RPC プロトコルも自前実装しています。Python 版は `mcp` ライブラリに依存しています。

---

## まとめ

| カテゴリ | 主な違い |
|---|---|
| **API スタイル** | Python: データクラス + 非同期イテレータ / Kotlin: DSL ビルダー + Flow |
| **型安全性** | Python: 実行時チェック中心 / Kotlin: コンパイル時チェック + sealed type |
| **非同期** | Python: anyio / Kotlin: コルーチン（構造化並行性） |
| **MCP ツール** | Python: デコレータ / Kotlin: reified ジェネリクス |
| **Hook 出力** | Python: Async/Sync 分離 / Kotlin: suspend で統合 |
| **命名規則** | Python: snake_case / Kotlin: camelCase |
| **依存関係** | Python: 4+ ライブラリ / Kotlin: 2 ライブラリ |
