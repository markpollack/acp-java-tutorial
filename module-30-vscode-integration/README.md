# Module 30: VS Code Integration

> Full tutorial: https://springaicommunity.mintlify.app/acp-java-sdk/tutorial/30-vscode-integration

Connect your Java ACP agent to VS Code using the community [vscode-acp](https://marketplace.visualstudio.com/items?itemName=omercnet.vscode-acp) extension.

## Overview

VS Code doesn't have native ACP support yet ([Issue #265496](https://github.com/microsoft/vscode/issues/265496) is under discussion), but the community extension by omercnet provides full ACP functionality.

**Key insight**: The same agent JAR works in Zed, JetBrains, and VS Code!

## Prerequisites

1. **VS Code** installed
2. **vscode-acp extension** installed (see Step 1)
3. **Java 17+** installed
4. **This module built** (see Step 2)

## Step 1: Install the vscode-acp Extension

1. Open VS Code
2. Press `Ctrl+Shift+X` to open Extensions
3. Search for "**VSCode ACP**" by omercnet
4. Click **Install**

Or install from command line:
```bash
code --install-extension omercnet.vscode-acp
```

## Step 2: Build the Agent JAR

```bash
# From the tutorial root directory
./mvnw package -pl module-30-vscode-integration -q

# Verify the JAR was created
ls -la module-30-vscode-integration/target/vscode-agent.jar
```

## Step 3: Create a Wrapper Script

The vscode-acp extension auto-detects agents from your PATH. Create a wrapper script:

### Linux/macOS

```bash
# Create the wrapper script
cat > ~/.local/bin/java-tutorial-agent << 'EOF'
#!/bin/bash
exec java -jar /absolute/path/to/vscode-agent.jar "$@"
EOF

# Make it executable
chmod +x ~/.local/bin/java-tutorial-agent

# Add to PATH if needed (add to ~/.bashrc or ~/.zshrc)
export PATH="$HOME/.local/bin:$PATH"
```

**Important**: Replace `/absolute/path/to/vscode-agent.jar` with:
```bash
realpath module-30-vscode-integration/target/vscode-agent.jar
```

### Windows (PowerShell)

Create `%USERPROFILE%\bin\java-tutorial-agent.cmd`:
```cmd
@echo off
java -jar C:\path\to\vscode-agent.jar %*
```

Add `%USERPROFILE%\bin` to your PATH environment variable.

## Step 4: Verify the Script Works

```bash
# Test the wrapper script
java-tutorial-agent
# Should print:
# [VSCodeAgent] Starting Java ACP agent...
# [VSCodeAgent] Ready - waiting for VS Code to connect...

# Press Ctrl+C to stop
```

## Step 5: Use in VS Code

1. Click the **VSCode ACP** icon in the Activity Bar (sidebar)
2. Click **Connect**
3. Select your agent from the dropdown
4. Start chatting!

## How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                        VS Code                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │               vscode-acp Extension                   │   │
│  │  You: Hello!                                        │   │
│  │  Agent: Hello from VS Code! I'm a Java ACP agent... │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                    Finds agent in PATH                      │
│                           │                                 │
└───────────────────────────┼─────────────────────────────────┘
                            │
                            ▼
               ┌────────────────────────┐
               │  java-tutorial-agent   │
               │  (wrapper script)      │
               │         │              │
               │         ▼              │
               │  java -jar agent.jar   │
               └────────────────────────┘
```

## Extension Features

The vscode-acp extension provides:

| Feature | Description |
|---------|-------------|
| **Multi-Agent Support** | Connect to multiple ACP agents |
| **Native Chat Interface** | Integrated sidebar chat |
| **Tool Visibility** | Expandable tool execution details |
| **Streaming** | Real-time response streaming |
| **Rich Markdown** | Syntax highlighting in responses |

## Troubleshooting

### Agent Not Detected

1. Verify the wrapper script is in PATH:
   ```bash
   which java-tutorial-agent
   ```
2. Verify script is executable:
   ```bash
   ls -la ~/.local/bin/java-tutorial-agent
   ```
3. Restart VS Code after adding to PATH

### Extension Not Working

1. Check extension is enabled in Extensions panel
2. Look for errors in VS Code Developer Console (`Help → Toggle Developer Tools`)
3. Try reinstalling the extension

### Connection Issues

1. Test the agent manually first:
   ```bash
   java-tutorial-agent
   ```
2. Check Java is accessible:
   ```bash
   java -version
   ```

## Comparison: Three IDEs, One Agent

| IDE | Configuration Method | Native Support |
|-----|---------------------|----------------|
| **Zed** | `settings.json` | ✅ Yes |
| **JetBrains** | `acp.json` | ✅ Yes (25.3 RC+) |
| **VS Code** | PATH + extension | ⏳ Community only |

The same `vscode-agent.jar` can be configured in all three editors!

## Native VS Code Support

Microsoft is tracking ACP support in [Issue #265496](https://github.com/microsoft/vscode/issues/265496). The current status is "under discussion."

If/when native support arrives, configuration would likely follow a similar pattern to Zed's `settings.json` approach.

## Next Steps

- **Module 31**: Build a Claude-powered ACP agent
- Review Modules 28-29 to see the same agent in Zed and JetBrains

## Key Takeaway

**Write once, configure anywhere**: This Java agent works in Zed, JetBrains IDEs, and VS Code with zero code changes. That's the power of ACP!
