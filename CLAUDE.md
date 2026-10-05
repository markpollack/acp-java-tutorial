# ACP Java Tutorial - Claude Code Context

This is the tutorial repository for the ACP (Agent Client Protocol) Java SDK.

## Project Structure

```
acp-java-tutorial/
├── module-01-first-contact/     # Client basics - connect to the Grok CLI
├── module-05-streaming-updates/ # Receive session updates
├── module-07-agent-requests/    # Client-side file handlers
├── module-08-permissions/       # Permission handling
├── module-12-echo-agent/        # Minimal echo agent (the "reveal")
├── module-25-ai-chatbot-agent/  # Echo agent + a real LLM (the "juicier" reveal)
├── module-14-sending-updates/   # Agent sends all update types
├── module-15-agent-requests/    # Agent requests files/permissions
├── module-16-in-memory-testing/ # In-memory transport testing
├── integration-testing/         # JBang-based integration tests
├── plans/                       # Design docs (ROADMAP.md, LEARNINGS.md)
└── pom.xml                      # Parent POM
```

## Key Documentation

- `plans/ROADMAP.md` - Master roadmap, module status, current sprint
- `plans/LEARNINGS.md` - Implementation patterns and common mistakes
- `plans/SDK-ISSUES.md` - SDK issues encountered (all resolved)

## Build & Run

```bash
# Build all modules
./mvnw compile

# Run a client module (e.g., first-contact with Grok)
./mvnw exec:java -pl module-01-first-contact

# Build & run an agent module (requires package first)
./mvnw package -pl module-12-echo-agent -q
./mvnw exec:java -pl module-12-echo-agent
```

## Module Categories

### Client Modules (01-08)
Connect to the Grok CLI (`grok agent stdio`) or other ACP agents. Require the Grok CLI on
the `PATH`, signed in once with `grok login`; no API key environment variable.

### Agent Modules (12-15)
Implement ACP agents. Each has:
- `XxxAgent.java` - The agent (packaged as JAR via shade plugin)
- `XxxAgentDemo.java` - Demo client that launches agent as subprocess

### Test Modules (16+)
In-memory testing without external processes.

## SDK Location

The ACP Java SDK is at `/home/mark/acp/acp-java`. Key files:
- `acp-core/src/main/java/com/agentclientprotocol/sdk/client/AcpClient.java`
- `acp-core/src/main/java/com/agentclientprotocol/sdk/agent/AcpAgent.java`
- `acp-core/src/main/java/com/agentclientprotocol/sdk/spec/AcpSchema.java`

## Important Patterns

### Sync Agent (Recommended)
```java
AcpSyncAgent agent = AcpAgent.sync(transport)
    .initializeHandler(req -> InitializeResponse.ok())
    .newSessionHandler(req -> new NewSessionResponse(...))
    .promptHandler((req, context) -> {
        context.sendMessage("response text");  // blocking convenience method
        return PromptResponse.endTurn();
    })
    .build();
agent.run();  // blocks until client disconnects
```

### Sync Client
```java
try (AcpSyncClient client = AcpClient.sync(transport)
        .sessionUpdateHandler(notification -> { /* handle */ })
        .build()) {
    client.initialize();
    var session = client.newSession(new NewSessionRequest(cwd, List.of()));
    var response = client.prompt(new PromptRequest(session.sessionId(), content));
}
```

### Agent Self-Reference
Request handlers other than the prompt handler can take the built agent as a second
parameter; the prompt handler reaches the client through its context:
```java
AcpSyncAgent agent = AcpAgent.sync(transport)
    .loadSessionHandler((req, self) -> {
        self.sendSessionUpdate(req.sessionId(), update);  // replay history
        return new LoadSessionResponse(null);
    })
    .promptHandler((req, context) -> {
        String content = context.readFile("pom.xml");             // convenience
        var perm = context.client().requestPermission(request);   // raw ACP request
        // ...
    })
    .build();
agent.run();
```
Extension handlers have agent-aware forms too: `extRequestHandler(method, [TypeRef,] (params, self) -> ...)`
and `extNotificationHandler(...)`. A prompt handler calls the client's extension methods with
`context.client().sendExtRequest(...)` / `sendExtNotification(...)` (module 34).

### Robust JAR Path Resolution
Agent demos should work from repo root OR module directory:
```java
private static String findAgentJar(String moduleName, String jarName) {
    Path fromModule = Path.of("target/" + jarName);
    if (Files.exists(fromModule)) return fromModule.toString();
    Path fromRoot = Path.of(moduleName + "/target/" + jarName);
    if (Files.exists(fromRoot)) return fromRoot.toString();
    throw new RuntimeException("JAR not found. Run: ./mvnw package -pl " + moduleName);
}
```

## Current Work (January 2026)

Code Review Sprint in progress. See `plans/ROADMAP.md` section "Code Review & Refactoring Sprint" for:
- P0: JAR path robustness (modules 12-15)
- P1: Error handling investigation (module 07)
- P2: Static state refactoring (module 06)
- P2: AtomicReference documentation (module 15)
- P3: Test utilities SDK enhancement (module 16)

## Integration Testing

```bash
cd integration-testing

# Run single test
jbang RunIntegrationTest.java module-12-echo-agent

# Run all local tests (no API key)
./scripts/run-integration-tests.sh --local

# Run all tests
./scripts/run-integration-tests.sh
```

## Common Commands

```bash
# Check module status
./mvnw compile -pl module-XX-name

# Run integration test for a module
cd integration-testing && jbang RunIntegrationTest.java module-XX-name

# View test logs
ls integration-testing/logs/

# Git status
git status
```
