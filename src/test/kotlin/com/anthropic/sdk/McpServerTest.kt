package com.anthropic.sdk

import com.anthropic.sdk.annotations.Description
import com.anthropic.sdk.mcp.createSdkMcpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpServerTest {

    @Serializable
    data class AddArgs(
        @Description("First number")
        val a: Int,
        @Description("Second number")
        val b: Int,
    )

    @Serializable
    data class AddResult(val sum: Int)

    private val server = createSdkMcpServer("test", "1.0.0") {
        tool<AddArgs, AddResult>("add", "Add two numbers") { args ->
            AddResult(sum = args.a + args.b)
        }
    }

    @Test
    fun `initialize returns server info and capabilities`() = runTest {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "initialize")
        }
        val response = server.handleRequest(request)
        assertNotNull(response)

        assertEquals("2.0", response["jsonrpc"]!!.jsonPrimitive.content)
        val result = response["result"]!!.jsonObject
        assertEquals("test", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("1.0.0", result["serverInfo"]!!.jsonObject["version"]!!.jsonPrimitive.content)
        assertTrue(result["capabilities"]!!.jsonObject.containsKey("tools"))
    }

    @Test
    fun `notifications initialized returns null`() = runTest {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        val response = server.handleRequest(request)
        assertNull(response)
    }

    @Test
    fun `tools list returns registered tools`() = runTest {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/list")
        }
        val response = server.handleRequest(request)
        assertNotNull(response)

        val tools = response["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(1, tools.size)

        val tool = tools[0].jsonObject
        assertEquals("add", tool["name"]!!.jsonPrimitive.content)
        assertEquals("Add two numbers", tool["description"]!!.jsonPrimitive.content)
        assertTrue(tool.containsKey("inputSchema"))
    }

    @Test
    fun `tools call executes handler and returns result`() = runTest {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 3)
            put("method", "tools/call")
            putJsonObject("params") {
                put("name", "add")
                putJsonObject("arguments") {
                    put("a", 3)
                    put("b", 7)
                }
            }
        }
        val response = server.handleRequest(request)
        assertNotNull(response)

        val result = response["result"]!!.jsonObject
        val content = result["content"]!!.jsonArray
        assertTrue(content.isNotEmpty())
        // Result should contain the serialized AddResult
        val textContent = content[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(textContent.contains("10"))
    }

    @Test
    fun `tools call with unknown tool returns error`() = runTest {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 4)
            put("method", "tools/call")
            putJsonObject("params") {
                put("name", "nonexistent")
                putJsonObject("arguments") {}
            }
        }
        val response = server.handleRequest(request)
        assertNotNull(response)

        assertTrue(response.containsKey("error"))
        assertTrue(response["error"]!!.jsonObject["message"]!!.jsonPrimitive.content.contains("not found"))
    }

    @Test
    fun `unknown method returns method not found error`() = runTest {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 5)
            put("method", "resources/list")
        }
        val response = server.handleRequest(request)
        assertNotNull(response)

        val error = response["error"]!!.jsonObject
        assertEquals(-32601, error["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun `tool input schema includes Description annotations`() = runTest {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 6)
            put("method", "tools/list")
        }
        val response = server.handleRequest(request)!!
        val tool = response["result"]!!.jsonObject["tools"]!!.jsonArray[0].jsonObject
        val schema = tool["inputSchema"]!!.jsonObject
        val props = schema["properties"]!!.jsonObject

        assertEquals("First number", props["a"]!!.jsonObject["description"]!!.jsonPrimitive.content)
        assertEquals("Second number", props["b"]!!.jsonObject["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tools call with handler exception returns error content`() = runTest {
        val errorServer = createSdkMcpServer("error-server") {
            tool<AddArgs, AddResult>("failing_tool", "Always fails") { _ ->
                throw RuntimeException("Something went wrong")
            }
        }

        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 7)
            put("method", "tools/call")
            putJsonObject("params") {
                put("name", "failing_tool")
                putJsonObject("arguments") {
                    put("a", 1)
                    put("b", 2)
                }
            }
        }
        val response = errorServer.handleRequest(request)
        assertNotNull(response)

        val result = response["result"]!!.jsonObject
        assertTrue(result["isError"]!!.jsonPrimitive.boolean)
        val content = result["content"]!!.jsonArray[0].jsonObject
        assertTrue(content["text"]!!.jsonPrimitive.content.contains("Something went wrong"))
    }
}
