# ACP Java Tutorial

> **Documentation**: https://springaicommunity.mintlify.app/acp-java-sdk/tutorial | [API Reference](https://springaicommunity.mintlify.app/acp-java-sdk/reference/java)

A progressive, hands-on tutorial for the **[Agent Client Protocol (ACP)](https://agentclientprotocol.com/)** Java SDK.

ACP has two sides: the **agent** (what you build and ship) and the **client** (the
IDE/editor that talks to it). The fastest way to *get* ACP is to build an agent and
watch it grow up.

## ⭐ The recommended path — build an agent

> An ACP agent is just a few handlers: `initialize`, `newSession`, `prompt`.
> **The value is what you put in the prompt handler** — echo, a real LLM call, or a
> curated domain workflow. The skeleton never changes; only that one method does.

1. **[Module 12 — Echo Agent](module-12-echo-agent/)** — the whole shape of an ACP
   agent in ~25 lines. No AI, no API key.
2. **[Module 25 — AI Chatbot Agent](module-25-ai-chatbot-agent/)** — the *same*
   agent, but the prompt handler now calls **Claude** and streams a real answer.
   This is the first place in the tutorial where you can point at the line of Java
   that invokes the AI.
3. **[Module 29 — Run it in your IDE](module-29-jetbrains-integration/)** — plug your
   agent into IntelliJ (or [Zed / VS Code](module-29-jetbrains-integration/#other-acp-editors--same-jar-different-config))
   and chat with it inside the editor. The *same JAR* works in all three — only the
   config differs.
4. **Make it real:** [14 sending updates](module-14-sending-updates/) ·
   [15 files & permissions](module-15-agent-requests/) ·
   [18 terminal](module-18-terminal-operations/) ·
   [31 elicitation](module-31-elicitation/).
5. **Ship it:** [23 Spring Boot agent](module-23-spring-boot-agent/) with `@AcpAgent`.

## Prerequisites

- **Java 17+**
- **Maven 3.8+** (or use the included `./mvnw` wrapper)
- **For the chatbot (module 25):** an `ANTHROPIC_API_KEY` — this key *is actually
  used*. Get one at <https://console.anthropic.com/> and `export ANTHROPIC_API_KEY=...`.
- **For the client modules (01–08, 21):** an ACP-capable agent CLI on your `PATH`
  (e.g. `gemini --experimental-acp`, or `claude-code-acp` / `codex-acp`). ACP is
  model-agnostic — point them at any agentic CLI. **The tutorial code never reads an
  API key;** the CLI you launch handles its own model and authentication.

## Getting started

```bash
git clone https://github.com/markpollack/acp-java-tutorial.git
cd acp-java-tutorial
./mvnw compile
```

```bash
# 1) Your first agent — echoes, runs entirely locally, no key
./mvnw package -pl module-12-echo-agent -q
./mvnw exec:java -pl module-12-echo-agent

# 2) The same agent with a brain — calls Claude (needs ANTHROPIC_API_KEY)
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw package -pl module-25-ai-chatbot-agent -q
./mvnw exec:java -pl module-25-ai-chatbot-agent

# 3) Be the client — connect to an existing agent CLI (e.g. gemini)
./mvnw exec:java -pl module-01-first-contact
```

## Module map

### Build an agent — the core path

| Module | Title | What you'll learn |
|--------|-------|-------------------|
| 12 | [Echo Agent](module-12-echo-agent/) | A minimal ACP agent (~25 lines) — the reveal |
| **25** | [**AI Chatbot Agent**](module-25-ai-chatbot-agent/) | **The same agent, but the prompt handler calls Claude and streams** |
| 14 | [Sending Updates](module-14-sending-updates/) | Stream all update types to clients |
| 15 | [Agent Requests](module-15-agent-requests/) | Request files / permissions from the client |
| 18 | [Terminal Operations](module-18-terminal-operations/) | Execute commands via the terminal API |
| 31 | [Elicitation](module-31-elicitation/) | Ask the user for structured input (forms) |
| 23 | [Spring Boot Agent](module-23-spring-boot-agent/) | Ship an agent with `@AcpAgent` (Java 21+) |

> **Portability — same chatbot, any provider:** the module-25 agent rebuilt on the
> two top Java AI frameworks, so the model is a swap-a-dependency choice:
> [Module 26 — Spring AI `ChatClient`](module-26-spring-ai-chatbot/) ·
> [Module 27 — LangChain4j `ChatModel`](module-27-langchain4j-chatbot/). The ACP
> agent never changes — only the line that talks to the model does.

### Run it in your IDE

| Module | Title | What you'll learn |
|--------|-------|-------------------|
| 29 | [Run it in your IDE](module-29-jetbrains-integration/) | Plug your agent into JetBrains, Zed, or VS Code — same JAR, different config |

### Be a client — talk to an existing agent (no API key in the tutorial code)

| Module | Title | What you'll learn |
|--------|-------|-------------------|
| 01 | [First Contact](module-01-first-contact/) | Launch an agent CLI, get your first response |
| 02 | [Protocol Basics](module-02-protocol-basics/) | The `initialize` handshake and capability exchange |
| 03 | [Sessions](module-03-sessions/) | Session creation and lifecycle |
| 04 | [Prompts](module-04-prompts/) | Prompt requests and response handling |
| 05 | [Streaming Updates](module-05-streaming-updates/) | Receive real-time updates during prompts |
| 06 | [Update Types](module-06-update-types/) | All `SessionUpdate` types in depth |
| 07 | [Agent Requests](module-07-agent-requests/) | Respond to file read/write requests |
| 08 | [Permissions](module-08-permissions/) | Handle permission requests from agents |

### Production & advanced

| Module | Title | What you'll learn |
|--------|-------|-------------------|
| 09 | [Session Resume](module-09-session-resume/) | Load and resume existing sessions |
| 10 | [Cancellation](module-10-cancellation/) | Cancel in-progress operations |
| 11 | [Error Handling](module-11-error-handling/) | Handle protocol errors |
| 13 | [Agent Handlers](module-13-agent-handlers/) | All handler types, in one place |
| 16 | [In-Memory Testing](module-16-in-memory-testing/) | Test client + agent without subprocesses |
| 17 | [Capability Negotiation](module-17-capability-negotiation/) | Advertise and check capabilities |
| 19 | [MCP Servers](module-19-mcp-servers/) | Pass MCP server configs to agents |
| 20 | [Session Management](module-20-session-management/) | List, resume, and close sessions |
| 21 | [Async Client](module-21-async-client/) | Reactive client with `Mono` |
| 22 | [Async Agent](module-22-async-agent/) | Build agents with `AcpAgent.async()` |
| 24 | [Spring Boot Client](module-24-spring-boot-client/) | Autoconfigured `AcpSyncClient` |

## Error handling in handlers

When implementing file or permission handlers, **throw exceptions for errors**. The SDK automatically converts exceptions to proper JSON-RPC error responses.

```java
// CORRECT: Throw exceptions - SDK converts to JSON-RPC errors
.readTextFileHandler(req -> {
    if (!Files.exists(Path.of(req.path()))) {
        throw new RuntimeException("File not found: " + req.path());
    }
    return new ReadTextFileResponse(Files.readString(Path.of(req.path())));
})
```

**Why throw exceptions?** Errors belong in the JSON-RPC `error` field, not `result`;
agents get proper error codes; and it's consistent with the Kotlin and Python SDKs.

## Build commands

```bash
./mvnw compile                              # build everything
./mvnw compile -pl module-12-echo-agent     # build one module
./mvnw package -pl module-25-ai-chatbot-agent -q   # package an agent (before running)
./mvnw test                                 # run tests
```

## Integration testing

The tutorial includes an automated suite (deterministic output checks + an AI judge):

```bash
cd integration-testing
./scripts/run-integration-tests.sh --local   # local tests, no keys
jbang RunIntegrationTest.java module-25-ai-chatbot-agent   # needs ANTHROPIC_API_KEY
./scripts/run-integration-tests.sh            # everything
```

## Related projects

- [ACP Java SDK](https://github.com/agentclientprotocol/java-sdk) — the SDK this tutorial teaches
- [ACP Java SDK Documentation](https://springaicommunity.mintlify.app/acp-java-sdk) — full docs + API reference
- [Agent Client Protocol](https://agentclientprotocol.com) — the official specification
- **Other ACP SDKs:** [Kotlin](https://github.com/agentclientprotocol/kotlin-sdk) | [Python](https://github.com/agentclientprotocol/python-sdk) | [TypeScript](https://github.com/agentclientprotocol/typescript-sdk) | [Rust](https://github.com/agentclientprotocol/rust-sdk)
