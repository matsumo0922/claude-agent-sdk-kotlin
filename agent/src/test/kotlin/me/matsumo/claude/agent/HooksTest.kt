package me.matsumo.claude.agent

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.matsumo.claude.agent.types.HookEvent
import me.matsumo.claude.agent.types.HookOutput
import me.matsumo.claude.agent.types.HooksBuilder
import me.matsumo.claude.agent.types.SessionOptionsBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HooksTest {

    @Test
    fun `HookOutput allow produces empty HookJSONOutput`() {
        val output = HookOutput.allow()
        assertNull(output.decision)
        assertNull(output.shouldContinue)
        assertNull(output.reason)
    }

    @Test
    fun `HookOutput deny produces block decision with reason`() {
        val output = HookOutput.deny("Cannot modify sensitive files")
        assertEquals("block", output.decision)
        assertEquals("Cannot modify sensitive files", output.reason)
    }

    @Test
    fun `HookOutput proceed produces empty HookJSONOutput`() {
        val output = HookOutput.proceed()
        assertNull(output.decision)
        assertNull(output.stopReason)
    }

    @Test
    fun `HookOutput modify includes updatedInput in hookSpecificOutput`() {
        val newInput = buildJsonObject { put("file_path", "/safe/path.txt") }
        val output = HookOutput.modify(newInput)
        assertNotNull(output.hookSpecificOutput)
        assertTrue(output.hookSpecificOutput!!.containsKey("updatedInput"))
    }

    @Test
    fun `HooksBuilder registers preToolUse hooks`() {
        val builder = HooksBuilder()
        builder.preToolUse("Edit|Write") { _, _, _ ->
            HookOutput.allow()
        }
        val hooks = builder.build()
        assertTrue(hooks.containsKey(HookEvent.PRE_TOOL_USE))
        assertEquals(1, hooks[HookEvent.PRE_TOOL_USE]!!.size)
        assertEquals("Edit|Write", hooks[HookEvent.PRE_TOOL_USE]!![0].matcher)
    }

    @Test
    fun `HooksBuilder registers postToolUse hooks`() {
        val builder = HooksBuilder()
        builder.postToolUse { _, _, _ ->
            HookOutput.proceed()
        }
        val hooks = builder.build()
        assertTrue(hooks.containsKey(HookEvent.POST_TOOL_USE))
        assertNull(hooks[HookEvent.POST_TOOL_USE]!![0].matcher)
    }

    @Test
    fun `HooksBuilder registers multiple hook types`() {
        val builder = HooksBuilder()
        builder.preToolUse("Bash") { _, _, _ -> HookOutput.allow() }
        builder.postToolUse { _, _, _ -> HookOutput.proceed() }
        builder.stop { _, _, _ -> HookOutput.proceed() }
        builder.notification { _, _, _ -> HookOutput.proceed() }

        val hooks = builder.build()
        assertEquals(4, hooks.size)
        assertTrue(hooks.containsKey(HookEvent.PRE_TOOL_USE))
        assertTrue(hooks.containsKey(HookEvent.POST_TOOL_USE))
        assertTrue(hooks.containsKey(HookEvent.STOP))
        assertTrue(hooks.containsKey(HookEvent.NOTIFICATION))
    }

    @Test
    fun `HooksBuilder on registers arbitrary event hooks`() {
        val builder = HooksBuilder()
        builder.on(HookEvent.SUBAGENT_START) { _, _, _ ->
            HookOutput.proceed()
        }
        val hooks = builder.build()
        assertTrue(hooks.containsKey(HookEvent.SUBAGENT_START))
    }

    @Test
    fun `HookMatcher stores timeout`() {
        val builder = HooksBuilder()
        builder.preToolUse("Edit", timeout = 30.0) { _, _, _ ->
            HookOutput.allow()
        }
        val hooks = builder.build()
        assertEquals(30.0, hooks[HookEvent.PRE_TOOL_USE]!![0].timeout)
    }

    @Test
    fun `HookEvent enum has all expected values`() {
        val events = HookEvent.entries
        assertEquals(10, events.size)
        assertTrue(events.contains(HookEvent.PRE_TOOL_USE))
        assertTrue(events.contains(HookEvent.POST_TOOL_USE))
        assertTrue(events.contains(HookEvent.POST_TOOL_USE_FAILURE))
        assertTrue(events.contains(HookEvent.USER_PROMPT_SUBMIT))
        assertTrue(events.contains(HookEvent.STOP))
        assertTrue(events.contains(HookEvent.SUBAGENT_STOP))
        assertTrue(events.contains(HookEvent.PRE_COMPACT))
        assertTrue(events.contains(HookEvent.NOTIFICATION))
        assertTrue(events.contains(HookEvent.SUBAGENT_START))
        assertTrue(events.contains(HookEvent.PERMISSION_REQUEST))
    }

    @Test
    fun `SessionOptionsBuilder hooks integration`() {
        val builder = SessionOptionsBuilder()
        builder.hooks {
            preToolUse("Read") { _, _, _ -> HookOutput.allow() }
            postToolUse { _, _, _ -> HookOutput.proceed() }
        }
        val options = builder.build()
        assertNotNull(options.hooks)
        assertEquals(2, options.hooks!!.size)
    }
}
