/*
 * Module 11: Error-Prone Agent
 *
 * An agent that demonstrates various protocol errors.
 *
 * Key concepts:
 * - Throwing AcpProtocolException from handlers
 * - Standard error codes (AcpErrorCodes): only the codes the ACP v1 schema defines
 * - Error propagation to client
 *
 * Build & run:
 *   ./mvnw package -pl module-11-error-handling -q
 *   ./mvnw exec:java -pl module-11-error-handling
 */
package com.acptutorial.module11;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.error.AcpProtocolException;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;


public class ErrorProneAgent {

    private static final Map<String, Boolean> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.err.println("[ErrorProneAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[ErrorProneAgent] Initialize");
                return InitializeResponse.ok();
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

                // Demonstrate RESOURCE_NOT_FOUND (-32002), the ACP code for a missing
                // session, file or other resource
                if (!sessions.containsKey(sessionId)) {
                    System.err.println("[ErrorProneAgent] Throwing RESOURCE_NOT_FOUND");
                    throw new AcpProtocolException(
                        AcpErrorCodes.RESOURCE_NOT_FOUND,
                        "Session not found: " + sessionId);
                }

                return new com.agentclientprotocol.sdk.spec.AcpSchema.LoadSessionResponse(null, null);
            })

            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();
                String text = req.text();

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

                // AUTHENTICATION_REQUIRED (-32000): the client must authenticate
                // (session/authenticate) before the agent will do this work
                if (text.contains("authenticate")) {
                    System.err.println("[ErrorProneAgent] Throwing AUTHENTICATION_REQUIRED");
                    throw new AcpProtocolException(
                        AcpErrorCodes.AUTHENTICATION_REQUIRED,
                        "Authentication required for this operation");
                }

                // Normal response
                context.sendMessage("Success! Processed: " + text);

                return PromptResponse.endTurn();
            })
            .build();

        System.err.println("[ErrorProneAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[ErrorProneAgent] Shutdown.");
    }
}
