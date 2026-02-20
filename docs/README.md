# Claude Agent SDK for Kotlin

Kotlin 版 Claude Agent SDK のドキュメントです。

このライブラリは、[Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code) をプログラムから制御するための Kotlin SDK です。Python 版 Claude Agent SDK を参考に、Kotlin のイディオム（コルーチン、Flow、sealed class、kotlinx.serialization）を活かして設計されています。

## ドキュメント一覧

| ドキュメント | 内容 |
|---|---|
| [クイックスタート](getting-started.md) | インストール、基本的な使い方、コード例 |
| [アーキテクチャ](architecture.md) | レイヤー構成、通信プロトコル、データフロー |
| [API リファレンス](api-reference.md) | 主要クラス・メソッド・型の一覧 |
| [対応機能一覧](features.md) | 対応済み/未対応の機能リストと説明 |
| [Hook システム](hooks.md) | フック機能の詳細と使い方 |
| [MCP カスタムツール](mcp-tools.md) | インプロセス MCP サーバーによるカスタムツール定義 |
| [Python 版との差異](python-differences.md) | Python SDK との設計・API・挙動の違い |
| [開発ガイド](development.md) | ビルド方法、テスト、プロジェクト構成 |

## 前提条件

- **JDK 17** 以上
- **Claude Code CLI** 2.0.0 以上がインストール済みであること
- Kotlin 2.0.21 / kotlinx.serialization 1.7.3 / kotlinx.coroutines 1.8.1

## 最小限のコード例

```kotlin
import com.anthropic.sdk.query

suspend fun main() {
    val answer = query("日本の首都はどこですか？")
    println(answer) // → 東京です。
}
```

## ライセンス

本プロジェクトのライセンスについてはリポジトリのルートを参照してください。
