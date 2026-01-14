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

import java.util.List;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.CreateTerminalRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReleaseTerminalRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TerminalOutputRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.WaitForTerminalExitRequest;

public class TerminalAgent {

    public static void main(String[] args) {
        System.err.println("[TerminalAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[TerminalAgent] Initialize");
                return new InitializeResponse(1, new AgentCapabilities(), List.of());
            })

            .newSessionHandler(req -> {
                String sessionId = UUID.randomUUID().toString();
                System.err.println("[TerminalAgent] New session: " + sessionId);
                return new NewSessionResponse(sessionId, null, null);
            })

            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();
                String text = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("");

                System.err.println("[TerminalAgent] Prompt: " + text);

                // Check if client supports terminal
                NegotiatedCapabilities caps = context.getClientCapabilities();
                if (!caps.supportsTerminal()) {
                    context.sendUpdate(sessionId,
                        new AgentMessageChunk("agent_message_chunk",
                            new TextContent("Terminal not supported by client")));
                    return new PromptResponse(StopReason.END_TURN);
                }

                // Extract command to run (format: "run <command>")
                String command;
                if (text.startsWith("run ")) {
                    command = text.substring(4);
                } else {
                    context.sendUpdate(sessionId,
                        new AgentMessageChunk("agent_message_chunk",
                            new TextContent("Usage: 'run <command>' to execute a shell command")));
                    return new PromptResponse(StopReason.END_TURN);
                }

                StringBuilder response = new StringBuilder();
                response.append("Executing command: ").append(command).append("\n\n");

                try {
                    // Step 1: Create terminal
                    System.err.println("[TerminalAgent] Creating terminal for: " + command);
                    var termResp = context.createTerminal(
                        new CreateTerminalRequest(sessionId, command, List.of(), null, List.of(), null));
                    String terminalId = termResp.terminalId();
                    response.append("Terminal created: ").append(terminalId).append("\n");

                    // Step 2: Wait for exit
                    System.err.println("[TerminalAgent] Waiting for exit...");
                    var exitResp = context.waitForTerminalExit(
                        new WaitForTerminalExitRequest(sessionId, terminalId));
                    response.append("Exit code: ").append(exitResp.exitCode()).append("\n\n");

                    // Step 3: Get output
                    System.err.println("[TerminalAgent] Getting output...");
                    var outputResp = context.getTerminalOutput(
                        new TerminalOutputRequest(sessionId, terminalId));
                    response.append("Output:\n").append(outputResp.output());
                    if (outputResp.truncated()) {
                        response.append("\n[Output truncated]");
                    }

                    // Step 4: Release terminal
                    System.err.println("[TerminalAgent] Releasing terminal...");
                    context.releaseTerminal(
                        new ReleaseTerminalRequest(sessionId, terminalId));
                    response.append("\n\nTerminal released.");

                } catch (Exception e) {
                    response.append("\nError: ").append(e.getMessage());
                    System.err.println("[TerminalAgent] Error: " + e.getMessage());
                }

                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent(response.toString())));

                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        System.err.println("[TerminalAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[TerminalAgent] Shutdown.");
    }
}
