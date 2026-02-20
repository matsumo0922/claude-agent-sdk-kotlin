# Hook システム

Hook はツールの実行前後やセッションイベントに介入するためのコールバック機構です。セキュリティ制御、ロギング、ツール入力の書き換えなどに使用できます。

## 概要

```
Claude Code CLI
  │
  ├── ツール実行リクエスト
  │     │
  │     ▼
  │   [PreToolUse Hook]  ← 実行前に介入（許可/拒否/入力変更）
  │     │
  │     ▼
  │   ツール実行
  │     │
  │     ├── 成功 → [PostToolUse Hook]
  │     └── 失敗 → [PostToolUseFailure Hook]
  │
  ├── ユーザープロンプト
  │     │
  │     ▼
  │   [UserPromptSubmit Hook]
  │
  ├── セッション停止
  │     ▼
  │   [Stop Hook]
  │
  └── ... (その他イベント)
```

## Hook イベント一覧

| イベント | 説明 | 入力型 | 用途 |
|---|---|---|---|
| `PRE_TOOL_USE` | ツール実行前 | `PreToolUseHookInput` | ツール入力の検証・書き換え・ブロック |
| `POST_TOOL_USE` | ツール実行成功後 | `PostToolUseHookInput` | ログ記録、出力の確認 |
| `POST_TOOL_USE_FAILURE` | ツール実行失敗後 | `PostToolUseFailureHookInput` | エラーログ、リカバリー |
| `USER_PROMPT_SUBMIT` | プロンプト送信時 | `UserPromptSubmitHookInput` | プロンプトの検証・変更 |
| `STOP` | セッション停止時 | `StopHookInput` | クリーンアップ、結果記録 |
| `SUBAGENT_STOP` | サブエージェント停止 | `SubagentStopHookInput` | サブエージェント監視 |
| `PRE_COMPACT` | コンテキスト圧縮前 | `PreCompactHookInput` | 圧縮前のカスタム指示追加 |
| `NOTIFICATION` | 通知受信 | `NotificationHookInput` | 通知のカスタム処理 |
| `SUBAGENT_START` | サブエージェント開始 | `SubagentStartHookInput` | サブエージェント監視 |
| `PERMISSION_REQUEST` | パーミッション要求 | `PermissionRequestHookInput` | パーミッション判定のカスタマイズ |

## 基本的な使い方

### HooksBuilder DSL

`SessionOptionsBuilder` の `hooks { }` ブロックで Hook を登録します。

```kotlin
createSession {
    hooks {
        // ツール実行前フック（全ツール対象）
        preToolUse { input, toolUseId, context ->
            val hookInput = input as PreToolUseHookInput
            println("ツール実行: ${hookInput.toolName}")
            HookOutput.allow()
        }

        // 特定ツールのみ対象（正規表現パターン）
        preToolUse("Bash|Write|Edit") { input, toolUseId, context ->
            val hookInput = input as PreToolUseHookInput
            println("書き込みツール: ${hookInput.toolName}")
            HookOutput.allow()
        }

        // ツール実行後フック
        postToolUse { input, toolUseId, context ->
            val hookInput = input as PostToolUseHookInput
            println("完了: ${hookInput.toolName}")
            HookOutput.proceed()
        }

        // セッション停止フック
        stop { input, toolUseId, context ->
            println("セッション終了")
            HookOutput.proceed()
        }
    }
}
```

### 汎用的な `on()` メソッド

任意のイベントに対してフックを登録できます。

```kotlin
hooks {
    on(HookEvent.NOTIFICATION) { input, _, _ ->
        val hookInput = input as NotificationHookInput
        println("[通知] ${hookInput.title}: ${hookInput.message}")
        HookOutput.proceed()
    }
}
```

## HookOutput の種類

Hook コールバックは `HookJSONOutput` を返す必要があります。`HookOutput` のコンパニオンオブジェクトにヘルパー関数が用意されています。

### `HookOutput.allow()`

ツール実行を許可します。`PreToolUse` で主に使用します。

```kotlin
preToolUse { input, _, _ ->
    HookOutput.allow()
}
```

### `HookOutput.deny(reason)`

ツール実行を拒否します。理由メッセージが Claude にフィードバックされます。

```kotlin
preToolUse("Write|Edit") { input, _, _ ->
    val hookInput = input as PreToolUseHookInput
    val filePath = hookInput.toolInput["file_path"]?.toString() ?: ""

    if (filePath.contains("/production/")) {
        HookOutput.deny("本番環境のファイルは変更できません")
    } else {
        HookOutput.allow()
    }
}
```

### `HookOutput.proceed()`

フック処理を完了します。`PostToolUse` や `Stop` など、制御が不要なフックで使用します。

```kotlin
postToolUse { input, _, _ ->
    // ログだけ記録して続行
    HookOutput.proceed()
}
```

### `HookOutput.modify(newInput)`

ツール入力を書き換えます。`PreToolUse` で使用します。

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

preToolUse("Bash") { input, _, _ ->
    val hookInput = input as PreToolUseHookInput
    val command = hookInput.toolInput["command"]?.toString() ?: ""

    if (command.contains("rm -rf")) {
        // 危険なコマンドを安全なバージョンに書き換え
        HookOutput.modify(buildJsonObject {
            put("command", command.replace("rm -rf", "rm -ri"))
        })
    } else {
        HookOutput.allow()
    }
}
```

## Hook 入力型の詳細

### PreToolUseHookInput

```kotlin
data class PreToolUseHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String?,
    val toolName: String,          // ツール名 ("Bash", "Read" など)
    val toolInput: JsonObject,     // ツールへの入力パラメータ
    val toolUseId: String?,        // ツール使用 ID
) : BaseHookInput
```

### PostToolUseHookInput

```kotlin
data class PostToolUseHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String?,
    val toolName: String,
    val toolInput: JsonObject,
    val toolResponse: JsonElement?,  // ツールの実行結果
    val toolUseId: String?,
) : BaseHookInput
```

### PostToolUseFailureHookInput

```kotlin
data class PostToolUseFailureHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String?,
    val toolName: String,
    val toolInput: JsonObject,
    val toolUseId: String?,
    val error: String?,             // エラーメッセージ
    val isInterrupt: Boolean?,      // 中断によるものか
) : BaseHookInput
```

### UserPromptSubmitHookInput

```kotlin
data class UserPromptSubmitHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String?,
    val prompt: String?,            // ユーザーのプロンプトテキスト
) : BaseHookInput
```

### NotificationHookInput

```kotlin
data class NotificationHookInput(
    override val sessionId: String,
    override val transcriptPath: String,
    override val cwd: String,
    override val permissionMode: String?,
    val message: String?,           // 通知メッセージ
    val title: String?,             // 通知タイトル
    val notificationType: String?,  // 通知タイプ
) : BaseHookInput
```

他の入力型（`StopHookInput`, `SubagentStopHookInput`, `PreCompactHookInput`, `SubagentStartHookInput`, `PermissionRequestHookInput`）についてはソースコード `types/Hooks.kt` を参照してください。

## マッチャーパターン

`preToolUse` と `postToolUse`、`postToolUseFailure` では、`matcher` パラメータでツール名のパターンを指定できます。

```kotlin
hooks {
    // 単一ツール
    preToolUse("Bash") { ... }

    // 複数ツール（OR パターン）
    preToolUse("Write|Edit|MultiEdit") { ... }

    // 全ツール（matcher 省略）
    preToolUse { ... }
}
```

## タイムアウト

`timeout` パラメータでフック実行のタイムアウトを秒単位で指定できます。

```kotlin
hooks {
    preToolUse("Bash", timeout = 5.0) { input, _, _ ->
        // 5秒以内に完了しなければタイムアウト
        HookOutput.allow()
    }
}
```

## 実行フロー

1. CLI がツール実行前に制御リクエスト（`hook_callback`）を SDK に送信
2. `QueryController` がコールバック ID からフック関数を特定
3. 入力 JSON を適切な `HookInput` サブタイプに変換
4. Hook コールバックを実行（suspend 関数として非同期実行可能）
5. `HookJSONOutput` を制御レスポンスとして CLI に返却
6. CLI がフック結果に基づいてツール実行を制御

## ユースケース例

### セキュリティゲート

```kotlin
hooks {
    preToolUse("Bash") { input, _, _ ->
        val hookInput = input as PreToolUseHookInput
        val command = hookInput.toolInput["command"]?.toString() ?: ""

        val dangerousPatterns = listOf("rm -rf /", "sudo", "chmod 777", ":(){ :|:& };:")
        if (dangerousPatterns.any { command.contains(it) }) {
            HookOutput.deny("危険なコマンドがブロックされました: $command")
        } else {
            HookOutput.allow()
        }
    }
}
```

### 実行ログ

```kotlin
hooks {
    preToolUse { input, _, _ ->
        val hookInput = input as PreToolUseHookInput
        logger.info("→ ${hookInput.toolName}: ${hookInput.toolInput}")
        HookOutput.allow()
    }

    postToolUse { input, _, _ ->
        val hookInput = input as PostToolUseHookInput
        logger.info("← ${hookInput.toolName}: 成功")
        HookOutput.proceed()
    }

    postToolUseFailure { input, _, _ ->
        val hookInput = input as PostToolUseFailureHookInput
        logger.error("✗ ${hookInput.toolName}: ${hookInput.error}")
        HookOutput.proceed()
    }
}
```
