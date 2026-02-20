# クイックスタート

## セットアップ

### 1. Claude Code CLI のインストール

SDK は Claude Code CLI のサブプロセスとして動作するため、先に CLI をインストールしてください。

```bash
npm install -g @anthropic-ai/claude-code
```

バージョン 2.0.0 以上が必要です。`claude --version` で確認できます。

### 2. プロジェクトへの依存追加

現時点では Maven Central には公開されていないため、ローカルビルドして使用します。

```bash
cd claude-agent-sdk-kotlin
./gradlew build
```

`build.gradle.kts` に依存を追加する場合：

```kotlin
dependencies {
    implementation("me.matsumo.claude:agent:0.1.0-SNAPSHOT")
    // 推移的依存:
    // org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1
    // org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3
}
```

---

## 基本的な使い方

### ワンショットクエリ — `query()`

最もシンプルな使い方です。プロンプトを送り、応答テキストを受け取ります。

```kotlin
import me.matsumo.claude.agent.query

suspend fun main() {
    val answer = query("Kotlin のコルーチンを3行で説明してください")
    println(answer)
}
```

### メタデータ付きクエリ — `prompt()`

コスト、トークン数、所要時間などのメタデータも取得できます。

```kotlin
import me.matsumo.claude.agent.prompt
import me.matsumo.claude.agent.types.Model

suspend fun main() {
    val result = prompt("量子コンピューティングとは？") {
        model = Model.SONNET
        maxTurns = 3
    }

    println("応答: ${result.result}")
    println("コスト: $${result.totalCostUsd}")
    println("入力トークン: ${result.inputTokens}")
    println("出力トークン: ${result.outputTokens}")
    println("ターン数: ${result.numTurns}")
    println("所要時間: ${result.durationMs}ms")
}
```

### マルチターンセッション — `createSession()`

双方向の対話的セッションを構築できます。`Closeable` を実装しているため `use {}` でリソースを自動解放できます。

```kotlin
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.types.*
import kotlinx.coroutines.flow.collect

suspend fun main() {
    createSession {
        model = Model.SONNET
        allowTools("Read", "Glob", "Grep")
        bypassPermissions()
    }.use { session ->
        session.connect()

        // 1回目のプロンプト
        session.send("src/ ディレクトリの構成を教えてください")
        session.receiveResponse().collect { msg ->
            when (msg) {
                is AssistantMessage -> print(msg.textContent())
                is ResultMessage -> println("\n--- 完了 ---")
                else -> {}
            }
        }

        // 2回目のプロンプト（同一セッション内で会話を継続）
        session.send("そのうちテストファイルだけをリストアップしてください")
        session.receiveResponse().collect { msg ->
            when (msg) {
                is AssistantMessage -> print(msg.textContent())
                is ResultMessage -> println("\n--- 完了 ---")
                else -> {}
            }
        }
    }
}
```

### セッション再開 — `resumeSession()`

以前のセッション ID を使って会話を再開できます。

```kotlin
import me.matsumo.claude.agent.createSession
import me.matsumo.claude.agent.resumeSession
import me.matsumo.claude.agent.types.*
import kotlinx.coroutines.flow.collect

suspend fun main() {
    // 最初のセッション
    var savedSessionId: String? = null
    createSession {
        model = Model.SONNET
        bypassPermissions()
    }.use { session ->
        session.connect()
        session.send("Kotlin の sealed class について教えてください")
        session.receiveResponse().collect { msg ->
            when (msg) {
                is SystemMessage -> if (msg.isInit) savedSessionId = msg.sessionId
                is AssistantMessage -> print(msg.textContent())
                is ResultMessage -> println()
                else -> {}
            }
        }
    }

    // セッションの再開
    val sessionId = savedSessionId ?: return
    resumeSession(sessionId) {
        model = Model.SONNET
        bypassPermissions()
    }.use { session ->
        session.connect()
        session.send("さっきの例にエラーハンドリングを追加してください")
        session.receiveResponse().collect { msg ->
            when (msg) {
                is AssistantMessage -> print(msg.textContent())
                is ResultMessage -> println()
                else -> {}
            }
        }
    }
}
```

---

## セッション設定 DSL

`SessionOptionsBuilder` DSL を使って、セッションを細かく設定できます。

```kotlin
createSession {
    // モデル設定
    model = Model.OPUS               // enum で指定
    // modelId = "claude-sonnet-4-6" // または文字列で直接指定
    fallbackModel = "haiku"

    // ターンとコストの制限
    maxTurns = 10
    maxBudgetUsd = 1.0

    // ツール設定
    allowTools("Read", "Glob", "Grep", "Bash")
    disallowTools("Write", "Edit")

    // パーミッション
    bypassPermissions()
    // または: permissionMode = PermissionMode.ACCEPT_EDITS

    // システムプロンプト
    systemPrompt = "あなたはKotlinの専門家です。"

    // 作業ディレクトリ
    cwd = "/path/to/project"

    // 環境変数
    env {
        put("MY_API_KEY", "xxx")
    }

    // Extended Thinking
    thinking = ThinkingConfig.Enabled(budgetTokens = 8192)
    effort = Effort.HIGH

    // サンドボックス
    sandbox = SandboxSettings(enabled = true)

    // stderr コールバック
    stderr = { line -> System.err.println("[CLI] $line") }
}
```

すべてのオプションの詳細は [API リファレンス](api-reference.md) を参照してください。
