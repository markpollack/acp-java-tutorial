/*
 * Module 17: Capability Agent
 *
 * An agent that demonstrates capability negotiation.
 *
 * Key concepts:
 * - Advertising agent capabilities via AgentCapabilities
 * - Checking client capabilities via NegotiatedCapabilities
 * - Graceful degradation when capabilities aren't supported
 *
 * Build & run:
 *   ./mvnw package -pl module-17-capability-negotiation -q
 *   ./mvnw exec:java -pl module-17-capability-negotiation
 */
package com.acptutorial.module17;

import java.util.List;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.capabilities.NegotiatedCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;


public class CapabilityAgent {

    public static void main(String[] args) {
        System.err.println("[CapabilityAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[CapabilityAgent] Initialize");

                // Report client capabilities
                var clientCaps = req.clientCapabilities();
                System.err.println("[CapabilityAgent] Client capabilities:");
                if (clientCaps != null && clientCaps.fs() != null) {
                    System.err.println("  - fs.readTextFile: " + clientCaps.fs().readTextFile());
                    System.err.println("  - fs.writeTextFile: " + clientCaps.fs().writeTextFile());
                }
                System.err.println("  - terminal: " + (clientCaps != null ? clientCaps.terminal() : false));

                // Advertise our capabilities
                var agentCaps = new AgentCapabilities(
                    true,  // loadSession - we support session resume
                    new McpCapabilities(false, false),  // no MCP support
                    new PromptCapabilities(false, false, true)  // only embeddedContext
                );

                return InitializeResponse.ok(agentCaps);
            })

            .newSessionHandler(req -> {
                String sessionId = UUID.randomUUID().toString();
                System.err.println("[CapabilityAgent] New session: " + sessionId);
                return new NewSessionResponse(sessionId, null, null);
            })

            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();
                String text = req.text();

                System.err.println("[CapabilityAgent] Prompt: " + text);

                // Check client capabilities and respond accordingly
                NegotiatedCapabilities clientCaps = context.getClientCapabilities();
                StringBuilder response = new StringBuilder();

                if (text.contains("check")) {
                    response.append("Checking client capabilities:\n");
                    response.append("  - readTextFile: ").append(clientCaps.supportsReadTextFile()).append("\n");
                    response.append("  - writeTextFile: ").append(clientCaps.supportsWriteTextFile()).append("\n");
                    response.append("  - terminal: ").append(clientCaps.supportsTerminal()).append("\n");
                }
                else if (text.contains("read")) {
                    // Attempt file read only if capability is supported
                    if (clientCaps.supportsReadTextFile()) {
                        try {
                            // Using convenience method
                            String content = context.readFile("/etc/hostname");
                            response.append("File read successful! Content: ").append(content.trim());
                        } catch (Exception e) {
                            response.append("File read failed: ").append(e.getMessage());
                        }
                    } else {
                        response.append("Cannot read files - client doesn't support fs.readTextFile capability");
                    }
                }
                else if (text.contains("write")) {
                    // Attempt file write only if capability is supported
                    if (clientCaps.supportsWriteTextFile()) {
                        response.append("File write capability available - would write if path provided");
                    } else {
                        response.append("Cannot write files - client doesn't support fs.writeTextFile capability");
                    }
                }
                else if (text.contains("terminal")) {
                    // Check terminal capability
                    if (clientCaps.supportsTerminal()) {
                        response.append("Terminal capability available - would execute command if provided");
                    } else {
                        response.append("Cannot execute commands - client doesn't support terminal capability");
                    }
                }
                else {
                    response.append("Commands: 'check' (show capabilities), 'read' (test file read), ");
                    response.append("'write' (test file write), 'terminal' (test terminal)");
                }

                // Using convenience method
                context.sendMessage(response.toString());

                return PromptResponse.endTurn();
            })

            .loadSessionHandler(req -> {
                System.err.println("[CapabilityAgent] Load session: " + req.sessionId());
                return new com.agentclientprotocol.sdk.spec.AcpSchema.LoadSessionResponse(null, null);
            })
            .build();

        System.err.println("[CapabilityAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[CapabilityAgent] Shutdown.");
    }
}
