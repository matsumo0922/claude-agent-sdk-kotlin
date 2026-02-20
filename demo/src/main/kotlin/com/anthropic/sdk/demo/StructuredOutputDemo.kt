/**
 * 構造化出力デモ
 *
 * OutputFormat + JSON Schema を指定して、Claude の応答を型安全な
 * Kotlin オブジェクトとして受け取るパターンを示す。
 */
package com.anthropic.sdk.demo

import com.anthropic.sdk.annotations.Description
import com.anthropic.sdk.mcp.JsonSchemaGenerator
import com.anthropic.sdk.prompt
import com.anthropic.sdk.types.Model
import com.anthropic.sdk.types.OutputFormat
import kotlinx.serialization.Serializable

// ── 出力型定義 ───────────────────────────────────────

@Description("書籍のレビュー結果")
@Serializable
data class BookReview(
    @Description("書籍タイトル") val title: String,
    @Description("著者名") val author: String,
    @Description("5段階評価") val rating: Int,
    @Description("レビュー本文") val summary: String,
    @Description("おすすめ度") val recommended: Boolean,
    @Description("ジャンルタグ") val tags: List<String> = emptyList(),
)

// ── デモ本体 ──────────────────────────────────────────

suspend fun runStructuredOutputDemo() {
    println("=== 構造化出力デモ ===")
    println()

    // 1. JSON Schema を生成
    val schema = JsonSchemaGenerator.jsonSchema<BookReview>()
    println("■ BookReview の JSON Schema:")
    println("  $schema")
    println()

    // 2. OutputFormat を設定して prompt() を呼ぶ
    println("■ outputFormat で構造化出力を指定:")
    println("  outputFormat(OutputFormat(")
    println("      type = \"json_schema\",")
    println("      schema = JsonSchemaGenerator.jsonSchema<BookReview>()")
    println("  ))")
    println()

    try {
        val result = prompt("「銀河鉄道の夜」(宮沢賢治)のレビューをJSON形式で書いてください") {
            model = Model.SONNET
            maxTurns = 1
            bypassPermissions()
            outputFormat(OutputFormat(
                type = "json_schema",
                schema = schema,
            ))
        }

        println("■ PromptResult:")
        println("  result (raw): ${result.result}")
        println()

        // 3. structuredOutput<T>() で型安全にデシリアライズ
        println("■ structuredOutput<BookReview>() でデシリアライズ:")
        val review = result.structuredOutput<BookReview>()
        println("  タイトル: ${review.title}")
        println("  著者: ${review.author}")
        println("  評価: ${"★".repeat(review.rating)}")
        println("  要約: ${review.summary}")
        println("  おすすめ: ${if (review.recommended) "はい" else "いいえ"}")
        println("  タグ: ${review.tags.joinToString()}")
    } catch (e: Exception) {
        println("  ※ CLI未接続のため実行スキップ: ${e.message}")
    }

    println()
}
