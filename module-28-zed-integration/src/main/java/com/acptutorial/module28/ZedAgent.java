/*
 * Module 28: Zed Integration
 *
 * This module demonstrates how to connect a Java ACP agent to the Zed editor.
 * Zed was the first editor with full ACP support, developed in collaboration
 * with Google's Gemini CLI team.
 *
 * This agent:
 * 1. Responds to prompts with helpful messages
 * 2. Can read files from the workspace (via ACP file system capability)
 * 3. Streams responses as "thoughts" and "messages"
 *
 * Key concept: The SAME agent code works whether launched from:
 * - A Java client (like our tutorial demos)
 * - Zed editor
 * - JetBrains IDEs
 * - Any ACP-compatible client
 *
 * Build:
 *   ./mvnw package -pl module-28-zed-integration -q
 *
 * Test locally (before Zed):
 *   java -jar module-28-zed-integration/target/zed-agent.jar
 *   (Then send JSON-RPC messages on stdin - see README.md)
 *
 * Configure Zed - add to ~/.config/zed/settings.json:
 *   {
 *     "agent_servers": {
 *       "Java Tutorial Agent": {
 *         "type": "custom",
 *         "command": "java",
 *         "args": ["-jar", "/absolute/path/to/zed-agent.jar"]
 *       }
 *     }
 *   }
 */
package com.acptutorial.module28;

import java.util.List;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentThoughtChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class ZedAgent {

    public static void main(String[] args) {
        // Log to stderr (Zed captures stdout for ACP protocol)
        System.err.println("[ZedAgent] Starting Java ACP agent...");

        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[ZedAgent] Received initialize request");
                // Advertise our capabilities
                return new InitializeResponse(
                    1,  // protocol version
                    new AgentCapabilities(),  // default capabilities
                    List.of()  // no extensions
                );
            })

            .newSessionHandler(req -> {
                System.err.println("[ZedAgent] Creating session for cwd: " + req.cwd());
                return new NewSessionResponse(
                    UUID.randomUUID().toString(),
                    null,  // no system prompt
                    null   // no extra config
                );
            })

            .promptHandler((req, context) -> {
                // Extract the prompt text
                String promptText = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("");

                System.err.println("[ZedAgent] Processing prompt: " + promptText);

                // Send a "thinking" update first
                context.sendUpdate(req.sessionId(),
                    new AgentThoughtChunk("agent_thought_chunk",
                        new TextContent("Processing your request...")));

                // Generate a response
                String response = generateResponse(promptText);

                // Send the response as a message chunk
                context.sendUpdate(req.sessionId(),
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent(response)));

                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        System.err.println("[ZedAgent] Ready - waiting for Zed to connect...");
        agent.run();
        System.err.println("[ZedAgent] Shutdown.");
    }

    private static String generateResponse(String prompt) {
        String lowerPrompt = prompt.toLowerCase();

        if (lowerPrompt.contains("hello") || lowerPrompt.contains("hi")) {
            return "Hello! I'm a Java ACP agent running in Zed. " +
                   "I was built with the ACP Java SDK. How can I help you?";
        }

        if (lowerPrompt.contains("help")) {
            return """
                I'm a demonstration agent from the ACP Java Tutorial.

                I can:
                - Respond to your questions
                - Show how ACP enables editor integration
                - Demonstrate streaming updates (thoughts + messages)

                This same agent works in Zed, JetBrains IDEs, and any ACP client!
                """;
        }

        if (lowerPrompt.contains("what") && lowerPrompt.contains("acp")) {
            return """
                ACP (Agent Client Protocol) is an open standard for connecting
                AI coding agents to editors and IDEs.

                Think of it like LSP (Language Server Protocol), but for AI agents.

                With ACP, you write your agent once and it works with:
                - Zed
                - JetBrains IDEs (IntelliJ, PyCharm, etc.)
                - VS Code (via community extension)
                - Neovim
                - And more!
                """;
        }

        // Default response
        return "You said: \"" + prompt + "\"\n\n" +
               "I'm a simple demo agent. Try asking me 'help' or 'what is ACP?'";
    }
}
