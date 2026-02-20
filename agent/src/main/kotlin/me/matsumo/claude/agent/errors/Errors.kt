package me.matsumo.claude.agent.errors

/**
 * Base exception for all Claude SDK errors.
 *
 * All SDK-specific exceptions extend this class, making it easy to catch
 * any SDK error with a single `catch` block.
 */
public open class ClaudeSDKException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Raised when unable to connect to the Claude Code CLI process.
 */
public open class CLIConnectionException(
    message: String? = null,
    cause: Throwable? = null,
) : ClaudeSDKException(message, cause)

/**
 * Raised when the Claude Code CLI binary is not found on the system.
 *
 * This typically means Claude Code is not installed or the configured
 * [cliPath] does not point to a valid executable.
 *
 * @param cliPath The path that was searched for the CLI binary, if available.
 */
public class CLINotFoundException(
    message: String = "Claude Code not found",
    public val cliPath: String? = null,
) : CLIConnectionException(
    if (cliPath != null) "$message: $cliPath" else message,
)

/**
 * Raised when the Claude Code CLI process exits with an error.
 *
 * @param exitCode The process exit code, if available.
 * @param stderr Captured standard error output from the process, if available.
 */
public class ProcessException(
    message: String,
    public val exitCode: Int? = null,
    public val stderr: String? = null,
) : ClaudeSDKException(
    buildString {
        append(message)
        if (exitCode != null) append(" (exit code: $exitCode)")
        if (stderr != null) append("\nError output: $stderr")
    },
)

/**
 * Raised when the SDK fails to decode JSON from the CLI output stream.
 *
 * @param rawText The raw text line that failed to parse.
 */
public class CLIJsonDecodeException(
    public val rawText: String,
    cause: Throwable? = null,
) : ClaudeSDKException("Failed to decode JSON: ${rawText.take(100)}...", cause)

/**
 * Raised when a JSON message from the CLI cannot be mapped to any known [SDKMessage] type.
 *
 * @param rawMessage The raw JSON string of the message that failed to parse.
 */
public class MessageParseException(
    message: String,
    public val rawMessage: String? = null,
    cause: Throwable? = null,
) : ClaudeSDKException(
    if (rawMessage != null) "$message | Raw: ${rawMessage.take(200)}" else message,
    cause,
)
