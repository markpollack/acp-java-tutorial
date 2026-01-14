/*
 * Module 18: Terminal Agent
 *
 * An agent that executes commands on the client via terminal API.
 *
 * Key concepts:
 * - context.createTerminal() - request terminal creation
 * - context.getTerminalOutput() - get command output
 * - context.waitForTerminalExit() - wait for command to finish
 * - context.releaseTerminal() - clean up terminal
 *
 * Note: This module uses the low-level terminal API to demonstrate
 * each step of the terminal lifecycle. The SDK also provides
 * context.execute() as a convenience method.
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
import com.agentclientprotocol.sdk.spec.AcpSchema.CreateTerminalRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.ReleaseTerminalRequest;
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

                String terminalId = null;
                try {
                    // Step 1: Create terminal
                    System.err.println("[TerminalAgent] Creating terminal for: " + command);
                    var createResp = context.createTerminal(
                        new CreateTerminalRequest(
                            context.getSessionId(),
                            "sh",                           // executable
                            List.of("-c", command),         // args
                            null,                           // cwd (use client default)
                            null,                           // env
                            null                            // outputByteLimit
                        ));
                    terminalId = createResp.terminalId();
                    System.err.println("[TerminalAgent] Terminal created: " + terminalId);

                    // Step 2: Wait for command to finish
                    System.err.println("[TerminalAgent] Waiting for exit...");
                    var exitResp = context.waitForTerminalExit(
                        new WaitForTerminalExitRequest(context.getSessionId(), terminalId));
                    int exitCode = exitResp.exitCode();
                    System.err.println("[TerminalAgent] Exit code: " + exitCode);

                    // Step 3: Get output
                    System.err.println("[TerminalAgent] Getting output...");
                    var outputResp = context.getTerminalOutput(
                        new TerminalOutputRequest(context.getSessionId(), terminalId));
                    String output = outputResp.output();

                    response.append("Exit code: ").append(exitCode).append("\n");
                    response.append("\nOutput:\n").append(output);

                    if (exitCode == 0) {
                        response.append("\n\nCommand completed successfully.");
                    } else {
                        response.append("\n\nCommand failed.");
                    }

                } catch (Exception e) {
                    response.append("\nError: ").append(e.getMessage());
                    System.err.println("[TerminalAgent] Error: " + e.getMessage());
                } finally {
                    // Step 4: Always release the terminal
                    if (terminalId != null) {
                        try {
                            System.err.println("[TerminalAgent] Releasing terminal: " + terminalId);
                            context.releaseTerminal(
                                new ReleaseTerminalRequest(context.getSessionId(), terminalId));
                            System.err.println("[TerminalAgent] Terminal released");
                        } catch (Exception e) {
                            System.err.println("[TerminalAgent] Failed to release terminal: " + e.getMessage());
                        }
                    }
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
