/**
 * JSON Schema 生成デモ
 *
 * JsonSchemaGenerator.jsonSchema<T>() を直接使い、
 * さまざまな @Serializable 型から JSON Schema を生成するパターンを示す。
 */
package me.matsumo.claude.agent.demo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.matsumo.claude.agent.annotations.Description
import me.matsumo.claude.agent.mcp.JsonSchemaGenerator

// ── 各種型定義 ───────────────────────────────────────

/** プリミティブ型のみ */
@Serializable
data class PrimitiveTypes(
    val name: String,
    val age: Int,
    val score: Double,
    val active: Boolean,
)

/** @Description 付きの型 */
@Description("ユーザー情報を表す型")
@Serializable
data class UserInfo(
    @Description("ユーザーの表示名") val displayName: String,
    @Description("メールアドレス") val email: String,
    @Description("年齢 (0-150)") val age: Int,
)

/** ネストされた型 */
@Serializable
data class Address(val city: String, val zipCode: String)

@Serializable
data class PersonWithAddress(
    val name: String,
    val address: Address,
    val tags: List<String>,
)

/** Nullable + デフォルト値 */
@Serializable
data class OptionalFields(
    val required: String,
    val nullable: String? = null,
    val withDefault: Int = 42,
)

/** Enum 型 */
@Serializable
enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class Task(
    val title: String,
    val priority: Priority,
)

/** Sealed class (oneOf) */
@Serializable
sealed interface Shape {
    @Serializable
    @SerialName("circle")
    data class Circle(val radius: Double) : Shape

    @Serializable
    @SerialName("rectangle")
    data class Rectangle(val width: Double, val height: Double) : Shape
}

@Serializable
data class Drawing(val name: String, val shape: Shape)

/** Map 型 */
@Serializable
data class Config(val settings: Map<String, String>, val flags: Map<String, Boolean>)

// ── デモ本体 ──────────────────────────────────────────

suspend fun runJsonSchemaDemo() {
    println("=== JSON Schema 生成デモ ===")
    println()

    fun showSchema(label: String, schema: kotlinx.serialization.json.JsonObject) {
        println("■ $label:")
        println("  $schema")
        println()
    }

    // 1. プリミティブ型
    showSchema(
        "プリミティブ型 (String, Int, Double, Boolean)",
        JsonSchemaGenerator.jsonSchema<PrimitiveTypes>(),
    )

    // 2. @Description 付き
    showSchema(
        "@Description アノテーション付き",
        JsonSchemaGenerator.jsonSchema<UserInfo>(),
    )

    // 3. ネスト + List
    showSchema(
        "ネストされたオブジェクト + List",
        JsonSchemaGenerator.jsonSchema<PersonWithAddress>(),
    )

    // 4. Nullable + デフォルト
    showSchema(
        "Nullable + デフォルト値 (required 配列に注目)",
        JsonSchemaGenerator.jsonSchema<OptionalFields>(),
    )

    // 5. Enum
    showSchema(
        "Enum (enum 配列として表現)",
        JsonSchemaGenerator.jsonSchema<Task>(),
    )

    // 6. Sealed class
    showSchema(
        "Sealed class (oneOf として表現)",
        JsonSchemaGenerator.jsonSchema<Drawing>(),
    )

    // 7. Map
    showSchema(
        "Map 型 (additionalProperties として表現)",
        JsonSchemaGenerator.jsonSchema<Config>(),
    )

    // 8. トップレベル関数からも利用可能
    println("■ トップレベル関数:")
    println("  import me.matsumo.claude.agent.mcp.jsonSchema")
    println("  val schema = jsonSchema<MyType>()  // 同じ結果")
    println()
}
