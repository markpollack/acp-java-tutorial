# Module 29: JetBrains Integration

> Full tutorial: https://springaicommunity.mintlify.app/acp-java-sdk/tutorial/29-jetbrains-integration

Connect your Java ACP agent to JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.).

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
3. Try prompts like `hello`, `help`, or `tell me about jetbrains`

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

## Next Steps

- **Module 30**: Connect to VS Code using the community extension

## Key Takeaway

The **same agent code** works across all ACP-compatible editors. You built one JAR that works in:
- Zed (Module 28)
- JetBrains (this module)
- VS Code (Module 30)

This is the power of ACP!
