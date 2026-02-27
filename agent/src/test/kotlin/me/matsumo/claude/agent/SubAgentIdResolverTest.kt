package me.matsumo.claude.agent

import me.matsumo.claude.agent.types.SubAgentIdResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SubAgentIdResolverTest {

    @Test
    fun `register returns KeyRef with initial hookId`() {
        val resolver = SubAgentIdResolver()
        val keyRef = resolver.register("hook-1")
        assertEquals("hook-1", keyRef.currentKey)
    }

    @Test
    fun `resolve maps FIFO hookId to parentToolUseId`() {
        val resolver = SubAgentIdResolver()
        val keyRef = resolver.register("hook-1")

        val result = resolver.resolve("parent-1")
        assertNotNull(result)
        assertEquals("hook-1", result.hookToolUseId)
        assertEquals("parent-1", result.parentToolUseId)
        assertEquals("parent-1", keyRef.currentKey)
    }

    @Test
    fun `resolve returns null when queue is empty`() {
        val resolver = SubAgentIdResolver()
        assertNull(resolver.resolve("parent-1"))
    }

    @Test
    fun `resolve returns null for already resolved parentToolUseId`() {
        val resolver = SubAgentIdResolver()
        resolver.register("hook-1")
        resolver.resolve("parent-1")

        // Same parentToolUseId again -> null
        assertNull(resolver.resolve("parent-1"))
    }

    @Test
    fun `FIFO ordering with multiple registrations`() {
        val resolver = SubAgentIdResolver()
        val ref1 = resolver.register("hook-1")
        val ref2 = resolver.register("hook-2")
        val ref3 = resolver.register("hook-3")

        val result1 = resolver.resolve("parent-A")
        assertNotNull(result1)
        assertEquals("hook-1", result1.hookToolUseId)
        assertEquals("parent-A", ref1.currentKey)
        assertEquals("hook-2", ref2.currentKey) // not yet resolved

        val result2 = resolver.resolve("parent-B")
        assertNotNull(result2)
        assertEquals("hook-2", result2.hookToolUseId)
        assertEquals("parent-B", ref2.currentKey)

        val result3 = resolver.resolve("parent-C")
        assertNotNull(result3)
        assertEquals("hook-3", result3.hookToolUseId)
        assertEquals("parent-C", ref3.currentKey)

        // Queue empty
        assertNull(resolver.resolve("parent-D"))
    }

    @Test
    fun `clear resets all state`() {
        val resolver = SubAgentIdResolver()
        resolver.register("hook-1")
        resolver.resolve("parent-1")

        resolver.clear()

        // After clear, resolve returns null (queue empty)
        assertNull(resolver.resolve("parent-2"))

        // Can register again
        val newRef = resolver.register("hook-new")
        assertEquals("hook-new", newRef.currentKey)
        val result = resolver.resolve("parent-new")
        assertNotNull(result)
        assertEquals("hook-new", result.hookToolUseId)
    }
}
