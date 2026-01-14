/*
 * Module 10: Cancellable Agent
 *
 * An agent that performs long-running work and responds to cancellation.
 *
 * Key concepts:
 * - cancelHandler() - handles cancel notifications from client
 * - Tracking cancellation state per session
 * - Checking cancellation during long-running operations
 * - Clean shutdown when cancelled
 *
 * Build & run:
 *   ./mvnw package -pl module-10-cancellation -q
 *   ./mvnw exec:java -pl module-10-cancellation
 */
package com.acptutorial.module10;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class CancellableAgent {

    // Track which sessions have been cancelled (thread-safe)
    private static final Map<String, Boolean> cancelledSessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.err.println("[CancellableAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[CancellableAgent] Initialize");
                return new InitializeResponse(1, new AgentCapabilities(), List.of());
            })

            .newSessionHandler(req -> {
                String sessionId = UUID.randomUUID().toString();
                cancelledSessions.put(sessionId, false);
                System.err.println("[CancellableAgent] New session: " + sessionId);
                return new NewSessionResponse(sessionId, null, null);
            })

            // Cancel handler - called when client sends cancel notification
            .cancelHandler(notification -> {
                String sessionId = notification.sessionId();
                System.err.println("[CancellableAgent] CANCEL received for session: " + sessionId);
                cancelledSessions.put(sessionId, true);
                // Note: cancel is a notification, not a request - no response needed
            })

            // Prompt handler - performs slow work, checking for cancellation
            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();
                System.err.println("[CancellableAgent] Starting long operation...");

                // Reset cancellation state for this prompt
                cancelledSessions.put(sessionId, false);

                // Simulate long-running work with progress updates
                for (int i = 1; i <= 10; i++) {
                    // Check if cancelled before each step
                    if (cancelledSessions.getOrDefault(sessionId, false)) {
                        System.err.println("[CancellableAgent] Cancelled at step " + i);
                        context.sendUpdate(sessionId,
                            new AgentMessageChunk("agent_message_chunk",
                                new TextContent("\n[Operation cancelled at step " + i + "]")));
                        return new PromptResponse(StopReason.END_TURN);
                    }

                    // Send progress update
                    context.sendUpdate(sessionId,
                        new AgentMessageChunk("agent_message_chunk",
                            new TextContent("Step " + i + "/10... ")));

                    // Simulate work
                    try {
                        Thread.sleep(500);  // 500ms per step
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new PromptResponse(StopReason.END_TURN);
                    }
                }

                // Completed without cancellation
                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("\nAll steps completed!")));

                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        System.err.println("[CancellableAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[CancellableAgent] Shutdown.");
    }
}
