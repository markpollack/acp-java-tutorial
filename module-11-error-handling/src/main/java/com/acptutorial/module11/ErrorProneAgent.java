/*
 * Module 11: Error-Prone Agent
 *
 * An agent that demonstrates various protocol errors.
 *
 * Key concepts:
 * - Throwing AcpProtocolException from handlers
 * - Standard error codes (AcpErrorCodes)
 * - Error propagation to client
 *
 * Build & run:
 *   ./mvnw package -pl module-11-error-handling -q
 *   ./mvnw exec:java -pl module-11-error-handling
 */
package com.acptutorial.module11;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.error.AcpProtocolException;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentCapabilities;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.StopReason;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class ErrorProneAgent {

    private static final Map<String, Boolean> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.err.println("[ErrorProneAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[ErrorProneAgent] Initialize");
                return new InitializeResponse(1, new AgentCapabilities(), List.of());
            })

            .newSessionHandler(req -> {
                String sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, true);
                System.err.println("[ErrorProneAgent] New session: " + sessionId);
                return new NewSessionResponse(sessionId, null, null);
            })

            .loadSessionHandler(req -> {
                String sessionId = req.sessionId();
                System.err.println("[ErrorProneAgent] Load session: " + sessionId);

                // Demonstrate SESSION_NOT_FOUND error
                if (!sessions.containsKey(sessionId)) {
                    System.err.println("[ErrorProneAgent] Throwing SESSION_NOT_FOUND");
                    throw new AcpProtocolException(
                        AcpErrorCodes.SESSION_NOT_FOUND,
                        "Session not found: " + sessionId);
                }

                return new com.agentclientprotocol.sdk.spec.AcpSchema.LoadSessionResponse(null, null);
            })

            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();
                String text = req.prompt().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse("");

                System.err.println("[ErrorProneAgent] Prompt: " + text);

                // Demonstrate different errors based on prompt content
                if (text.contains("invalid")) {
                    System.err.println("[ErrorProneAgent] Throwing INVALID_PARAMS");
                    throw new AcpProtocolException(
                        AcpErrorCodes.INVALID_PARAMS,
                        "Invalid parameter in prompt: '" + text + "'");
                }

                if (text.contains("internal")) {
                    System.err.println("[ErrorProneAgent] Throwing INTERNAL_ERROR");
                    throw new AcpProtocolException(
                        AcpErrorCodes.INTERNAL_ERROR,
                        "Simulated internal error");
                }

                if (text.contains("permission")) {
                    System.err.println("[ErrorProneAgent] Throwing PERMISSION_DENIED");
                    throw new AcpProtocolException(
                        AcpErrorCodes.PERMISSION_DENIED,
                        "Permission denied for this operation");
                }

                // Normal response
                context.sendUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                        new TextContent("Success! Processed: " + text)));

                return new PromptResponse(StopReason.END_TURN);
            })
            .build();

        System.err.println("[ErrorProneAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[ErrorProneAgent] Shutdown.");
    }
}
