package com.anthropic.sdk

import com.anthropic.sdk.annotations.Description
import com.anthropic.sdk.mcp.JsonSchemaGenerator
import com.anthropic.sdk.mcp.jsonSchema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonSchemaGeneratorTest {

    // --- Test types ---

    @Serializable
    data class Primitives(
        val s: String,
        val i: Int,
        val l: Long,
        val d: Double,
        val f: Float,
        val b: Boolean,
        val sh: Short,
        val by: Byte,
    )

    @Serializable
    enum class Color { RED, GREEN, BLUE }

    @Serializable
    data class WithEnum(val color: Color)

    @Serializable
    data class WithList(val items: List<String>)

    @Serializable
    data class WithMap(val data: Map<String, Int>)

    @Serializable
    data class Nested(val inner: Inner)

    @Serializable
    data class Inner(val value: String)

    @Serializable
    data class WithNullable(val name: String, val age: Int? = null)

    @Serializable
    data class WithDefault(val name: String, val count: Int = 10)

    @Serializable
    data class WithDescription(
        @Description("The user's full name")
        val name: String,
        @Description("Age in years")
        val age: Int,
    )

    @Description("A described class")
    @Serializable
    data class ClassDescription(val x: Int)

    @Serializable
    sealed interface Shape {
        @Serializable
        @SerialName("circle")
        data class Circle(val radius: Double) : Shape

        @Serializable
        @SerialName("rectangle")
        data class Rectangle(val width: Double, val height: Double) : Shape
    }

    // --- Tests ---

    @Test
    fun `primitive types produce correct JSON Schema`() {
        val schema = jsonSchema<Primitives>()
        val props = schema["properties"]!!.jsonObject

        assertEquals("string", props["s"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("integer", props["i"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("integer", props["l"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("number", props["d"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("number", props["f"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("boolean", props["b"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("integer", props["sh"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("integer", props["by"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `enum produces string with enum values`() {
        val schema = jsonSchema<WithEnum>()
        val colorSchema = schema["properties"]!!.jsonObject["color"]!!.jsonObject

        assertEquals("string", colorSchema["type"]!!.jsonPrimitive.content)
        val enumValues = colorSchema["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("RED", "GREEN", "BLUE"), enumValues)
    }

    @Test
    fun `list produces array schema`() {
        val schema = jsonSchema<WithList>()
        val itemsSchema = schema["properties"]!!.jsonObject["items"]!!.jsonObject

        assertEquals("array", itemsSchema["type"]!!.jsonPrimitive.content)
        assertEquals("string", itemsSchema["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `map produces object with additionalProperties`() {
        val schema = jsonSchema<WithMap>()
        val dataSchema = schema["properties"]!!.jsonObject["data"]!!.jsonObject

        assertEquals("object", dataSchema["type"]!!.jsonPrimitive.content)
        assertEquals("integer", dataSchema["additionalProperties"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `nullable fields are not in required`() {
        val schema = jsonSchema<WithNullable>()
        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }

        assertTrue("name" in required)
        assertFalse("age" in required)
    }

    @Test
    fun `fields with defaults are not in required`() {
        val schema = jsonSchema<WithDefault>()
        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }

        assertTrue("name" in required)
        assertFalse("count" in required)
    }

    @Test
    fun `Description annotation on properties`() {
        val schema = jsonSchema<WithDescription>()
        val props = schema["properties"]!!.jsonObject

        assertEquals("The user's full name", props["name"]!!.jsonObject["description"]!!.jsonPrimitive.content)
        assertEquals("Age in years", props["age"]!!.jsonObject["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Description annotation on class`() {
        val schema = jsonSchema<ClassDescription>()
        assertEquals("A described class", schema["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sealed interface produces oneOf`() {
        val schema = jsonSchema<Shape>()
        assertTrue(schema.containsKey("oneOf"))
        val variants = schema["oneOf"]!!.jsonArray
        assertEquals(2, variants.size)
    }

    @Test
    fun `root schema is always type object for data classes`() {
        val schema = jsonSchema<Primitives>()
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `all required fields listed for non-nullable non-default`() {
        val schema = jsonSchema<Primitives>()
        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        // All fields are non-nullable and have no defaults
        assertEquals(8, required.size)
    }
}
