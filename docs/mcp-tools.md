# MCP カスタムツール

MCP (Model Context Protocol) を使うと、Claude がセッション中に呼び出せるカスタムツールを定義できます。Kotlin SDK では **インプロセス MCP サーバー** として実装でき、別プロセスを起動することなくツールを提供できます。

## 概要

```
Claude Code CLI
  │
  ├── ツール呼び出し: mcp__myserver__search
  │     │
  │     ▼
  │   SDK (制御プロトコル経由)
  │     │
  │     ▼
  │   SdkMcpServer.handleRequest()
  │     │
  │     ▼
  │   ToolDefinition.handler() ← ユーザー定義のハンドラ
  │     │
  │     ▼
  │   結果を CLI に返却
```

ツール名は `mcp__<サーバー名>__<ツール名>` の形式で、`allowTools` に含める必要があります。

## 基本的な使い方

### 1. MCP サーバーの作成

```kotlin
import com.anthropic.sdk.mcp.createSdkMcpServer
import com.anthropic.sdk.annotations.Description
import kotlinx.serialization.Serializable

// ツールの入力型を定義
@Serializable
@Description("検索パラメータ")
data class SearchArgs(
    @Description("検索クエリ文字列")
    val query: String,
    @Description("最大結果数")
    val maxResults: Int = 10,
)

// ツールの出力型を定義
@Serializable
data class SearchResult(
    val items: List<String>,
    val totalCount: Int,
)

// MCP サーバーを作成
val searchServer = createSdkMcpServer("search-tools", version = "1.0.0") {
    tool<SearchArgs, SearchResult>("search", "テキストを検索する") { args ->
        // args は自動的に SearchArgs にデシリアライズされる
        val results = performSearch(args.query, args.maxResults)
        SearchResult(
            items = results,
            totalCount = results.size,
        )
        // 戻り値は自動的に JSON にシリアライズされる
    }
}
```

### 2. セッションに登録

```kotlin
import com.anthropic.sdk.createSession
import com.anthropic.sdk.types.McpSdkServerConfig

createSession {
    mcpServers {
        put("search", McpSdkServerConfig(searchServer))
    }
    // MCP ツールを許可リストに追加
    allowTools("mcp__search__search")
    bypassPermissions()
}
```

### 3. 使用

```kotlin
session.send("'TODO' を含むファイルを検索してください")
session.receiveResponse().collect { msg ->
    when (msg) {
        is AssistantMessage -> print(msg.textContent())
        is ResultMessage -> println()
        else -> {}
    }
}
// Claude が mcp__search__search ツールを自動的に呼び出す
```

## tool<A, R>() の仕組み

### シグネチャ

```kotlin
inline fun <reified A, reified R> tool(
    name: String,
    description: String,
    noinline handler: suspend (A) -> R,
)
```

| パラメータ | 説明 |
|---|---|
| `A` | 入力型。`@Serializable` が必要 |
| `R` | 出力型。`@Serializable` が必要 |
| `name` | ツール名 |
| `description` | ツールの説明（Claude に提示される） |
| `handler` | ツール本体。suspend 関数として非同期処理が可能 |

### 処理フロー

1. `A` の `SerialDescriptor` から JSON Schema を自動生成（`JsonSchemaGenerator`）
2. Claude がツールを呼び出すと、CLI が制御プロトコル経由で SDK にリクエスト送信
3. SDK がリクエストの引数 JSON を `A` にデシリアライズ
4. `handler(args)` を実行
5. 結果 `R` を JSON にシリアライズし、MCP レスポンスとして CLI に返却

## Raw ツール

JSON Schema を手動で指定したい場合は `toolRaw()` を使います。

```kotlin
import kotlinx.serialization.json.*

val server = createSdkMcpServer("tools") {
    toolRaw(
        name = "echo",
        description = "入力をそのまま返す",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("text") {
                    put("type", "string")
                    put("description", "エコーするテキスト")
                }
            }
            putJsonArray("required") { add("text") }
        },
    ) { input ->
        // input: JsonObject, 戻り値: JsonElement
        JsonPrimitive(input["text"]?.jsonPrimitive?.content ?: "")
    }
}
```

## @Description アノテーション

`@Description` アノテーションを使うと、JSON Schema の `description` フィールドに説明文を追加できます。

```kotlin
import com.anthropic.sdk.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@Description("ファイル操作のパラメータ")
data class FileArgs(
    @Description("操作対象のファイルパス")
    val path: String,

    @Description("書き込む内容（読み取りの場合は不要）")
    val content: String? = null,
)
```

生成される JSON Schema:

```json
{
  "type": "object",
  "description": "ファイル操作のパラメータ",
  "properties": {
    "path": {
      "type": "string",
      "description": "操作対象のファイルパス"
    },
    "content": {
      "type": "string",
      "description": "書き込む内容（読み取りの場合は不要）"
    }
  },
  "required": ["path"]
}
```

`@Description` は `@SerialInfo` アノテーションを使用しているため、kotlinx.serialization のコンパイラプラグインによって `SerialDescriptor` に自動的に保持されます。

## JSON Schema 生成

`JsonSchemaGenerator` は `@Serializable` クラスの `SerialDescriptor` から JSON Schema を生成します。

### 対応する型マッピング

| Kotlin の型 | JSON Schema |
|---|---|
| `String` | `{ "type": "string" }` |
| `Int`, `Long`, `Short`, `Byte` | `{ "type": "integer" }` |
| `Double`, `Float` | `{ "type": "number" }` |
| `Boolean` | `{ "type": "boolean" }` |
| `List<T>`, `Set<T>` | `{ "type": "array", "items": ... }` |
| `Map<String, T>` | `{ "type": "object", "additionalProperties": ... }` |
| `T?` (nullable) | `required` から除外 |
| デフォルト値あり | `required` から除外 |
| `enum class` | `{ "type": "string", "enum": [...] }` |
| `sealed class/interface` | `{ "oneOf": [...] }` |
| `@Serializable data class` | `{ "type": "object", "properties": ... }` |

### 直接使用

MCP サーバー以外でも JSON Schema 生成を利用できます。

```kotlin
import com.anthropic.sdk.mcp.jsonSchema

@Serializable
data class Config(
    val host: String,
    val port: Int = 8080,
    val debug: Boolean = false,
)

val schema = jsonSchema<Config>()
println(schema) // JSON Schema を出力
```

## 複数ツールの登録

```kotlin
val server = createSdkMcpServer("file-tools", version = "1.0.0") {
    tool<ReadArgs, ReadResult>("read", "ファイルを読み取る") { args ->
        ReadResult(content = File(args.path).readText())
    }

    tool<WriteArgs, WriteResult>("write", "ファイルに書き込む") { args ->
        File(args.path).writeText(args.content)
        WriteResult(success = true)
    }

    tool<ListArgs, ListResult>("list", "ディレクトリの内容を一覧表示") { args ->
        val files = File(args.path).listFiles()?.map { it.name } ?: emptyList()
        ListResult(files = files)
    }
}

createSession {
    mcpServers {
        put("files", McpSdkServerConfig(server))
    }
    allowTools(
        "mcp__files__read",
        "mcp__files__write",
        "mcp__files__list",
    )
}
```

## 外部 MCP サーバーとの併用

インプロセスサーバーと外部サーバーを組み合わせることもできます。

```kotlin
createSession {
    mcpServers {
        // インプロセス SDK サーバー
        put("mytools", McpSdkServerConfig(myServer))

        // 外部 stdio サーバー
        put("external", McpStdioServerConfig(
            command = "npx",
            args = listOf("-y", "@anthropic/mcp-server-filesystem"),
            env = mapOf("HOME" to "/tmp"),
        ))

        // SSE サーバー
        put("remote", McpSSEServerConfig(
            url = "http://localhost:3000/mcp",
            headers = mapOf("Authorization" to "Bearer token"),
        ))
    }
}
```

## スタンドアロン MCP サーバー

`runMcpServer()` を使うと、SDK サーバーを独立した MCP サーバープロセスとして実行できます。これは Claude Code の `McpStdioServerConfig` から参照する外部ツールとして使う場合に便利です。

```kotlin
import com.anthropic.sdk.mcp.createSdkMcpServer
import com.anthropic.sdk.mcp.runMcpServer

fun main() {
    val server = createSdkMcpServer("standalone") {
        tool<MyArgs, MyResult>("process", "データを処理する") { args ->
            // ...
        }
    }
    // stdin/stdout で JSON-RPC を処理する
    runMcpServer(server)
}
```

## エラーハンドリング

ツールハンドラ内で例外がスローされた場合、エラーメッセージが MCP レスポンスの `isError: true` として返却されます。

```kotlin
tool<Args, Result>("risky", "失敗する可能性があるツール") { args ->
    if (!isValid(args)) {
        throw IllegalArgumentException("無効な引数: ${args}")
    }
    // ... 正常処理
}
```

Claude はエラーメッセージを受け取り、適切にリトライや代替手段を試みます。
