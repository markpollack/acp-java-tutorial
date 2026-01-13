# ACP Java Tutorial

A progressive, hands-on tutorial for learning the **Agent Client Protocol (ACP)** using the Java SDK.

## Prerequisites

- Java 17+
- Maven 3.8+
- Gemini CLI with ACP support (`gemini --experimental-acp`)

## Quick Start

```bash
# Run Module 01 - First Contact with Gemini CLI
mvn exec:java -pl module-01-first-contact
```

## Tutorial Structure

### Part 1: First Steps (Client Basics)
| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 01 | First Contact | Launch Gemini CLI, get your first response |
| 02 | Protocol Basics | Initialize handshake, version negotiation |
| 03 | Sessions | Create sessions, understand session lifecycle |
| 04 | Prompts | Send prompts, handle responses |

### Part 2: Client Features
| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 05 | Streaming Updates | Receive real-time updates during prompts |
| 06 | Update Types | Handle different update types (thoughts, messages, tools) |
| 07 | Agent Requests | Respond to file read/write requests |
| 08 | Permissions | Handle permission requests from agents |

### Part 3: Client Mastery
| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 09 | Session Resume | Load and resume existing sessions |
| 10 | Cancellation | Cancel in-progress operations |
| 11 | Error Handling | Handle protocol errors gracefully |

### Part 4: The Agent Side (The Reveal)
| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 12 | Echo Agent | Build a minimal ACP agent (~30 lines) |
| 13 | Agent Handlers | Implement all handler types |
| 14 | Sending Updates | Stream updates to clients |
| 15 | Agent Requests | Request files/permissions from clients |

### Part 5: Both Ends Together
| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 16 | In-Memory Testing | Test client-agent without subprocesses |
| 17 | Capability Negotiation | Feature detection and graceful degradation |
| 18 | Terminal Operations | Agent executes commands via client |

### Part 6: Advanced Integration
| Module | Title | What You'll Learn |
|--------|-------|-------------------|
| 19 | MCP Servers | Pass MCP servers to agents |
| 20 | WebSocket Transport | Network-based ACP communication |
| 21 | Async Patterns | Reactive programming with Mono/Flux |

### Capstone: Claude Code Agent
Build a fully functional ACP agent that wraps the Claude Agent SDK Java.

## Running Individual Modules

Each module is a standalone Maven project:

```bash
# Run any module
mvn exec:java -pl module-XX-name

# Example: Run the Echo Agent
mvn exec:java -pl module-12-echo-agent
```

## Building

```bash
# Build all modules
mvn compile

# Test all modules
mvn test
```

## Related Projects

- [ACP Java SDK](https://github.com/agentclientprotocol/acp-java) - The SDK this tutorial teaches
- [Claude Agent SDK Java](https://github.com/springaicommunity/claude-agent-sdk-java) - Used in capstone
- [Agent Client Protocol](https://agentclientprotocol.com) - Official ACP documentation
