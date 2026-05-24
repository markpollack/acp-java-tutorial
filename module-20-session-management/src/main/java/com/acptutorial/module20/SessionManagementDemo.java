/*
 * Module 20: Session Management Demo
 *
 * Demonstrates the full session lifecycle with three new APIs:
 *   - session/list   - enumerate active sessions
 *   - session/resume - reconnect without history replay
 *   - session/close  - close a session and free resources
 *
 * Key APIs:
 *   client.listSessions(ListSessionsRequest)   -> ListSessionsResponse (paginated)
 *   client.resumeSession(ResumeSessionRequest)  -> ResumeSessionResponse
 *   client.closeSession(CloseSessionRequest)    -> CloseSessionResponse
 *
 * Contrasts session/resume with session/load (module 09):
 *   - loadSession replays conversation history via session/update notifications
 *   - resumeSession reconnects silently — no history replay
 *
 * Build & run:
 *   ./mvnw package -pl module-20-session-management -q
 *   ./mvnw exec:java -pl module-20-session-management
 */
package com.acptutorial.module20;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.CloseSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ListSessionsRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.ResumeSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class SessionManagementDemo {

    private static final String MODULE_NAME = "module-20-session-management";
    private static final String JAR_NAME = "session-manager-agent.jar";

    public static void main(String[] args) {
        var params = AgentParameters.builder("java")
            .arg("-jar")
            .arg(findAgentJar())
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        String text = ((TextContent) msg.content()).text();
                        System.out.print(text);
                    }
                })
                .build()) {

            System.out.println("=== Module 20: Session Management ===\n");

            client.initialize();
            System.out.println("Connected to SessionManagerAgent\n");

            String cwd = System.getProperty("user.dir");

            // ---- Part 1: Create multiple sessions ----
            System.out.println("--- Part 1: Create Sessions ---");

            var session1 = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Created session 1: " + session1.sessionId());

            var session2 = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Created session 2: " + session2.sessionId());

            var session3 = client.newSession(new NewSessionRequest("/tmp", List.of()));
            System.out.println("Created session 3: " + session3.sessionId() + " (cwd: /tmp)");

            // Send messages to build some history
            client.prompt(new PromptRequest(session1.sessionId(),
                List.of(new TextContent("Hello from session 1"))));
            client.prompt(new PromptRequest(session1.sessionId(),
                List.of(new TextContent("Second message in session 1"))));
            client.prompt(new PromptRequest(session2.sessionId(),
                List.of(new TextContent("Hello from session 2"))));

            System.out.println();

            // ---- Part 2: List all sessions ----
            System.out.println("--- Part 2: List Sessions ---");

            var allSessions = client.listSessions(new ListSessionsRequest(null));
            System.out.println("All sessions (" + allSessions.sessions().size() + "):");
            for (var info : allSessions.sessions()) {
                System.out.println("  " + info.sessionId() +
                    " | cwd: " + info.cwd() +
                    " | " + info.title() +
                    " | updated: " + info.updatedAt());
            }
            System.out.println();

            // List with cwd filter
            var filteredSessions = client.listSessions(new ListSessionsRequest(cwd));
            System.out.println("Sessions in " + cwd + ": " + filteredSessions.sessions().size());

            var tmpSessions = client.listSessions(new ListSessionsRequest("/tmp"));
            System.out.println("Sessions in /tmp: " + tmpSessions.sessions().size());
            System.out.println();

            // ---- Part 3: Resume a session (no history replay) ----
            System.out.println("--- Part 3: Resume Session ---");
            System.out.println("Resuming session 1: " + session1.sessionId());

            // resumeSession reconnects without replaying history.
            // Compare with loadSession (module 09) which replays previous messages.
            var resumed = client.resumeSession(new ResumeSessionRequest(
                session1.sessionId(), cwd, List.of()));
            System.out.println("Session resumed (modes: " + resumed.modes() +
                ", models: " + resumed.models() + ")");

            // Continue the conversation — session state is still there
            System.out.println("\nSending message after resume...");
            client.prompt(new PromptRequest(session1.sessionId(),
                List.of(new TextContent("Message after resuming!"))));
            System.out.println();

            // ---- Part 4: Close a session ----
            System.out.println("--- Part 4: Close Session ---");
            System.out.println("Closing session 3: " + session3.sessionId());

            client.closeSession(new CloseSessionRequest(session3.sessionId()));
            System.out.println("Session closed.\n");

            // Verify it's gone
            var afterClose = client.listSessions(new ListSessionsRequest(null));
            System.out.println("Sessions after close: " + afterClose.sessions().size() +
                " (was " + allSessions.sessions().size() + ")");
            System.out.println();

            // ---- Part 5: Close remaining sessions ----
            System.out.println("--- Part 5: Cleanup ---");
            client.closeSession(new CloseSessionRequest(session1.sessionId()));
            System.out.println("Closed session 1");
            client.closeSession(new CloseSessionRequest(session2.sessionId()));
            System.out.println("Closed session 2");

            var empty = client.listSessions(new ListSessionsRequest(null));
            System.out.println("Sessions remaining: " + empty.sessions().size());

            System.out.println("\n=== Demo Complete ===");
            System.out.println("New APIs demonstrated:");
            System.out.println("  session/list   - enumerate sessions with optional cwd filter");
            System.out.println("  session/resume - reconnect without replaying history");
            System.out.println("  session/close  - close session and free resources");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String findAgentJar() {
        Path fromModule = Path.of("target/" + JAR_NAME);
        if (Files.exists(fromModule)) {
            return fromModule.toString();
        }
        Path fromRoot = Path.of(MODULE_NAME + "/target/" + JAR_NAME);
        if (Files.exists(fromRoot)) {
            return fromRoot.toString();
        }
        throw new RuntimeException(
            "Agent JAR not found. Run: ./mvnw package -pl " + MODULE_NAME + " -q");
    }

}
