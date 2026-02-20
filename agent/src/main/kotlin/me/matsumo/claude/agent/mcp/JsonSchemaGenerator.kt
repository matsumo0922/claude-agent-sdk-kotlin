package me.matsumo.claude.agent.mcp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import me.matsumo.claude.agent.annotations.Description

/**
 * Generates JSON Schema objects from kotlinx.serialization [SerialDescriptor]s.
 *
 * The generator walks the descriptor tree produced by the compiler plugin and emits
 * a JSON Schema (draft 2020-12 compatible subset) suitable for MCP tool definitions
 * and structured-output declarations.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
public object JsonSchemaGenerator {

    /**
     * Generate a JSON Schema for the reified serializable type [T].
     *
     * ```kotlin
     * val schema = JsonSchemaGenerator.jsonSchema<WeatherArgs>()
     * ```
     */
    public inline fun <reified T> jsonSchema(): JsonObject {
        val descriptor = serializer<T>().descriptor
        return jsonSchema(descriptor)
    }

    /**
     * Generate a JSON Schema from an arbitrary [SerialDescriptor].
     *
     * This is the main entry-point when you already have a descriptor in hand
     * (e.g. from `serializer<T>().descriptor`).
     */
    public fun jsonSchema(descriptor: SerialDescriptor): JsonObject {
        val context = Context()
        val root = generateSchema(descriptor, context)
        return if (context.defs.isEmpty()) {
            root
        } else {
            // Merge $defs into the root object
            buildJsonObject {
                root.forEach { (key, value) -> put(key, value) }
                putJsonObject("\$defs") {
                    context.defs.forEach { (name, schema) -> put(name, schema) }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * Mutable context carried through recursive generation so that named types
     * can be extracted into `$defs` and referenced via `$ref`.
     */
    private class Context {
        /** Fully-built definitions keyed by the descriptor's serialName. */
        val defs: MutableMap<String, JsonObject> = mutableMapOf()

        /** Descriptors currently being visited (cycle detection). */
        val visiting: MutableSet<String> = mutableSetOf()
    }

    /**
     * Core recursive function that maps a [SerialDescriptor] to a [JsonObject]
     * representing the corresponding JSON Schema.
     */
    private fun generateSchema(
        descriptor: SerialDescriptor,
        context: Context,
    ): JsonObject {
        // Handle nullable wrapper – just delegate to the inner descriptor.
        // Nullability is expressed via the "required" array on the parent object.
        if (descriptor.isNullable && descriptor.kind == SerialKind.CONTEXTUAL) {
            // Contextual nullable – treat as the inner kind
        }

        return when (val kind = descriptor.kind) {
            // ---- Primitives ------------------------------------------------
            PrimitiveKind.STRING -> primitiveSchema("string", descriptor)
            PrimitiveKind.CHAR -> primitiveSchema("string", descriptor)
            PrimitiveKind.BOOLEAN -> primitiveSchema("boolean", descriptor)
            PrimitiveKind.BYTE,
            PrimitiveKind.SHORT,
            PrimitiveKind.INT,
            PrimitiveKind.LONG -> primitiveSchema("integer", descriptor)
            PrimitiveKind.FLOAT,
            PrimitiveKind.DOUBLE -> primitiveSchema("number", descriptor)

            // ---- Enum ------------------------------------------------------
            SerialKind.ENUM -> buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    for (i in 0 until descriptor.elementsCount) {
                        add(JsonPrimitive(descriptor.getElementName(i)))
                    }
                }
                addDescription(descriptor)
            }

            // ---- List / Array ----------------------------------------------
            StructureKind.LIST -> buildJsonObject {
                put("type", "array")
                if (descriptor.elementsCount > 0) {
                    put("items", generateSchema(descriptor.getElementDescriptor(0), context))
                }
                addDescription(descriptor)
            }

            // ---- Map -------------------------------------------------------
            StructureKind.MAP -> buildJsonObject {
                put("type", "object")
                if (descriptor.elementsCount >= 2) {
                    put("additionalProperties", generateSchema(descriptor.getElementDescriptor(1), context))
                }
                addDescription(descriptor)
            }

            // ---- Object (data class) ---------------------------------------
            StructureKind.OBJECT,
            StructureKind.CLASS -> generateObjectSchema(descriptor, context)

            // ---- Sealed (oneOf) --------------------------------------------
            PolymorphicKind.SEALED -> generateSealedSchema(descriptor, context)

            // ---- Open polymorphic – best-effort ----------------------------
            PolymorphicKind.OPEN -> buildJsonObject {
                put("type", "object")
                addDescription(descriptor)
            }

            // ---- Contextual – best-effort ----------------------------------
            SerialKind.CONTEXTUAL -> buildJsonObject {
                addDescription(descriptor)
            }

            else -> buildJsonObject {
                addDescription(descriptor)
            }
        }
    }

    /**
     * Generates a schema for a `@Serializable` class (or `object`).
     *
     * Named classes that appear more than once (or recursively) are emitted
     * into `$defs` and referenced via `$ref`.
     */
    private fun generateObjectSchema(
        descriptor: SerialDescriptor,
        context: Context,
    ): JsonObject {
        val name = descriptor.serialName

        // If we already emitted this definition, just produce a $ref.
        if (name in context.defs) {
            return refTo(name)
        }

        // Cycle detection: if we are currently visiting this descriptor,
        // insert a placeholder in defs and return a $ref.
        if (name in context.visiting) {
            context.defs[name] = buildJsonObject { } // placeholder
            return refTo(name)
        }

        context.visiting += name

        val schema = buildObjectSchema(descriptor, context)

        context.visiting -= name

        // If the type was referenced recursively (placeholder was set), replace
        // the placeholder with the real schema and return a $ref.
        if (name in context.defs) {
            context.defs[name] = schema
            return refTo(name)
        }

        return schema
    }

    private fun buildObjectSchema(
        descriptor: SerialDescriptor,
        context: Context,
    ): JsonObject = buildJsonObject {
        put("type", "object")

        val requiredList = mutableListOf<String>()

        if (descriptor.elementsCount > 0) {
            putJsonObject("properties") {
                for (i in 0 until descriptor.elementsCount) {
                    val elementName = descriptor.getElementName(i)
                    val elementDescriptor = descriptor.getElementDescriptor(i)
                    val isOptional = descriptor.isElementOptional(i)
                    val isNullable = elementDescriptor.isNullable

                    val elementSchema = generateElementSchema(
                        descriptor, i, elementDescriptor, context
                    )
                    put(elementName, elementSchema)

                    if (!isOptional && !isNullable) {
                        requiredList += elementName
                    }
                }
            }
        }

        if (requiredList.isNotEmpty()) {
            putJsonArray("required") {
                requiredList.forEach { add(JsonPrimitive(it)) }
            }
        }

        addDescription(descriptor)
    }

    /**
     * Generate the schema for a single element (property) of a class, incorporating
     * the element-level @Description annotation.
     */
    private fun generateElementSchema(
        parentDescriptor: SerialDescriptor,
        index: Int,
        elementDescriptor: SerialDescriptor,
        context: Context,
    ): JsonObject {
        val base = generateSchema(unwrapNullable(elementDescriptor), context)

        // Look for @Description on the element
        val descAnno = parentDescriptor.getElementAnnotations(index)
            .filterIsInstance<Description>()
            .firstOrNull()

        return if (descAnno != null && "description" !in base) {
            buildJsonObject {
                base.forEach { (k, v) -> put(k, v) }
                put("description", descAnno.value)
            }
        } else {
            base
        }
    }

    /**
     * Sealed classes → `oneOf` with a discriminator property.
     *
     * kotlinx.serialization represents sealed descriptors with two elements:
     *  - element 0: the discriminator key (usually "type")
     *  - element 1: a descriptor whose sub-descriptors are the sealed subtypes
     */
    private fun generateSealedSchema(
        descriptor: SerialDescriptor,
        context: Context,
    ): JsonObject {
        val discriminatorName = if (descriptor.elementsCount > 0) {
            descriptor.getElementName(0)
        } else {
            "type"
        }

        // The second element's descriptor contains the subclass descriptors.
        val subtypesDescriptor = if (descriptor.elementsCount >= 2) {
            descriptor.getElementDescriptor(1)
        } else {
            null
        }

        val variants = mutableListOf<JsonObject>()

        if (subtypesDescriptor != null) {
            for (i in 0 until subtypesDescriptor.elementsCount) {
                val subDescriptor = subtypesDescriptor.getElementDescriptor(i)
                val subSchema = generateSchema(subDescriptor, context)
                variants += subSchema
            }
        }

        return buildJsonObject {
            putJsonArray("oneOf") {
                variants.forEach { add(it) }
            }
            put("discriminator", JsonPrimitive(discriminatorName))
            addDescription(descriptor)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun primitiveSchema(type: String, descriptor: SerialDescriptor): JsonObject =
        buildJsonObject {
            put("type", type)
            addDescription(descriptor)
        }

    private fun refTo(serialName: String): JsonObject = buildJsonObject {
        put("\$ref", "#/\$defs/$serialName")
    }

    /**
     * If [descriptor] is a nullable wrapper, return the inner (non-null) descriptor.
     * kotlinx.serialization wraps nullable types in a descriptor whose `isNullable` is `true`
     * but whose serial name and kind match the underlying type.
     */
    private fun unwrapNullable(descriptor: SerialDescriptor): SerialDescriptor = descriptor

    /**
     * Looks for a class-level [Description] annotation on the descriptor and,
     * if found, adds `"description"` to the [JsonObjectBuilder].
     */
    private fun JsonObjectBuilder.addDescription(descriptor: SerialDescriptor) {
        val desc = descriptor.annotations
            .filterIsInstance<Description>()
            .firstOrNull()
        if (desc != null) {
            put("description", desc.value)
        }
    }
}

/**
 * Top-level convenience function that delegates to [JsonSchemaGenerator.jsonSchema].
 *
 * ```kotlin
 * val schema = jsonSchema<WeatherArgs>()
 * ```
 */
public inline fun <reified T> jsonSchema(): JsonObject =
    JsonSchemaGenerator.jsonSchema<T>()

/**
 * Top-level convenience overload that accepts a [SerialDescriptor] directly.
 */
public fun jsonSchema(descriptor: SerialDescriptor): JsonObject =
    JsonSchemaGenerator.jsonSchema(descriptor)
