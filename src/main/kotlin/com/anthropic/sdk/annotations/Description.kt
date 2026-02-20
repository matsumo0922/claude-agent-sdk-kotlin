package com.anthropic.sdk.annotations

/**
 * Provides a human-readable description for a serializable property or class.
 *
 * When used on properties of `@Serializable` data classes, the [JsonSchemaGenerator]
 * includes this text as the `"description"` field in the generated JSON Schema.
 * Claude uses these descriptions to understand the purpose of tool parameters.
 *
 * Example:
 * ```kotlin
 * @Serializable
 * data class WeatherArgs(
 *     @Description("City name (e.g., 'London, UK')")
 *     val city: String,
 *     @Description("Temperature unit")
 *     val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
 * )
 * ```
 *
 * @param value The description text to include in the JSON Schema.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Description(val value: String)
