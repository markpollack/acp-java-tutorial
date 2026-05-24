/*
 * Module 20: Session Manager Agent
 *
 * An agent that demonstrates the full session lifecycle:
 * - session/new    - create sessions with unique IDs
 * - session/list   - enumerate active sessions (with optional cwd filter)
 * - session/resume - reconnect to a session without replaying history
 * - session/close  - close a session and free resources
 * - session/cancel - cancel in-flight work (notification)
 *
 * Key concepts:
 * - listSessionsHandler()  - returns SessionInfo objects with metadata
 * - closeSessionHandler()  - cleans up session state
 * - resumeSessionHandler() - lightweight reconnect (no history replay)
 * - Difference between loadSession (replays history) and resumeSession (no replay)
 *
 * Build & run:
 *   ./mvnw package -pl module-20-session-management -q
 *   ./mvnw exec:java -pl module-20-session-management
 */
package com.acptutorial.module20;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.CloseSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.InitializeResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.ListSessionsResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.ResumeSessionResponse;
import com.agentclientprotocol.sdk.spec.AcpSchema.SessionInfo;

public class SessionManagerAgent {

    // Session state
    private static final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    // Tracks per-session metadata and message history
    private record SessionState(String cwd, String title, Instant createdAt,
            List<String> messages) {
    }

    public static void main(String[] args) {
        System.err.println("[SessionManager] Starting...");
        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent agent = AcpAgent.sync(transport)
            .initializeHandler(req -> {
                System.err.println("[SessionManager] Initialize");
                return InitializeResponse.ok();
            })

            // Create a new session
            .newSessionHandler(req -> {
                String sessionId = "sess-" + UUID.randomUUID().toString().substring(0, 8);
                sessions.put(sessionId, new SessionState(
                    req.cwd(), null, Instant.now(), new ArrayList<>()));
                System.err.println("[SessionManager] New session: " + sessionId);
                return new NewSessionResponse(sessionId, null, null);
            })

            // List all sessions, optionally filtered by cwd
            .listSessionsHandler(req -> {
                System.err.println("[SessionManager] List sessions (cwd filter: " + req.cwd() + ")");

                List<SessionInfo> infos = new ArrayList<>();
                for (var entry : sessions.entrySet()) {
                    SessionState state = entry.getValue();

                    // Apply cwd filter if provided
                    if (req.cwd() != null && !req.cwd().equals(state.cwd())) {
                        continue;
                    }

                    infos.add(new SessionInfo(
                        entry.getKey(),
                        state.cwd(),
                        state.title() != null ? state.title()
                            : "Session with " + state.messages().size() + " messages",
                        state.createdAt().toString(),
                        null));
                }

                System.err.println("[SessionManager] Returning " + infos.size() + " sessions");
                return new ListSessionsResponse(infos);
            })

            // Resume a session without replaying history
            .resumeSessionHandler(req -> {
                String sessionId = req.sessionId();
                System.err.println("[SessionManager] Resume session: " + sessionId);

                if (!sessions.containsKey(sessionId)) {
                    // Create fresh state for unknown sessions
                    sessions.put(sessionId, new SessionState(
                        req.cwd(), null, Instant.now(), new ArrayList<>()));
                    System.err.println("[SessionManager] Unknown session, created fresh state");
                } else {
                    System.err.println("[SessionManager] Session found, " +
                        sessions.get(sessionId).messages().size() + " messages in history");
                }

                // Unlike loadSession, resumeSession does NOT replay history
                // The client reconnects but no session/update notifications are sent
                return new ResumeSessionResponse(null, null);
            })

            // Close a session and free resources
            .closeSessionHandler(req -> {
                String sessionId = req.sessionId();
                System.err.println("[SessionManager] Close session: " + sessionId);

                SessionState removed = sessions.remove(sessionId);
                if (removed != null) {
                    System.err.println("[SessionManager] Cleaned up session with " +
                        removed.messages().size() + " messages");
                } else {
                    System.err.println("[SessionManager] Session not found (already closed?)");
                }

                return new CloseSessionResponse();
            })

            // Handle prompts
            .promptHandler((req, context) -> {
                String sessionId = req.sessionId();
                String text = req.text();

                SessionState state = sessions.get(sessionId);
                if (state != null) {
                    state.messages().add(text);
                }

                int msgCount = state != null ? state.messages().size() : 0;

                context.sendMessage(
                    "Session " + sessionId + " | Message #" + msgCount + "\n" +
                    "Active sessions: " + sessions.size() + "\n" +
                    "You said: " + text + "\n");

                return PromptResponse.endTurn();
            })

            // Handle cancel
            .cancelHandler(notification -> {
                System.err.println("[SessionManager] Cancel: " + notification.sessionId());
            })
            .build();

        System.err.println("[SessionManager] Ready");
        agent.run();
    }

}
