package me.matsumo.claude.agent.internal.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.matsumo.claude.agent.errors.CLIConnectionException
import me.matsumo.claude.agent.errors.CLIJsonDecodeException
import me.matsumo.claude.agent.errors.CLINotFoundException
import me.matsumo.claude.agent.errors.ProcessException
import me.matsumo.claude.agent.types.ClaudeAgentOptions
import me.matsumo.claude.agent.types.Effort
import me.matsumo.claude.agent.types.McpSdkServerConfig
import me.matsumo.claude.agent.types.McpServerConfig
import me.matsumo.claude.agent.types.PermissionMode
import me.matsumo.claude.agent.types.SandboxSettings
import me.matsumo.claude.agent.types.SdkBeta
import me.matsumo.claude.agent.types.SettingSource
import me.matsumo.claude.agent.types.SystemPromptPreset
import me.matsumo.claude.agent.types.ThinkingConfig
import me.matsumo.claude.agent.types.ToolsPreset
import java.io.BufferedReader
import java.io.File
import java.io.OutputStreamWriter
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * [Transport] implementation that spawns the Claude Code CLI as a subprocess
 * and communicates via stdin/stdout using the `stream-json` protocol.
 */
internal class SubprocessTransport(
    private val options: ClaudeAgentOptions,
) : Transport {

    @Volatile
    private var process: Process? = null

    @Volatile
    private var stdinWriter: OutputStreamWriter? = null

    @Volatile
    private var stdoutReader: BufferedReader? = null

    private val writeMutex = Mutex()

    @Volatile
    private var ready: Boolean = false

    @Volatile
    private var exitError: Exception? = null

    private val cliPath: String = options.cliPath ?: findCli()
    private val cwd: String? = options.cwd

    private val maxBufferSize: Int = options.maxBufferSize ?: DEFAULT_MAX_BUFFER_SIZE

    // ------------------------------------------------------------------
    // Transport interface
    // ------------------------------------------------------------------

    override suspend fun connect() {
        if (process != null) return

        if (System.getenv("CLAUDE_AGENT_SDK_SKIP_VERSION_CHECK") == null) {
            checkClaudeVersion()
        }

        val cmd = buildCommand()

        try {
            val processEnv = buildEnvironment()

            val pb = ProcessBuilder(cmd).apply {
                environment().putAll(processEnv)
                if (cwd != null) {
                    directory(File(cwd))
                }
                redirectErrorStream(false)
            }

            val proc = withContext(Dispatchers.IO) { pb.start() }
            process = proc

            stdinWriter = proc.outputStream.writer(Charsets.UTF_8)
            stdoutReader = proc.inputStream.bufferedReader(Charsets.UTF_8)

            // Start stderr reader if callback is configured or debug is on
            val shouldReadStderr = options.stderr != null ||
                    options.extraArgs.containsKey("debug-to-stderr")
            if (shouldReadStderr) {
                startStderrReader(proc)
            }

            ready = true
        } catch (e: java.io.IOException) {
            val cwdPath = cwd
            if (cwdPath != null && !File(cwdPath).exists()) {
                val err = CLIConnectionException("Working directory does not exist: $cwdPath", e)
                exitError = err
                throw err
            }
            val err = CLINotFoundException(
                "Claude Code not found",
                cliPath = cliPath,
            )
            exitError = err
            throw err
        } catch (e: Exception) {
            val err = CLIConnectionException("Failed to start Claude Code: ${e.message}", e)
            exitError = err
            throw err
        }
    }

    override suspend fun write(data: String) {
        writeMutex.withLock {
            if (!ready || stdinWriter == null) {
                throw CLIConnectionException("SubprocessTransport is not ready for writing")
            }

            val proc = process
            if (proc != null && !proc.isAlive) {
                throw CLIConnectionException(
                    "Cannot write to terminated process (exit code: ${proc.exitValue()})"
                )
            }

            exitError?.let {
                throw CLIConnectionException(
                    "Cannot write to process that exited with error: $it", it
                )
            }

            try {
                withContext(Dispatchers.IO) {
                    stdinWriter?.apply {
                        write(data)
                        flush()
                    }
                }
            } catch (e: Exception) {
                ready = false
                exitError = CLIConnectionException("Failed to write to process stdin: ${e.message}", e)
                throw exitError!!
            }
        }
    }

    override fun readMessages(): Flow<String> = flow {
        val reader = stdoutReader ?: throw CLIConnectionException("Not connected")
        var jsonBuffer = ""

        try {
            while (true) {
                val rawLine = withContext(Dispatchers.IO) { reader.readLine() } ?: break // EOF
                val line = rawLine.trim()

                if (line.isEmpty()) continue

                // The stream may deliver multiple JSON objects per read or split
                // a single object across reads, so we buffer and speculatively parse.
                val jsonLines = line.split("\n")

                for (jsonLine in jsonLines) {
                    val trimmed = jsonLine.trim()
                    if (trimmed.isEmpty()) continue

                    jsonBuffer += trimmed

                    if (jsonBuffer.length > maxBufferSize) {
                        val bufLen = jsonBuffer.length
                        jsonBuffer = ""
                        throw CLIJsonDecodeException(
                            "JSON message exceeded maximum buffer size of $maxBufferSize bytes (was $bufLen)",
                        )
                    }

                    try {
                        // Validate it's parseable JSON
                        Json.parseToJsonElement(jsonBuffer)
                        // Success – emit the complete JSON string
                        emit(jsonBuffer)
                        jsonBuffer = ""
                    } catch (_: Exception) {
                        // Incomplete JSON – keep buffering
                    }
                }
            }
        } catch (_: java.io.IOException) {
            // Stream closed
        }

        // Check process exit status
        val proc = process ?: return@flow
        val exitCode = withContext(Dispatchers.IO) { proc.waitFor() }
        if (exitCode != 0) {
            exitError = ProcessException(
                message = "Command failed",
                exitCode = exitCode,
                stderr = "Check stderr output for details",
            )
            throw exitError!!
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun close() {
        // Acquire write lock so no concurrent writes happen
        writeMutex.withLock {
            ready = false

            withContext(Dispatchers.IO) {
                runCatching { stdinWriter?.close() }
            }
            stdinWriter = null
        }

        withContext(Dispatchers.IO) {
            val proc = process
            if (proc != null && proc.isAlive) {
                proc.destroy()
                runCatching { proc.waitFor() }
            }
        }

        stdoutReader = null
        process = null
        exitError = null
    }

    override fun isReady(): Boolean = ready

    override suspend fun endInput() {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { stdinWriter?.close() }
            }
            stdinWriter = null
        }
    }

    // ------------------------------------------------------------------
    // CLI discovery
    // ------------------------------------------------------------------

    private fun findCli(): String {
        // Check PATH via `which` / `where`
        val whichCmd = if (System.getProperty("os.name").lowercase().contains("win")) "where" else "which"
        runCatching {
            val whichProc = ProcessBuilder(whichCmd, "claude")
                .redirectErrorStream(true)
                .start()
            val result = whichProc.inputStream.bufferedReader().readLine()?.trim()
            whichProc.waitFor()
            if (!result.isNullOrBlank() && File(result).exists()) return result
        }

        val home = System.getProperty("user.home")
        val knownLocations = listOf(
            "$home/.npm-global/bin/claude",
            "/usr/local/bin/claude",
            "$home/.local/bin/claude",
            "$home/node_modules/.bin/claude",
            "$home/.yarn/bin/claude",
            "$home/.claude/local/claude",
        )

        for (loc in knownLocations) {
            val path = Path.of(loc)
            if (path.exists() && path.isRegularFile()) return loc
        }

        throw CLINotFoundException(
            "Claude Code not found. Install with:\n" +
                    "  npm install -g @anthropic-ai/claude-code\n" +
                    "\nIf already installed locally, try:\n" +
                    "  export PATH=\"\$HOME/node_modules/.bin:\$PATH\"\n" +
                    "\nOr provide the path via ClaudeAgentOptions:\n" +
                    "  ClaudeAgentOptions(cliPath = \"/path/to/claude\")",
        )
    }

    // ------------------------------------------------------------------
    // Version check
    // ------------------------------------------------------------------

    private suspend fun checkClaudeVersion() {
        try {
            withContext(Dispatchers.IO) {
                val versionProc = ProcessBuilder(cliPath, "-v")
                    .redirectErrorStream(true)
                    .start()

                val output = versionProc.inputStream.bufferedReader().readLine()?.trim() ?: return@withContext
                val completed = versionProc.waitFor()

                val match = VERSION_REGEX.find(output) ?: return@withContext
                val version = match.groupValues[1]
                val versionParts = version.split(".").map { it.toIntOrNull() ?: 0 }
                val minParts = MINIMUM_CLAUDE_CODE_VERSION.split(".").map { it.toInt() }

                if (compareParts(versionParts, minParts) < 0) {
                    System.err.println(
                        "Warning: Claude Code version $version is unsupported in the Agent SDK. " +
                                "Minimum required version is $MINIMUM_CLAUDE_CODE_VERSION. " +
                                "Some features may not work correctly."
                    )
                }
            }
        } catch (_: Exception) {
            // Non-fatal – don't block on version check failures
        }
    }

    private fun compareParts(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    // ------------------------------------------------------------------
    // Command building
    // ------------------------------------------------------------------

    private fun buildCommand(): List<String> {
        val cmd = mutableListOf(cliPath, "--output-format", "stream-json", "--verbose")

        // System prompt
        when (val sp = options.systemPrompt) {
            null -> {
                // Do nothing
            }

            is String -> {
                cmd.addAll(listOf("--system-prompt", sp))
            }

            is SystemPromptPreset -> {
                if (sp.append != null) {
                    cmd.addAll(listOf("--append-system-prompt", sp.append))
                }
            }
        }

        // Tools
        when (val tools = options.tools) {
            null -> {
                // default tools
            }

            is List<*> -> {
                if (tools.isNotEmpty()) {
                    cmd.addAll(listOf("--tools", tools.joinToString(",")))
                }
            }

            is ToolsPreset -> {
                cmd.addAll(listOf("--tools", "default"))
            }
        }

        if (options.allowedTools.isNotEmpty()) {
            cmd.addAll(listOf("--allowedTools", options.allowedTools.joinToString(",")))
        }

        if (options.maxTurns != null) {
            cmd.addAll(listOf("--max-turns", options.maxTurns.toString()))
        }

        if (options.maxBudgetUsd != null) {
            cmd.addAll(listOf("--max-budget-usd", options.maxBudgetUsd.toString()))
        }

        if (options.disallowedTools.isNotEmpty()) {
            cmd.addAll(listOf("--disallowedTools", options.disallowedTools.joinToString(",")))
        }

        if (options.model != null) {
            cmd.addAll(listOf("--model", options.model))
        }

        if (options.fallbackModel != null) {
            cmd.addAll(listOf("--fallback-model", options.fallbackModel))
        }

        if (options.betas.isNotEmpty()) {
            cmd.addAll(listOf("--betas", options.betas.joinToString(",") { betaToString(it) }))
        }

        if (options.permissionPromptToolName != null) {
            cmd.addAll(listOf("--permission-prompt-tool", options.permissionPromptToolName))
        }

        if (options.permissionMode != null) {
            cmd.addAll(listOf("--permission-mode", permissionModeToString(options.permissionMode)))
        }

        if (options.continueConversation) {
            cmd.add("--continue")
        }

        if (options.resume != null) {
            cmd.addAll(listOf("--resume", options.resume))
        }

        // Settings + sandbox merge
        val settingsValue = buildSettingsValue()
        if (settingsValue != null) {
            cmd.addAll(listOf("--settings", settingsValue))
        }

        for (dir in options.addDirs) {
            cmd.addAll(listOf("--add-dir", dir))
        }

        // MCP servers
        if (options.mcpServers.isNotEmpty()) {
            val serversJson = buildMcpConfigJson(options.mcpServers)
            cmd.addAll(listOf("--mcp-config", serversJson))
        }

        if (options.includePartialMessages) {
            cmd.add("--include-partial-messages")
        }

        if (options.forkSession) {
            cmd.add("--fork-session")
        }

        // Setting sources
        if (options.settingSources != null) {
            val sourcesValue = options.settingSources.joinToString(",") { settingSourceToString(it) }
            cmd.addAll(listOf("--setting-sources", sourcesValue))
        }

        // Plugins
        for (plugin in options.plugins) {
            if (plugin.type == "local") {
                cmd.addAll(listOf("--plugin-dir", plugin.path))
            } else {
                throw IllegalArgumentException("Unsupported plugin type: ${plugin.type}")
            }
        }

        // Extra args
        for ((flag, value) in options.extraArgs) {
            if (value == null) {
                cmd.add("--$flag")
            } else {
                cmd.addAll(listOf("--$flag", value))
            }
        }

        // Thinking config
        @Suppress("DEPRECATION")
        var resolvedMaxThinkingTokens = options.maxThinkingTokens
        val thinking = options.thinking
        if (thinking != null) {
            when (thinking) {
                is ThinkingConfig.Adaptive -> {
                    if (resolvedMaxThinkingTokens == null) resolvedMaxThinkingTokens = 32_000
                }

                is ThinkingConfig.Enabled -> resolvedMaxThinkingTokens = thinking.budgetTokens
                is ThinkingConfig.Disabled -> resolvedMaxThinkingTokens = 0
            }
        }
        if (resolvedMaxThinkingTokens != null) {
            cmd.addAll(listOf("--max-thinking-tokens", resolvedMaxThinkingTokens.toString()))
        }

        if (options.effort != null) {
            cmd.addAll(listOf("--effort", effortToString(options.effort)))
        }

        // Structured output JSON schema
        val outputFormat = options.outputFormat
        if (outputFormat != null && outputFormat.type == "json_schema" && outputFormat.schema != null) {
            cmd.addAll(listOf("--json-schema", outputFormat.schema.toString()))
        }

        // Always use streaming stdin
        cmd.addAll(listOf("--input-format", "stream-json"))

        println("Executing command: ${cmd.joinToString(" ")}")

        return cmd
    }

    // ------------------------------------------------------------------
    // Settings + sandbox merge
    // ------------------------------------------------------------------

    private fun buildSettingsValue(): String? {
        val hasSettings = options.settings != null
        val hasSandbox = options.sandbox != null

        if (!hasSettings && !hasSandbox) return null

        // If only settings path and no sandbox, pass through as-is
        if (hasSettings && !hasSandbox) return options.settings

        // Merge sandbox into settings JSON
        val settingsObj = if (hasSettings) {
            val raw = options.settings!!.trim()
            if (raw.startsWith("{") && raw.endsWith("}")) {
                runCatching { Json.parseToJsonElement(raw) as JsonObject }
                    .getOrElse { buildJsonObject { } }
            } else {
                // It's a file path – read and parse
                val file = File(raw)
                if (file.exists()) {
                    runCatching { Json.parseToJsonElement(file.readText()) as JsonObject }
                        .getOrElse { buildJsonObject { } }
                } else {
                    buildJsonObject { }
                }
            }
        } else {
            buildJsonObject { }
        }

        // Merge sandbox
        val merged = buildJsonObject {
            settingsObj.forEach { (k, v) -> put(k, v) }
            if (hasSandbox) {
                put("sandbox", Json.encodeToJsonElement(SandboxSettings.serializer(), options.sandbox!!))
            }
        }

        return merged.toString()
    }

    // ------------------------------------------------------------------
    // MCP config JSON
    // ------------------------------------------------------------------

    private fun buildMcpConfigJson(servers: Map<String, McpServerConfig>): String {
        val mcpServers = buildJsonObject {
            for ((name, config) in servers) {
                when (config) {
                    // SDK servers are handled in-process; send minimal config to CLI
                    is McpSdkServerConfig -> put(name, buildJsonObject {
                        put("type", JsonPrimitive("sdk"))
                        put("name", JsonPrimitive(name))
                    })

                    else -> put(name, Json.encodeToJsonElement(McpServerConfig.serializer(), config))
                }
            }
        }
        val wrapper = buildJsonObject {
            put("mcpServers", mcpServers)
        }
        return wrapper.toString()
    }

    // ------------------------------------------------------------------
    // Environment
    // ------------------------------------------------------------------

    private fun buildEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()

        // Inherit current process env
        env.putAll(System.getenv())

        // User-provided env vars
        env.putAll(options.env)

        // SDK required env vars
        env["CLAUDE_CODE_ENTRYPOINT"] = "sdk-kt"
        env["CLAUDE_AGENT_SDK_VERSION"] = SDK_VERSION

        // File checkpointing
        if (options.enableFileCheckpointing) {
            env["CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING"] = "true"
        }

        if (cwd != null) {
            env["PWD"] = cwd
        }

        return env
    }

    // ------------------------------------------------------------------
    // Stderr reader
    // ------------------------------------------------------------------

    private fun startStderrReader(proc: Process) {
        val stderrCallback = options.stderr
        val stderrStream = proc.errorStream

        // Use a daemon thread for stderr so it doesn't block shutdown
        Thread {
            try {
                stderrStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.lineSequence().forEach { line ->
                        val trimmed = line.trimEnd()
                        if (trimmed.isNotEmpty()) {
                            stderrCallback?.invoke(trimmed)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore errors during stderr reading
            }
        }.apply {
            isDaemon = true
            name = "claude-sdk-stderr-reader"
            start()
        }
    }

    // ------------------------------------------------------------------
    // Enum → string helpers
    // ------------------------------------------------------------------

    private fun permissionModeToString(mode: PermissionMode): String = when (mode) {
        PermissionMode.DEFAULT -> "default"
        PermissionMode.ACCEPT_EDITS -> "acceptEdits"
        PermissionMode.PLAN -> "plan"
        PermissionMode.BYPASS_PERMISSIONS -> "bypassPermissions"
    }

    private fun betaToString(beta: SdkBeta): String = when (beta) {
        SdkBeta.CONTEXT_1M -> "context-1m-2025-08-07"
    }

    private fun effortToString(effort: Effort): String = when (effort) {
        Effort.LOW -> "low"
        Effort.MEDIUM -> "medium"
        Effort.HIGH -> "high"
        Effort.MAX -> "max"
    }

    private fun settingSourceToString(source: SettingSource): String = when (source) {
        SettingSource.USER -> "user"
        SettingSource.PROJECT -> "project"
        SettingSource.LOCAL -> "local"
    }

    internal companion object {
        private const val DEFAULT_MAX_BUFFER_SIZE = 1024 * 1024 // 1 MB
        internal const val MINIMUM_CLAUDE_CODE_VERSION: String = "2.0.0"
        private const val SDK_VERSION = "0.1.0"
        private val VERSION_REGEX = Regex("""(\d+\.\d+\.\d+)""")
    }
}
