/*
 * Module 13: Agent Handlers - The Agent
 *
 * Complete coverage of all handler types available to ACP agents.
 *
 * Key APIs exercised:
 * - initializeHandler() - connection handshake
 * - newSessionHandler() - session creation
 * - promptHandler() - main conversation handler
 * - loadSessionHandler() - session resume (optional)
 * - cancelHandler() - cancellation handling (optional)
 *
 * This agent demonstrates all handler types with detailed logging.
 *
 * Build & run:
 *   ./mvnw package -pl module-13-agent-handlers -q
 *   ./mvnw exec:java -pl module-13-agent-handlers
 */
package com.acptutorial.module13;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.LoadSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class HandlerAgent {

    // Track sessions for loadSession demonstration
    private static final Map<String, String> sessions = new HashMap<>();

    public static void main(String[] args) {
        System.err.println("[HandlerAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            // Handler 1: Initialize - First handler called, establishes connection
            .initializeHandler(req -> {
                System.err.println("[HandlerAgent] initializeHandler called");
                System.err.println("  Protocol version: " + req.protocolVersion());
                System.err.println("  Client capabilities: " + req.clientCapabilities());

                // Return our capabilities
                return new InitializeResponse(
                    1,  // Protocol version
                    new AgentCapabilities(),  // Our capabilities
                    List.of()  // Extensions
                );
            })

            // Handler 2: New Session - Creates a new conversation workspace
            .newSessionHandler(req -> {
                System.err.println("[HandlerAgent] newSessionHandler called");
                System.err.println("  Working directory: " + req.cwd());
                System.err.println("  MCP servers: " + req.mcpServers().size());

                String sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, req.cwd());
                System.err.println("  Created session: " + sessionId);

                return new NewSessionResponse(sessionId, null, null);
            })

            // Handler 3: Load Session - Resumes an existing session
            .loadSessionHandler(req -> {
                System.err.println("[HandlerAgent] loadSessionHandler called");
                System.err.println("  Session ID: " + req.sessionId());

                if (sessions.containsKey(req.sessionId())) {
                    System.err.println("  Session found, loading...");
                    // LoadSessionResponse takes mode state and model state (both optional)
                    return new LoadSessionResponse(null, null);
                } else {
                    System.err.println("  Session not found");
                    return new LoadSessionResponse(null, null);
                }
            })

            // Handler 4: Prompt - Main conversation handler
            .promptHandler((req, context) -> {
                System.err.println("[HandlerAgent] promptHandler called");
                System.err.println("  Session ID: " + req.sessionId());
                System.err.println("  Prompt items: " + req.prompt().size());

                // Extract the prompt text
                String promptText = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("(no text)");
                System.err.println("  Prompt text: " + promptText);

                // Send a response
                context.sendUpdate(req.sessionId(),
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("HandlerAgent received: " + promptText)));

                // Describe our handler types
                context.sendUpdate(req.sessionId(),
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("\n\nThis agent demonstrates:\n" +
                            "- initializeHandler: " + sessions.size() + " session(s) created\n" +
                            "- newSessionHandler: creates unique session IDs\n" +
                            "- loadSessionHandler: can resume sessions\n" +
                            "- promptHandler: processes prompts\n" +
                            "- cancelHandler: handles cancellation (not shown)")));

                return new PromptResponse(StopReason.END_TURN);
            })

            // Handler 5: Cancel - Handles cancellation requests
            .cancelHandler(req -> {
                System.err.println("[HandlerAgent] cancelHandler called");
                System.err.println("  Session ID: " + req.sessionId());
                System.err.println("  Cancellation acknowledged");
                // In a real agent, you would stop any in-progress work here
            })

            .build();

        System.err.println("[HandlerAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[HandlerAgent] Shutdown.");
    }
}
