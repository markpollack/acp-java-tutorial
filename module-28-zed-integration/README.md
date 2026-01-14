# Module 28: Zed Integration

Connect your Java ACP agent to the [Zed](https://zed.dev) editor.

## Overview

Zed was the first editor with full ACP support, developed in collaboration with Google's Gemini CLI team. This module shows how to configure Zed to use your Java-based ACP agent.

**Key insight**: The same agent code works whether launched from a Java client, Zed, JetBrains IDEs, or any ACP-compatible client. That's the power of ACP!

## Prerequisites

1. **Zed editor** installed ([download](https://zed.dev/download))
2. **Java 17+** installed
3. **This module built** (see below)

## Step 1: Build the Agent JAR

```bash
# From the tutorial root directory
./mvnw package -pl module-28-zed-integration -q

# Verify the JAR was created
ls -la module-28-zed-integration/target/zed-agent.jar
```

## Step 2: Test the Agent Locally (Optional)

Before configuring Zed, you can test the agent works:

```bash
# Start the agent (it will wait for JSON-RPC input)
java -jar module-28-zed-integration/target/zed-agent.jar
```

You should see:
```
[ZedAgent] Starting Java ACP agent...
[ZedAgent] Ready - waiting for Zed to connect...
```

Press `Ctrl+C` to stop.

## Step 3: Configure Zed

### 3.1 Find the Absolute Path to Your JAR

```bash
# Get the absolute path
realpath module-28-zed-integration/target/zed-agent.jar
```

Example output: `/home/user/projects/acp-java-tutorial/module-28-zed-integration/target/zed-agent.jar`

### 3.2 Edit Zed Settings

Open Zed and press `Cmd+,` (Mac) or `Ctrl+,` (Linux) to open settings.

Add your agent configuration:

```json
{
  "agent_servers": {
    "Java Tutorial Agent": {
      "type": "custom",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/zed-agent.jar"]
    }
  }
}
```

**Important**: Replace `/absolute/path/to/zed-agent.jar` with the actual path from step 3.1.

### 3.3 Optional: Add Environment Variables

If your agent needs environment variables (like API keys):

```json
{
  "agent_servers": {
    "Java Tutorial Agent": {
      "type": "custom",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/zed-agent.jar"],
      "env": {
        "MY_API_KEY": "your-key-here"
      }
    }
  }
}
```

## Step 4: Use the Agent in Zed

1. Open the **Agent Panel**: Press `Ctrl+?` (Linux) or `Cmd+?` (Mac)
2. Click the **+** button in the top right
3. Select **"Java Tutorial Agent"** from the list
4. Start chatting!

### Example Conversation

```
You: Hello!

Agent: Hello! I'm a Java ACP agent running in Zed.
       I was built with the ACP Java SDK. How can I help you?

You: What is ACP?

Agent: ACP (Agent Client Protocol) is an open standard for connecting
       AI coding agents to editors and IDEs.

       Think of it like LSP (Language Server Protocol), but for AI agents.

       With ACP, you write your agent once and it works with:
       - Zed
       - JetBrains IDEs (IntelliJ, PyCharm, etc.)
       - VS Code (via community extension)
       - Neovim
       - And more!
```

## How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                         Zed Editor                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   Agent Panel                        │   │
│  │  You: Hello!                                        │   │
│  │  Agent: Hello! I'm a Java ACP agent...             │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                    JSON-RPC over stdio                      │
│                           │                                 │
└───────────────────────────┼─────────────────────────────────┘
                            │
                            ▼
               ┌────────────────────────┐
               │   java -jar zed-agent  │
               │   (Your Java agent)    │
               └────────────────────────┘
```

1. Zed launches your agent as a subprocess
2. Communication happens via JSON-RPC over stdio (stdin/stdout)
3. Your agent receives prompts and sends responses
4. Zed displays the responses in the Agent Panel

## Troubleshooting

### Agent Doesn't Appear in Zed

1. Check your `settings.json` syntax is valid JSON
2. Verify the JAR path is absolute (starts with `/`)
3. Restart Zed after changing settings

### Agent Starts but No Response

1. Check stderr output: `java -jar zed-agent.jar 2>&1 | head`
2. Ensure Java 17+ is installed: `java -version`
3. Look for exceptions in Zed's developer console

### Check Agent Logs

Agent logs go to stderr. To see them while Zed runs the agent, check Zed's logs:
- Mac: `~/Library/Logs/Zed/`
- Linux: `~/.local/share/zed/logs/`

## Next Steps

- **Module 29**: Connect this same agent to JetBrains IDEs
- **Module 30**: Connect to VS Code using the community extension
- **Module 31**: Build a more sophisticated Claude-powered agent

## Key Takeaway

**Write once, run everywhere**: This agent works with any ACP-compatible editor. The same JAR file can be configured in Zed, JetBrains, or VS Code without any code changes.
