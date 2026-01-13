# ACP Java Tutorial

A progressive, hands-on tutorial for learning the **Agent Client Protocol (ACP)** using the Java SDK.

## Prerequisites

- Java 21+
- Maven 3.8+ (or use the included `./mvnw` wrapper)
- For client modules (01-08): Gemini CLI with ACP support and API key

## Getting Started

### Step 1: Build All Modules

```bash
# Clone and build
git clone https://github.com/markpollack/acp-java-tutorial.git
cd acp-java-tutorial
./mvnw compile
```

### Step 2: Run Local Agent Demos (No API Key Required)

These modules run entirely locally - no external API needed:

```bash
# Module 12: Echo Agent - Your first ACP agent
./mvnw package -pl module-12-echo-agent -q
./mvnw exec:java -pl module-12-echo-agent

# Module 14: All session update types
./mvnw package -pl module-14-sending-updates -q
./mvnw exec:java -pl module-14-sending-updates

# Module 15: Agent requests files from client
./mvnw package -pl module-15-agent-requests -q
./mvnw exec:java -pl module-15-agent-requests

# Module 16: In-memory testing (no subprocess)
./mvnw exec:java -pl module-16-in-memory-testing
```

### Step 3: Set Up Gemini API Key (For Client Modules)

Client modules (01-08) connect to Gemini CLI, which requires an API key:

```bash
# Get your API key from https://aistudio.google.com/app/apikey
export GEMINI_API_KEY=your-api-key-here

# Verify Gemini CLI is installed with ACP support
gemini --experimental-acp --version
```

### Step 4: Run Client Modules

```bash
# Module 01: First Contact - Connect to Gemini
./mvnw exec:java -pl module-01-first-contact

# Module 05: Streaming Updates
./mvnw exec:java -pl module-05-streaming-updates

# Module 07: Handle agent file requests
./mvnw exec:java -pl module-07-agent-requests

# Module 08: Handle permission requests
./mvnw exec:java -pl module-08-permissions
```

## Module Categories

### Client Modules (Require GEMINI_API_KEY)

| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 01 | First Contact | Launch Gemini CLI, get your first response |
| 05 | Streaming Updates | Receive real-time updates during prompts |
| 07 | Agent Requests | Respond to file read/write requests |
| 08 | Permissions | Handle permission requests from agents |

### Agent Modules (Run Locally, No API Key)

| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 12 | Echo Agent | Build a minimal ACP agent (~30 lines) |
| 13 | Agent Handlers | Implement all handler types |
| 14 | Sending Updates | Stream updates to clients |
| 15 | Agent Requests | Request files/permissions from clients |
| 16 | In-Memory Testing | Test client-agent without subprocesses |

### Coming Soon

| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 02-04 | Protocol Basics | Initialize, sessions, prompts in depth |
| 06 | Update Types | All SessionUpdate types |
| 09-11 | Client Mastery | Resume, cancel, error handling |
| 17-21 | Advanced | Capabilities, terminal, WebSocket, async |

## Error Handling in Handlers

When implementing file or permission handlers, **throw exceptions for errors**. The SDK automatically converts exceptions to proper JSON-RPC error responses.

```java
// CORRECT: Throw exceptions - SDK converts to JSON-RPC errors
.readTextFileHandler(req -> {
    if (!Files.exists(Path.of(req.path()))) {
        throw new RuntimeException("File not found: " + req.path());
    }
    return new ReadTextFileResponse(Files.readString(Path.of(req.path())));
})

// WRONG: Don't return error strings as content
.readTextFileHandler(req -> {
    if (!Files.exists(Path.of(req.path()))) {
        return new ReadTextFileResponse("[ERROR: File not found]");  // BAD!
    }
    // ...
})
```

**Why throw exceptions?**
- Follows JSON-RPC 2.0 spec (errors belong in `error` field, not `result`)
- Agents receive proper error codes for programmatic handling
- Prevents agents from misinterpreting error strings as file content
- Consistent with Kotlin and Python ACP SDKs

## Build Commands

```bash
# Build everything
./mvnw compile

# Build a specific module
./mvnw compile -pl module-12-echo-agent

# Package agent modules (required before running)
./mvnw package -pl module-12-echo-agent -q

# Run tests
./mvnw test
```

## Integration Testing

The tutorial includes an automated test suite:

```bash
cd integration-testing

# Run all local tests (no API key needed)
./scripts/run-integration-tests.sh --local

# Run a single module test
jbang RunIntegrationTest.java module-12-echo-agent

# Run all tests (requires GEMINI_API_KEY)
./scripts/run-integration-tests.sh
```

## Project Structure

```
acp-java-tutorial/
├── module-01-first-contact/     # Client: Connect to Gemini
├── module-05-streaming-updates/ # Client: Real-time updates
├── module-07-agent-requests/    # Client: File handlers
├── module-08-permissions/       # Client: Permission handling
├── module-12-echo-agent/        # Agent: Minimal echo agent
├── module-13-agent-handlers/    # Agent: All handler types
├── module-14-sending-updates/   # Agent: Send all update types
├── module-15-agent-requests/    # Agent: Request files
├── module-16-in-memory-testing/ # Testing: No subprocess
├── integration-testing/         # Automated test suite
└── plans/                       # Design documentation
```

## Related Projects

- [ACP Java SDK](https://github.com/agentclientprotocol/acp-java) - The SDK this tutorial teaches
- [Claude Agent SDK Java](https://github.com/springaicommunity/claude-agent-sdk-java) - Used in capstone
- [Agent Client Protocol](https://agentclientprotocol.com) - Official ACP documentation
