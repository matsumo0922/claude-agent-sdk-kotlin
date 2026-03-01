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
Transport (SubprocessTransport)
    ↓
Claude Code CLI (subprocess via ProcessBuilder)
```

### Module Structure
```
agent/                               # Main library module
  src/main/kotlin/me/matsumo/claude/agent/
    ├── ClaudeAgentSDK.kt           # Public API: query(), prompt(), createSession(), resumeSession()
    ├── ClaudeSDKClient.kt          # Bidirectional stateful client (multi-turn sessions)
    ├── types/
    │   ├── Messages.kt             # SDKMessage sealed hierarchy
    │   ├── ContentBlocks.kt        # Content block types (text, thinking, tool_use, tool_result)
    │   ├── Options.kt              # ClaudeAgentOptions, Model, PermissionMode
    │   ├── Hooks.kt                # Hook types, HookOutput, HookInput, matchers
    │   ├── MCP.kt                  # MCP server configs (Stdio, SSE, HTTP, SDK)
    │   ├── Agents.kt               # Agent definitions
    │   ├── Results.kt              # ResultMessage, cost tracking
    │   ├── ApiStreamEvents.kt     # Anthropic API streaming event types (MessageStart, ContentBlockDelta 等)
    │   ├── ContentBlockBuilder.kt # Type-safe DSL for building content blocks (text, image, document)
    │   ├── SubAgentIdResolver.kt  # hookToolUseId ↔ parentToolUseId の FIFO マッピング (thread-safe)
    │   └── SubAgentPaths.kt       # Sub-agent transcript path construction utilities
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

agent/
  src/test/kotlin/me/matsumo/claude/agent/
    ├── TypesTest.kt               # Types, enums, serialization, DSL builder (25 tests)
    ├── MessageParserTest.kt       # JSON → SDKMessage parsing (18 tests)
    ├── JsonSchemaGeneratorTest.kt # SerialDescriptor → JSON Schema (11 tests)
    ├── McpServerTest.kt           # MCP server JSON-RPC handling (8 tests)
    ├── HooksTest.kt               # Hooks DSL and hook outputs (11 tests)
    ├── TransportTest.kt           # CLI flag building verification (23 tests)
    ├── ApiStreamEventTest.kt      # API streaming event parsing (9 tests)
    ├── ContentBlockBuilderTest.kt # Content block DSL builder (6 tests)
    ├── SubAgentIdResolverTest.kt  # Sub-agent ID resolution (6 tests)
    └── SubAgentPathsTest.kt       # Sub-agent path construction (3 tests)

demo/                                # Demo applications
  src/main/kotlin/me/matsumo/claude/agent/demo/
    ├── Main.kt                     # Demo runner entry point
    ├── QueryDemo.kt / PromptDemo.kt / StreamingDemo.kt  # Basic usage demos
    ├── MultiTurnSessionDemo.kt / SessionResumeDemo.kt   # Session management demos
    ├── McpToolDemo.kt / McpMultipleToolsDemo.kt / McpStandaloneDemo.kt / McpRawToolDemo.kt  # MCP demos
    ├── HooksDemo.kt / HookEventsDemo.kt                 # Hook system demos
    └── ... (20+ demo files covering all SDK features)
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

Managed via `gradle/libs.versions.toml` (version catalog).

- Kotlin 2.3.0, JVM 17
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1`
- Test: `kotlin-test`, `kotlinx-coroutines-test`, `io.mockk:mockk:1.14.9`

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
- [x] Tests (120 tests, all passing)
- [x] Python SDK parity review
- [x] Kotlin code quality review
- [x] Package migration (`com.anthropic.sdk` → `me.matsumo.claude.agent`)
- [x] Version catalog (`gradle/libs.versions.toml`) 導入
- [x] API streaming events (ApiStreamEvent sealed interface)
- [x] Content block builder DSL (contentBlocks { text(); image(); document() })
- [x] Sub-agent support (SubAgentIdResolver, SubAgentPaths)
- [x] Comprehensive demo suite (20+ demos in `demo/` module)
