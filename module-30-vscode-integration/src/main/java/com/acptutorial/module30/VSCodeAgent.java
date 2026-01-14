/*
 * Module 30: VS Code Integration
 *
 * This module demonstrates how to connect a Java ACP agent to VS Code
 * using the community vscode-acp extension.
 *
 * VS Code doesn't have native ACP support yet (see Issue #265496), but
 * the community extension by omercnet provides ACP functionality.
 *
 * Key concept: The SAME agent code works in Zed, JetBrains, and VS Code.
 * Only the configuration differs!
 *
 * Build:
 *   ./mvnw package -pl module-30-vscode-integration -q
 *
 * Create wrapper script and add to PATH (see README.md for details)
 */
package com.acptutorial.module30;

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

public class VSCodeAgent {

    public static void main(String[] args) {
        System.err.println("[VSCodeAgent] Starting Java ACP agent...");

        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[VSCodeAgent] Initialize request received");
                return new InitializeResponse(1, new AgentCapabilities(), List.of());
            })

            .newSessionHandler(req -> {
                System.err.println("[VSCodeAgent] Creating session for: " + req.cwd());
                return new NewSessionResponse(UUID.randomUUID().toString(), null, null);
            })

            .promptHandler((req, context) -> {
                String promptText = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("");

                System.err.println("[VSCodeAgent] Processing: " + promptText);

                context.sendUpdate(req.sessionId(),
                    new AgentThoughtChunk("agent_thought_chunk",
                        new TextContent("Processing in VS Code...")));

                String response = generateResponse(promptText);
                context.sendUpdate(req.sessionId(),
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent(response)));

                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        System.err.println("[VSCodeAgent] Ready - waiting for VS Code to connect...");
        agent.run();
        System.err.println("[VSCodeAgent] Shutdown.");
    }

    private static String generateResponse(String prompt) {
        String lowerPrompt = prompt.toLowerCase();

        if (lowerPrompt.contains("hello") || lowerPrompt.contains("hi")) {
            return "Hello from VS Code! I'm a Java ACP agent using the " +
                   "community vscode-acp extension.";
        }

        if (lowerPrompt.contains("help")) {
            return """
                I'm a Java ACP agent from the ACP Tutorial.

                I'm running in VS Code via the vscode-acp extension.
                The same agent code works in Zed and JetBrains too!

                Native VS Code ACP support is being discussed:
                https://github.com/microsoft/vscode/issues/265496
                """;
        }

        if (lowerPrompt.contains("vscode") || lowerPrompt.contains("vs code")) {
            return """
                VS Code ACP support status:

                Native: Under discussion (Issue #265496)
                Community: vscode-acp extension by omercnet

                The community extension provides:
                - Multi-agent support
                - Native chat interface
                - Tool visibility
                - Real-time streaming

                Install: Search "VSCode ACP" in Extensions
                """;
        }

        return "You said: \"" + prompt + "\"\n\n" +
               "Try 'help' or ask about 'VS Code'.";
    }
}
