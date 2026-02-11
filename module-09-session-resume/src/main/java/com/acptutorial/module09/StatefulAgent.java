/*
 * Module 09: Stateful Agent
 *
 * An agent that maintains session state and supports session resume.
 *
 * Key concepts:
 * - loadSessionHandler() - handles session resume requests
 * - Session state persistence (in-memory for demo)
 * - Continuing conversation from previous state
 *
 * Build & run:
 *   ./mvnw package -pl module-09-session-resume -q
 *   ./mvnw exec:java -pl module-09-session-resume
 */
package com.acptutorial.module09;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.LoadSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;


public class StatefulAgent {

    // Session state: tracks message history per session
    private static final Map<String, List<String>> sessionHistory = new ConcurrentHashMap<>();
    private static final Map<String, String> sessionCwd = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.err.println("[StatefulAgent] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[StatefulAgent] Initialize");
                return InitializeResponse.ok();
            })

            // Create new session with fresh state
            .newSessionHandler(req -> {
                String sessionId = UUID.randomUUID().toString();
                sessionHistory.put(sessionId, new ArrayList<>());
                sessionCwd.put(sessionId, req.cwd());
                System.err.println("[StatefulAgent] New session: " + sessionId);
                System.err.println("[StatefulAgent] Working directory: " + req.cwd());
                return new NewSessionResponse(sessionId, null, null);
            })

            // Load existing session - resume from previous state
            .loadSessionHandler(req -> {
                String sessionId = req.sessionId();
                System.err.println("[StatefulAgent] Load session request: " + sessionId);

                if (sessionHistory.containsKey(sessionId)) {
                    System.err.println("[StatefulAgent] Session found! History size: " +
                        sessionHistory.get(sessionId).size());
                    // Session exists - can resume
                    return new LoadSessionResponse(null, null);
                } else {
                    System.err.println("[StatefulAgent] Session not found, creating fresh state");
                    // Session doesn't exist - create fresh state
                    sessionHistory.put(sessionId, new ArrayList<>());
                    sessionCwd.put(sessionId, req.cwd() != null ? req.cwd() : ".");
                    return new LoadSessionResponse(null, null);
                }
            })

            // Process prompts and track history
            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();

                String text = req.text();

                // Add to history
                List<String> history = sessionHistory.get(sessionId);
                if (history != null) {
                    history.add(text);
                }

                System.err.println("[StatefulAgent] Prompt: " + text);
                System.err.println("[StatefulAgent] History size: " +
                    (history != null ? history.size() : 0));

                // Build response showing session state
                StringBuilder response = new StringBuilder();
                response.append("Session: ").append(sessionId.substring(0, 8)).append("...\n");
                response.append("Message #").append(history != null ? history.size() : 0).append("\n");
                response.append("You said: ").append(text).append("\n");

                if (history != null && history.size() > 1) {
                    response.append("\nPrevious messages in this session:\n");
                    for (int i = 0; i < history.size() - 1; i++) {
                        response.append("  ").append(i + 1).append(". ").append(history.get(i)).append("\n");
                    }
                }

                context.sendMessage(response.toString());

                return PromptResponse.endTurn();
            })
            .build();

        System.err.println("[StatefulAgent] Ready, waiting for messages...");
        agent.run();
        System.err.println("[StatefulAgent] Shutdown.");
    }
}
