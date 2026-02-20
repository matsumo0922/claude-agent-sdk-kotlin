# The Claude Agent SDK for Kotlin

Anthropic's [Agent SDK](https://platform.claude.com/docs/en/agent-sdk/overview) provides a programmatic interface to Claude Code for building AI agents. The official implementations are in TypeScript and Python. If you've looked at the SDK documentation and wished there was a Kotlin version that felt native to the JVM ecosystem, this is that implementation.

We set out to build a feature-complete port that embraces Kotlin's idioms rather than transliterating TypeScript patterns. The SDK uses coroutines for async operations, Flow for streaming, sealed types for message handling, and kotlinx.serialization for JSON—no reflection, no external HTTP frameworks, just standard Kotlin libraries.

Throughout this post, we'll show how common patterns translate from TypeScript to Kotlin, highlighting where the languages differ and where we made deliberate design choices.

The complete feature set includes:

- One-shot queries and multi-turn sessions  
- Streaming via Kotlin Flow  
- Custom tools with MCP servers  
- Pre/post tool hooks for security and observability  
- File checkpointing and rewind  
- Session resumption  
- Structured outputs with JSON Schema validation

## API Overview

### One-Shot Queries

The simplest entry point is `query`, which sends a prompt and returns the text response:

```kotlin
val answer = query("What is the capital of France?")
```

Under the hood, this spawns a Claude Code CLI process in stream-json mode, collects the response messages, and extracts the final result. The process lifecycle, JSON parsing, and error handling are managed internally.

In TypeScript, even a simple query requires iterating over an async generator:

```ts
// TypeScript
for await (const message of query({ prompt: "What is the capital of France?" })) {
  if ("result" in message) console.log(message.result);
}
```

The Kotlin SDK provides this simpler `query` function that handles the iteration internally when you just want the final answer. For streaming access to all messages, you use sessions (covered below).

When you need access to metadata like cost, token counts, or the full result object, use `prompt` instead:

```kotlin
val result = prompt("Explain quantum computing") {
    model = Model.SONNET
    maxTurns = 5
}
println("Response: ${result.result}")
println("Cost: $${result.totalCostUsd}")
println("Tokens: ${result.inputTokens} in, ${result.outputTokens} out")
```

The trailing lambda configures the session options using a DSL builder. You can set the model, limit turns, configure allowed tools, and more.

### Sessions

For multi-turn conversations, you create a session and interact with it using send/receive:

```kotlin
createSession {
    model = Model.SONNET
    allowTools("Read", "Glob", "Grep")
    bypassPermissions()
}.use { session ->
    session.send("Find all TODO comments in the codebase")

    session.receive()
        .filterIsInstance<AssistantMessage>()
        .map { it.textContent() }
        .filter { it.isNotEmpty() }
        .collect { print(it) }
}
```

The `receive()` function returns a `Flow<SDKMessage>` that emits messages as they arrive from the CLI. This is a cold flow—it starts collecting from the process output when you call a terminal operator like `collect`. Because it's a standard Kotlin Flow, you can use all the operators you're familiar with: `filter`, `map`, `take`, `onEach`, `catch`, and so on. The SDK doesn't introduce custom stream abstractions that you need to learn.

Compare the streaming patterns:

```ts
// TypeScript - async generator with manual type checking
for await (const message of query({
  prompt: "Find all TODO comments",
  options: { allowedTools: ["Read", "Glob", "Grep"] }
})) {
  if ("result" in message) console.log(message.result);
}
```

```kotlin
// Kotlin - Flow with standard operators
session.receive()
    .filterIsInstance<ResultMessage>()
    .collect { println(it.result) }
```

In TypeScript, you check message types with `"result" in message`—a runtime property check. In Kotlin, `filterIsInstance<ResultMessage>()` is type-safe: the compiler knows that after this operator, you're working with `ResultMessage` objects. This extends to all Flow operators; you can map, filter, and transform with full type inference.

Sessions implement `Closeable`, so they work with Kotlin's `use` extension for automatic cleanup. When the block completes, the underlying CLI process is terminated and resources are released.

To continue a conversation, call `send()` again after the flow completes:

```kotlin
session.send("Now summarize what you found")
session.receive().collect { ... }
```

### Message Types

All messages from the CLI are modeled as a sealed hierarchy:

```kotlin
sealed interface SDKMessage {
    val sessionId: String
}

data class SystemMessage(...) : SDKMessage
data class AssistantMessage(...) : SDKMessage
data class UserMessage(...) : SDKMessage
data class ResultMessage(...) : SDKMessage
```

`SystemMessage` is emitted at session initialization and on errors. It contains information about MCP server status and any API errors that occurred.

`AssistantMessage` contains Claude's responses. Each message has a `Message` object with a list of content blocks, which we'll discuss next.

`UserMessage` appears when a tool result is sent back to Claude. It includes a UUID that can be used as a checkpoint for file rewinding.

`ResultMessage` signals the end of a turn. It contains the result text, cost information, token counts, and a subtype indicating how the turn ended (success, error, max turns reached, or interrupted).

Because the hierarchy is sealed, `when` expressions over `SDKMessage` are exhaustive. The compiler ensures you handle all message types, and adding a new message type in a future version would be a compile-time breaking change rather than a silent runtime bug.

This is a significant difference from TypeScript, where message handling typically looks like:

```ts
// TypeScript - property checks, no exhaustiveness guarantee
for await (const message of query({...})) {
  if (message.type === "system" && message.subtype === "init") {
    sessionId = message.session_id;
  } else if ("result" in message) {
    console.log(message.result);
  }
  // Easy to forget a case
}
```

```kotlin
// Kotlin - exhaustive when, compiler-enforced
session.receive().collect { message ->
    when (message) {
        is SystemMessage -> if (message.isInit) { /* ... */ }
        is AssistantMessage -> print(message.textContent())
        is UserMessage -> saveCheckpoint(message.uuid)
        is ResultMessage -> println("Done: ${message.result}")
    }
}
```

If Anthropic adds a new message type to the protocol, the TypeScript code silently ignores it. The Kotlin code fails to compile until you handle the new case.

## Custom Tools via MCP

The SDK includes a lightweight MCP (Model Context Protocol) server implementation for defining custom tools. MCP is the protocol Claude Code uses to communicate with external tool providers, and by implementing an MCP server, your tools integrate seamlessly with the CLI.

Each SDK takes a different approach to tool schema definition:

```ts
// TypeScript - Zod schemas at runtime
tool(
  "get_weather",
  "Get current temperature for a location",
  {
    latitude: z.number().describe("Latitude coordinate"),
    longitude: z.number().describe("Longitude coordinate")
  },
  async (args) => { ... }
)
```

```py
# Python - type mappings or raw JSON Schema
@tool("get_weather", "Get temperature", {"latitude": float, "longitude": float})
async def get_weather(args: dict[str, Any]) -> dict[str, Any]:
    ...
```

The Kotlin SDK derives schemas from `@Serializable` data classes at compile time using kotlinx.serialization. This means no runtime schema library, and the handler function receives a fully typed data class rather than a dictionary:

```kotlin
@Serializable
data class WeatherArgs(
    @Description("City name (e.g., 'London, UK')")
    val city: String,
    @Description("Temperature unit")
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS
)

@Serializable
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

@Serializable
data class WeatherResult(
    val temperature: Double,
    val conditions: String
)

val server = createSdkMcpServer("weather") {
    tool<WeatherArgs, WeatherResult>("get_weather", "Get current weather for a city") { args ->
        // Your implementation here
        fetchWeather(args.city, args.unit)
    }
}

fun main() = runMcpServer(server)
```

The `tool` function is generic over the argument and result types. Both must be `@Serializable`. The SDK uses kotlinx.serialization to parse incoming JSON arguments into your data class, and to serialize your result back to JSON.

### Schema Generation from SerialDescriptor

When Claude decides to use a tool, it needs to know the expected argument structure. MCP tools declare this using JSON Schema. Rather than requiring you to write schemas by hand, the SDK generates them automatically from your Kotlin types.

When you annotate a class with `@Serializable`, the Kotlin compiler plugin generates a `SerialDescriptor` at compile time. This descriptor contains the complete type structure: property names, their types, whether they're nullable, whether they have default values, and any annotations.

Our `JsonSchemaGenerator` walks this descriptor tree and produces a JSON Schema:

```kotlin
val schema = jsonSchema<WeatherArgs>()
```

For the `WeatherArgs` class above, this produces:

```json
{
  "type": "object",
  "properties": {
    "city": {
      "type": "string",
      "description": "City name (e.g., 'London, UK')"
    },
    "unit": {
      "type": "string",
      "enum": ["CELSIUS", "FAHRENHEIT"],
      "description": "Temperature unit"
    }
  },
  "required": ["city"]
}
```

Notice that `city` is in the `required` array but `unit` is not—because `unit` has a default value in the Kotlin class. The generator inspects `SerialDescriptor.isElementOptional` to determine this.

The mapping from Kotlin types to JSON Schema types:

| Kotlin Type | JSON Schema |
| :---- | :---- |
| `String`, `Char` | `"type": "string"` |
| `Int`, `Long`, `Short`, `Byte` | `"type": "integer"` |
| `Double`, `Float` | `"type": "number"` |
| `Boolean` | `"type": "boolean"` |
| `enum class` | `"type": "string", "enum": [...]` |
| `List<T>` | `"type": "array", "items": {...}` |
| `Map<String, T>` | `"type": "object", "additionalProperties": {...}` |
| Nested `@Serializable` class | `"$ref": "#/$defs/ClassName"` |
| `sealed class` | `"oneOf": [...], "discriminator": "type"` |
| Nullable type | Not included in `"required"` |
| Property with default | Not included in `"required"` |

The `@Description` annotation is a simple annotation class that the generator recognizes. It populates the `description` field in the schema, which Claude uses to understand the purpose of each parameter. The annotation has no effect on serialization itself.

This approach keeps your tool definitions in sync with your code. When you rename a property, the schema updates. When you add an enum value, it appears in the schema. There are no separate schema files to maintain or keep synchronized.

### Connecting Tools to Sessions

To use your custom tools in a session, you configure an MCP server that Claude Code will spawn:

```kotlin
createSession {
    mcpServers {
        "weather" to McpServerConfig(
            command = "java",
            args = listOf("-cp", classpath, "com.example.WeatherServerKt")
        )
    }
    allowTools("mcp__weather__get_weather")
}
```

The `mcpServers` block defines external MCP servers to connect. Each server has a name (used in tool naming) and a configuration specifying how to launch it. The `allowTools` function whitelists specific tools; by default, Claude will ask for permission before using tools.

Tool names follow the pattern `mcp__<server>__<tool>`. In this case, the weather server's `get_weather` tool becomes `mcp__weather__get_weather`.

You can also connect to existing MCP servers in the ecosystem. The configuration mirrors the TypeScript SDK's structure but uses Kotlin's DSL syntax:

```ts
// TypeScript
const options = {
  mcpServers: {
    brave: { command: "npx", args: ["@anthropic-ai/server-brave-search"] }
  }
};
```

```kotlin
// Kotlin
mcpServers {
    "brave" to McpServerConfig(
        command = "npx",
        args = listOf("@anthropic-ai/server-brave-search")
    )
}
```

For example, to add web search via Brave with an API key:

```kotlin
mcpServers {
    "brave" to McpServerConfig(
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-brave-search"),
        env = mapOf("BRAVE_API_KEY" to apiKey)
    )
}
```

## Hooks

Hooks provide interception points before and after tool execution. They enable security controls, audit logging, input validation, and output transformation.

The TypeScript SDK defines hooks as part of the query options:

```ts
// TypeScript
const logFileChange: HookCallback = async (input) => {
  const filePath = (input as any).tool_input?.file_path ?? "unknown";
  appendFileSync("./audit.log", `${new Date().toISOString()}: ${filePath}\n`);
  return {};
};

for await (const message of query({
  prompt: "Refactor utils.py",
  options: {
    hooks: {
      PostToolUse: [{ matcher: "Edit|Write", hooks: [logFileChange] }]
    }
  }
})) { ... }
```

The Kotlin SDK uses a DSL builder that reads more naturally:

```kotlin
createSession {
    hooks {
        // Runs before any Edit or Write tool
        preToolUse("Edit|Write") { input, toolUseId, sessionId ->
            val path = input.toolInput["file_path"]?.toString() ?: ""
            if (path.contains(".env") || path.contains("credentials")) {
                HookOutput.deny("Cannot modify sensitive files")
            } else {
                HookOutput.allow()
            }
        }

        // Runs after every tool
        postToolUse { output, toolUseId, sessionId ->
            logger.info("[$toolUseId] ${output.toolName} completed")
            HookOutput.proceed()
        }
    }
}
```

The first parameter to `preToolUse` is a regex pattern that matches tool names. The hook only runs for tools whose names match the pattern. Omit the pattern to create a catch-all hook that runs for every tool.

Pre-tool hooks receive a `PreToolInput` containing the tool name and input arguments. They return a `HookOutput` that determines what happens next:

- `HookOutput.allow()` \- Proceed with the tool execution  
- `HookOutput.deny(reason)` \- Block the tool and return an error to Claude  
- `HookOutput.modify(newInput)` \- Proceed with modified input arguments

Post-tool hooks receive a `PostToolOutput` containing the tool's result (stdout, stderr, exit code). They can log, transform the output, or take other actions. They return `HookOutput.proceed()` to continue normally.

Hooks are suspending functions, so you can perform async operations like database lookups or HTTP requests within them.

## File Checkpointing

When Claude modifies files during a session, you might want to undo those changes. The SDK supports file checkpointing through integration with Claude Code's rewind functionality.

Each `UserMessage` includes a UUID that serves as a checkpoint marker. By saving these UUIDs as the session progresses, you can later rewind file changes to any point:

```kotlin
createSession {
    enableCheckpointing()
}.use { session ->
    var checkpoint: String? = null

    session.send("Refactor the authentication module")
    session.receive().collect { msg ->
        if (msg is UserMessage) {
            checkpoint = msg.uuid
        }
    }

    // If the refactoring didn't go well, rewind
    checkpoint?.let {
        session.rewindFiles(CheckpointId(it))
    }
}
```

The `enableCheckpointing()` option tells Claude Code to track file changes. The `rewindFiles` function restores files to their state at the specified checkpoint. This is particularly useful for agents that make speculative changes or when you want to implement an undo feature.

## Session Resumption

Sessions can be resumed after they end, allowing you to continue a conversation later. The TypeScript SDK handles this with a `resume` option:

```ts
// TypeScript - capture session ID, then resume with options
let sessionId: string | undefined;
for await (const message of query({ prompt: "Read auth module", options: {...} })) {
  if (message.type === "system" && message.subtype === "init") {
    sessionId = message.session_id;
  }
}

for await (const message of query({
  prompt: "Find all callers",
  options: { resume: sessionId }
})) { ... }
```

The Kotlin SDK provides a dedicated `resumeSession` function that makes the intent clearer:

```kotlin
// First session
val sessionId = createSession {
    model = Model.SONNET
}.use { session ->
    session.send("Let's start refactoring the payment module")
    session.receive().collect { println(it) }
    session.sessionId  // Save this
}

// Later, resume the session
resumeSession(sessionId!!) {
    model = Model.SONNET
}.use { session ->
    session.send("Continue with the error handling improvements we discussed")
    session.receive().collect { println(it) }
}
```

The resumed session has access to the full conversation history from the previous session. Claude can reference earlier context, remember decisions made, and continue work in progress.

Session IDs are assigned by Claude Code and returned after the session initializes (once you've received the first `SystemMessage` with subtype "init").

## Structured Outputs

For cases where you need Claude's response in a specific format, the SDK supports structured outputs with JSON Schema validation:

```kotlin
@Serializable
data class CodeReview(
    val summary: String,
    val issues: List<Issue>,
    val approved: Boolean
)

@Serializable
data class Issue(
    val severity: Severity,
    val location: String,
    val description: String
)

@Serializable
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

val result = prompt("Review this code: $code") {
    outputSchema<CodeReview>()
}

val review: CodeReview = result.structuredOutput()
```

The `outputSchema<T>()` function generates a JSON Schema from your data class (using the same mechanism as tool schemas) and instructs Claude to respond in that format. Claude Code validates the response against the schema before returning.

The `structuredOutput()` function on the result deserializes the validated JSON into your Kotlin type.

## Dependencies

The SDK has minimal dependencies:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
```

All JSON handling uses kotlinx.serialization, which works through compiler-generated code rather than reflection. There are no HTTP client dependencies—the SDK communicates with Claude Code CLI via stdin/stdout using JSON-lines format.

This minimal footprint makes the SDK easy to integrate into existing projects without dependency conflicts.

## Getting Started

Add the SDK to your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

dependencies {
    implementation("me.matsumo:claude-agent-sdk:1.0")
}
```

You'll need [Claude Code CLI](https://claude.ai/claude-code) installed and authenticated on your machine.

Then try a simple query:

```kotlin
import sdk.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val response = query("Hello, Claude!")
    println(response)
}
```

The examples directory contains complete working examples covering all the features discussed here: multi-turn sessions, custom tools, streaming with Flow operators, hooks, checkpointing, and structured outputs.

The source code is available under Apache 2.0.  

