/*
 * Module 18: Terminal Agent
 *
 * An agent that executes commands on the client via terminal API.
 *
 * Key concepts:
 * - context.createTerminal() - request terminal creation
 * - context.terminalOutput() - get command output
 * - context.waitForTerminalExit() - wait for command to finish
 * - context.releaseTerminal() - clean up terminal
 *
 * Build & run:
 *   ./mvnw package -pl module-18-terminal-operations -q
 *   ./mvnw exec:java -pl module-18-terminal-operations
 */
package com.acptutorial.module18;

import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.Command;
import com.agentclientprotocol.sdk.agent.CommandResult;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class TerminalAgent {

    public static void main(String[] args) {
        System.err.println("[TerminalAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[TerminalAgent] Initialize");
                return InitializeResponse.ok();
            })

            .newSessionHandler(req -> {
                String sessionId = UUID.randomUUID().toString();
                System.err.println("[TerminalAgent] New session: " + sessionId);
                return new NewSessionResponse(sessionId, null, null);
            })

            .promptHandler((req, context) -> {
                String text = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("");

                System.err.println("[TerminalAgent] Prompt: " + text);

                // Check if client supports terminal
                NegotiatedCapabilities caps = context.getClientCapabilities();
                if (!caps.supportsTerminal()) {
                    context.sendMessage("Terminal not supported by client");
                    return PromptResponse.endTurn();
                }

                // Extract command to run (format: "run <command>")
                String command;
                if (text.startsWith("run ")) {
                    command = text.substring(4);
                } else {
                    context.sendMessage("Usage: 'run <command>' to execute a shell command");
                    return PromptResponse.endTurn();
                }

                StringBuilder response = new StringBuilder();
                response.append("Executing command: ").append(command).append("\n\n");

                try {
                    // Use convenience method - handles create, wait, output, release automatically!
                    System.err.println("[TerminalAgent] Executing: " + command);
                    CommandResult result = context.execute(command);

                    response.append("Exit code: ").append(result.exitCode()).append("\n");
                    if (result.timedOut()) {
                        response.append("Command timed out!\n");
                    }
                    response.append("\nOutput:\n").append(result.output());

                    if (result.success()) {
                        response.append("\n\nCommand completed successfully.");
                    } else {
                        response.append("\n\nCommand failed.");
                    }

                } catch (Exception e) {
                    response.append("\nError: ").append(e.getMessage());
                    System.err.println("[TerminalAgent] Error: " + e.getMessage());
                }

                context.sendMessage(response.toString());

                return PromptResponse.endTurn();
            })
            .build();

        System.err.println("[TerminalAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[TerminalAgent] Shutdown.");
    }
}
