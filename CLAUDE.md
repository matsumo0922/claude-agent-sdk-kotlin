# Claude Agent SDK for Kotlin

## Project Overview

This is a Kotlin port of the official Claude Agent SDK (TypeScript/Python). It provides a programmatic interface to Claude Code CLI for building AI agents, embracing Kotlin idioms (coroutines, Flow, sealed types, kotlinx.serialization).

## Architecture

### Layer Structure
```
User Code
    ↓
Public API (query() / createSession() / prompt())
    ↓
Internal Client (InternalClient)
    ↓
Query Controller (handles control protocol, hooks, permissions, MCP)
    ↓
Transport (SubprocessCLITransport)
    ↓
Claude Code CLI (subprocess via ProcessBuilder)
```

### Module Structure
```
claude-agent-sdk/                    # Main library module
  src/main/kotlin/com/anthropic/sdk/
    ├── ClaudeAgentSDK.kt           # Public API: query(), prompt(), createSession(), resumeSession()
    ├── ClaudeSDKClient.kt          # Bidirectional stateful client (multi-turn sessions)
    ├── types/
    │   ├── Messages.kt             # SDKMessage sealed hierarchy
    │   ├── ContentBlocks.kt        # Content block types (text, thinking, tool_use, tool_result)
    │   ├── Options.kt              # ClaudeAgentOptions, Model, PermissionMode
    │   ├── Hooks.kt                # Hook types, HookOutput, HookInput, matchers
    │   ├── MCP.kt                  # MCP server configs (Stdio, SSE, HTTP, SDK)
    │   ├── Agents.kt               # Agent definitions
    │   └── Results.kt              # ResultMessage, cost tracking
    ├── internal/
    │   ├── InternalClient.kt       # Internal client coordinating transport + query
    │   ├── QueryController.kt      # Control protocol handler (hooks, permissions, MCP routing)
    │   ├── MessageParser.kt        # JSON → SDKMessage parsing
    │   └── transport/
    │       ├── Transport.kt        # Abstract transport interface
    │       └── SubprocessTransport.kt # CLI subprocess transport
    ├── mcp/
    │   ├── McpServer.kt            # createSdkMcpServer(), tool DSL
    │   └── JsonSchemaGenerator.kt  # SerialDescriptor → JSON Schema
    ├── errors/
    │   └── Errors.kt               # Exception hierarchy
    └── annotations/
        └── Description.kt          # @Description annotation for schema generation

claude-agent-sdk/
  src/test/kotlin/com/anthropic/sdk/
    ├── TypesTest.kt               # Types, enums, serialization, DSL builder (20 tests)
    ├── MessageParserTest.kt       # JSON → SDKMessage parsing (17 tests)
    ├── JsonSchemaGeneratorTest.kt # SerialDescriptor → JSON Schema (13 tests)
    ├── McpServerTest.kt           # MCP server JSON-RPC handling (7 tests)
    ├── HooksTest.kt               # Hooks DSL and hook outputs (9 tests)
    └── TransportTest.kt           # CLI flag building verification (20 tests)
```

### Key Design Decisions

1. **Sealed interface for messages**: `SDKMessage` is a sealed interface with `SystemMessage`, `AssistantMessage`, `UserMessage`, `ResultMessage` implementations. This enables exhaustive `when` expressions.

2. **Kotlin Flow for streaming**: `session.receive()` returns `Flow<SDKMessage>`. Standard Flow operators work naturally.

3. **kotlinx.serialization**: All JSON handling via compiler-generated serializers. No reflection.

4. **Coroutines**: All async operations are suspend functions. Transport uses coroutine-based process I/O.

5. **DSL builders**: Session configuration uses trailing lambda DSL pattern.

6. **JSON Schema from SerialDescriptor**: Tool schemas auto-generated from `@Serializable` data classes at compile time.

## Communication Protocol with CLI

- Input/Output: Streaming JSON via stdin/stdout (newline-delimited)
- CLI flags: `--input-format stream-json --output-format stream-json --verbose`
- Control messages: Request/response with ID matching
- Minimum CLI version: 2.0.0

## Reference Implementation

The Python SDK at `../claude-agent-sdk-python/` is the reference implementation. Key files:
- `src/claude_agent_sdk/types.py` - All type definitions
- `src/claude_agent_sdk/client.py` - ClaudeSDKClient
- `src/claude_agent_sdk/query.py` - query() function
- `src/claude_agent_sdk/_internal/client.py` - InternalClient
- `src/claude_agent_sdk/_internal/query.py` - Query (control protocol)
- `src/claude_agent_sdk/_internal/transport/subprocess_cli.py` - Transport

## Dependencies

- Kotlin 2.0.21, JVM 17
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`
- Test: `kotlin-test`, `kotlinx-coroutines-test`, `io.mockk:mockk:1.13.13`

## Build

```bash
./gradlew build       # Build all
./gradlew test        # Run tests
```

## Current Status

- [x] Project setup (Gradle, dependencies)
- [x] Core types (messages, content blocks, options)
- [x] Error hierarchy
- [x] Transport layer
- [x] Message parser
- [x] Query controller (control protocol)
- [x] Public API (query, prompt, createSession, resumeSession)
- [x] ClaudeSDKClient (bidirectional sessions)
- [x] MCP server support (in-process SDK MCP servers + tool DSL)
- [x] JSON Schema generator (from SerialDescriptor, with @Description)
- [x] Hooks system (all 10 event types, callback routing)
- [x] File checkpointing (CheckpointId + rewindFiles)
- [x] Structured outputs (PromptResult.structuredOutput<T>())
- [x] Tests (96 tests, all passing)
- [x] Python SDK parity review
- [x] Kotlin code quality review
