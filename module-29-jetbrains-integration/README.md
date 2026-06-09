# Module 29: Run It in Your IDE (JetBrains, Zed, VS Code)

> Full tutorial: https://springaicommunity.mintlify.app/acp-java-sdk/tutorial/29-jetbrains-integration

Connect your Java ACP agent to JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.) — and, with the same JAR, to Zed and VS Code (see [Other ACP editors](#other-acp-editors--same-jar-different-config) below).

## Overview

JetBrains joined the ACP initiative in October 2025, collaborating with Zed to create a unified standard rather than competing protocols. ACP support is available in IDE versions 2026.1 and later.

**Key insight**: The same agent JAR works in Zed, JetBrains, and VS Code. Only the configuration differs!

## Prerequisites

1. **JetBrains IDE** version 2026.1 or later (IntelliJ, PyCharm, WebStorm, etc.)
2. **JetBrains AI Assistant** plugin enabled (no AI subscription required)
3. **Java 17+** installed
4. **This module built** (see below)

## Step 1: Build the Agent JAR

```bash
# From the tutorial root directory
./mvnw package -pl module-29-jetbrains-integration -q

# Verify the JAR was created
ls -la module-29-jetbrains-integration/target/jetbrains-agent.jar
```

## Step 2: Get the Absolute JAR Path

```bash
realpath module-29-jetbrains-integration/target/jetbrains-agent.jar
```

Example: `/home/user/projects/acp-java-tutorial/module-29-jetbrains-integration/target/jetbrains-agent.jar`

## Step 3: Configure JetBrains

### Option A: Using the IDE (Recommended)

Follow the official JetBrains ACP documentation for the current UI flow:

https://www.jetbrains.com/help/ai-assistant/acp.html#add-custom-agent

The JetBrains UI may change over time, so this tutorial intentionally avoids listing menu paths that can become outdated.

### Option B: Edit Manually

Create or edit `~/.jetbrains/acp.json`:

```json
{
  "default_mcp_settings": {},
  "agent_servers": {
    "Java Tutorial Agent": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/jetbrains-agent.jar",
        "acp"
      ]
    }
  }
}
```

**Replace** `/absolute/path/to/jetbrains-agent.jar` with the path from Step 2.

## Step 4: Use the Agent

1. Open the **AI Chat** tool window
2. Click the **agent selector** dropdown
3. Select **"Java Tutorial Agent"**
4. Start chatting!

## Chat with the Agent

1. Open AI Chat (Alt + Shift + C)
2. Select "Java Tutorial Agent" from the agent dropdown
3. Try prompts like `hello`, `help`, `tour`, or `tell me about jetbrains`

## Follow-Along: Reporting File Locations

ACP has no agent-to-client "open this file" request. Instead, tool call
updates carry a **`locations`** field, and clients that support follow-along
navigate to each location as it arrives. The spec calls this
["Following the Agent"](https://agentclientprotocol.com/protocol/tool-calls#following-the-agent):

> Tool calls can report file locations they're working with, enabling Clients
> to implement "follow-along" features that track which files the Agent is
> accessing or modifying in real-time.

Each location is an absolute `path` plus an optional 1-based `line`:

```java
context.sendUpdate(sessionId, new ToolCall(
    "tool_call", toolCallId,
    "Visiting pom.xml",
    ToolKind.READ, ToolCallStatus.IN_PROGRESS,
    List.of(),
    List.of(new ToolCallLocation("/abs/path/to/pom.xml", 1)),
    null, null, null));
```

### Try It

Say **`tour`** to the agent. It walks well-known files in the project
(`pom.xml`, `README.md`, ...), reporting each as a tool call location —
opening the file at line 1, then scrolling to the middle via a
`tool_call_update`. See `tourProject()` in `JetBrainsAgent.java`.

Client support differs:

| Client | Behavior |
|--------|----------|
| **Zed** | Click the **crosshair icon** at the bottom left of the Agent Panel (or hold `cmd`/`ctrl` when submitting). The editor jumps to every location the agent reports. |
| **JetBrains** | Locations render on the tool call cards in AI Chat. No auto-follow toggle yet (as of June 2026) — report locations anyway, so your agent is ready when it ships. |

Two things `locations` is **not**:

- Not `fs/read_text_file` / `fs/write_text_file` (`context.readFile()` /
  `context.writeFile()`) — those are request/response calls that access the
  editor's buffer state (including unsaved changes); they don't navigate.
- Not imperative — it's a one-way notification. The editor follows only if
  the user enabled follow mode; there is no guaranteed "force open" in ACP.

## Troubleshooting

### Agent Not Appearing

1. Verify IDE version is 2026.1 or later
2. Ensure AI Assistant plugin is enabled
3. Check `acp.json` is valid JSON
4. Restart the IDE after configuration changes
5. ACP agents are not supported in WSL

### Connection Issues

In the AI Chat tool window, click the more options button and select **"Get ACP Logs"** to download agent logs. For detailed diagnostics, enable the `llm.agent.extended.logging` registry key via Navigate > Search Everywhere.

### Test Agent Manually

```bash
java -jar module-29-jetbrains-integration/target/jetbrains-agent.jar
# Should print:
# [JetBrainsAgent] Starting Java ACP agent...
# [JetBrainsAgent] Ready - waiting for IDE to connect...
```

## Other ACP editors — same JAR, different config

The **same agent JAR** works in any ACP client; only the configuration differs.

### Zed

Zed was the first editor with full ACP support. Open Zed settings (`Cmd/Ctrl+,`) and add:

```json
{
  "agent_servers": {
    "Java Tutorial Agent": {
      "type": "custom",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/jetbrains-agent.jar"]
    }
  }
}
```

Pick the agent in Zed's Agent Panel. Hold `cmd`/`ctrl` when submitting (or click the
crosshair icon) to enable follow-along navigation.

### VS Code

VS Code has no native ACP support yet, but the community
[vscode-acp](https://marketplace.visualstudio.com/items?itemName=omercnet.vscode-acp)
extension provides it. Install it (`code --install-extension omercnet.vscode-acp`),
then expose the agent on your `PATH` with a wrapper script — the extension
auto-detects agents from `PATH`:

```bash
cat > ~/.local/bin/java-tutorial-agent <<'EOF'
#!/bin/bash
exec java -jar /absolute/path/to/jetbrains-agent.jar "$@"
EOF
chmod +x ~/.local/bin/java-tutorial-agent
```

## Key Takeaway

The **same agent code** works across every ACP-compatible editor — JetBrains, Zed,
and VS Code all launch the one JAR you built. Write once, run in any IDE. That's the
power of ACP.
