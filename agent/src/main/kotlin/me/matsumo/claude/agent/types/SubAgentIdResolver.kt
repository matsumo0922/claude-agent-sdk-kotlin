package me.matsumo.claude.agent.types

/**
 * Resolves the mapping between hookToolUseId and parentToolUseId for sub-agents.
 *
 * When a sub-agent starts via a hook, the CLI provides a temporary hookToolUseId.
 * The real parentToolUseId is only known when the first AssistantMessage arrives.
 * This resolver maintains a FIFO queue to match them in order.
 *
 * Thread-safe: all methods are synchronized.
 */
public class SubAgentIdResolver {

    /** hookToolUseId -> dynamic key reference (updated when resolved). */
    private val keyRefs = mutableMapOf<String, KeyRef>()

    /** Unresolved hookToolUseIds in FIFO order. */
    private val unresolvedQueue = mutableListOf<String>()

    /** Resolved hookId -> parentToolUseId mappings. */
    private val resolvedMap = mutableMapOf<String, String>()

    /**
     * Register a hookToolUseId when a sub-agent starts.
     *
     * @param hookToolUseId The toolUseId from the hook callback.
     * @return [KeyRef] that dynamically reflects the current key (hookId initially, parentToolUseId after resolution).
     */
    @Synchronized
    public fun register(hookToolUseId: String): KeyRef {
        val ref = KeyRef(hookToolUseId)
        keyRefs[hookToolUseId] = ref
        unresolvedQueue.add(hookToolUseId)
        return ref
    }

    /**
     * Resolve the next unresolved hookToolUseId to the given parentToolUseId (FIFO).
     *
     * Called when an AssistantMessage with a new parentToolUseId is received.
     *
     * @param parentToolUseId The real parentToolUseId from AssistantMessage.
     * @return Resolution result, or null if already resolved or no unresolved IDs remain.
     */
    @Synchronized
    public fun resolve(parentToolUseId: String): ResolveResult? {
        if (resolvedMap.containsValue(parentToolUseId)) return null
        if (unresolvedQueue.isEmpty()) return null

        val hookId = unresolvedQueue.removeFirst()
        resolvedMap[hookId] = parentToolUseId
        keyRefs[hookId]?.update(parentToolUseId)
        return ResolveResult(hookToolUseId = hookId, parentToolUseId = parentToolUseId)
    }

    /**
     * Clear all state. Call when resetting or stopping the session.
     */
    @Synchronized
    public fun clear() {
        keyRefs.clear()
        unresolvedQueue.clear()
        resolvedMap.clear()
    }

    /**
     * Result of a successful ID resolution.
     */
    public data class ResolveResult(
        val hookToolUseId: String,
        val parentToolUseId: String,
    )

    /**
     * A dynamically-updated key reference.
     *
     * Initially holds the hookToolUseId, then updated to parentToolUseId upon resolution.
     * Consumers can read [currentKey] to always get the most up-to-date key.
     */
    public class KeyRef(initialKey: String) {
        @Volatile
        public var currentKey: String = initialKey
            private set

        internal fun update(newKey: String) {
            currentKey = newKey
        }
    }
}
