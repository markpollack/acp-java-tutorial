# Module 29: JetBrains Integration

Connect your Java ACP agent to JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.).

## Overview

JetBrains joined the ACP initiative in October 2025, collaborating with Zed to create a unified standard rather than competing protocols. ACP support is available in IDE versions 25.3 RC and later.

**Key insight**: The same agent JAR works in Zed, JetBrains, and VS Code. Only the configuration differs!

## Prerequisites

1. **JetBrains IDE** version 25.3 RC or later (IntelliJ, PyCharm, WebStorm, etc.)
2. **JetBrains AI Assistant** plugin enabled
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

1. Open your JetBrains IDE
2. Open the **AI Chat** tool window (`Alt+Shift+C` or via View → Tool Windows → AI Chat)
3. Click the **gear icon** ⚙️
4. Select **Configure ACP Agents**
5. The `acp.json` file will open

### Option B: Edit Manually

Create or edit `~/.jetbrains/acp.json`:

```json
{
  "agent_servers": {
    "Java Tutorial Agent": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/jetbrains-agent.jar"]
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

## Configuration Options

### Basic Configuration

```json
{
  "agent_servers": {
    "Java Tutorial Agent": {
      "command": "java",
      "args": ["-jar", "/path/to/jetbrains-agent.jar"]
    }
  }
}
```

### With Environment Variables

```json
{
  "agent_servers": {
    "Java Tutorial Agent": {
      "command": "java",
      "args": ["-jar", "/path/to/jetbrains-agent.jar"],
      "env": {
        "JAVA_OPTS": "-Xmx512m",
        "MY_API_KEY": "your-key-here"
      }
    }
  }
}
```

### With IDE MCP Server Access

Let your agent access the IDE's built-in MCP server:

```json
{
  "agent_servers": {
    "Java Tutorial Agent": {
      "command": "java",
      "args": ["-jar", "/path/to/jetbrains-agent.jar"],
      "use_idea_mcp": true,
      "use_custom_mcp": true
    }
  }
}
```

### Windows with WSL

```json
{
  "agent_servers": {
    "Java Tutorial Agent": {
      "command": "java",
      "args": ["-jar", "/mnt/c/path/to/jetbrains-agent.jar"],
      "execution_environment": {
        "type": "wsl",
        "distribution": "Ubuntu"
      }
    }
  }
}
```

## Supported JetBrains IDEs

| IDE | Status |
|-----|--------|
| IntelliJ IDEA | ✅ Supported (25.3 RC+) |
| PyCharm | ✅ Supported (25.3 RC+) |
| WebStorm | ✅ Supported (25.3 RC+) |
| GoLand | ✅ Supported (25.3 RC+) |
| PhpStorm | ✅ Supported (25.3 RC+) |
| Rider | ✅ Supported (25.3 RC+) |
| CLion | ✅ Supported (25.3 RC+) |
| RubyMine | ✅ Supported (25.3 RC+) |
| DataGrip | ✅ Supported (25.3 RC+) |

## Troubleshooting

### Agent Not Appearing

1. Verify IDE version is 25.3 RC or later
2. Ensure AI Assistant plugin is enabled
3. Check `acp.json` is valid JSON
4. Restart the IDE after configuration changes

### Connection Issues

Check the agent logs:
- Linux/Mac: `~/.cache/JetBrains/<IDE>/log/idea.log`
- Windows: `%LOCALAPPDATA%\JetBrains\<IDE>\log\idea.log`

### Test Agent Manually

```bash
java -jar module-29-jetbrains-integration/target/jetbrains-agent.jar
# Should print:
# [JetBrainsAgent] Starting Java ACP agent...
# [JetBrainsAgent] Ready - waiting for IDE to connect...
```

## Next Steps

- **Module 30**: Connect to VS Code using the community extension
- **Module 31**: Build a Claude-powered agent

## Key Takeaway

The **same agent code** works across all ACP-compatible editors. You built one JAR that works in:
- Zed (Module 28)
- JetBrains (this module)
- VS Code (Module 30)

This is the power of ACP!
