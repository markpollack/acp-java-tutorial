/*
 * Module 03: Sessions
 *
 * Understanding session creation and lifecycle in ACP.
 *
 * Key APIs exercised:
 * - NewSessionRequest - working directory, context documents
 * - NewSessionResponse - session ID, initial state
 * - Multiple sessions - each session is independent
 *
 * Sessions are workspaces for conversations. Each session:
 * - Has a unique ID
 * - Has a working directory context
 * - Can have context documents attached
 * - Is independent of other sessions
 *
 * Build & run:
 *   ./mvnw exec:java -pl module-03-sessions
 */
package com.acptutorial.module03;

import java.util.List;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

public class SessionsClient {

    public static void main(String[] args) {
        checkGeminiApiKey();

        var params = AgentParameters.builder("gemini")
            .arg("--experimental-acp")
            .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport).build()) {

            System.out.println("=== Module 03: Sessions ===\n");

            client.initialize();
            System.out.println("Connected to agent\n");

            // Session 1: Simple session with just a working directory
            String cwd = System.getProperty("user.dir");
            System.out.println("Creating Session 1 (simple):");
            System.out.println("  Working directory: " + cwd);

            var session1 = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("  Session ID: " + session1.sessionId());
            System.out.println();

            // Session 2: Another session (same working directory, different ID)
            System.out.println("Creating Session 2:");
            System.out.println("  Working directory: " + cwd);

            var session2 = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("  Session ID: " + session2.sessionId());
            System.out.println();

            // Demonstrate session independence
            System.out.println("Session independence demo:");
            System.out.println("  Sessions have different IDs: " +
                !session1.sessionId().equals(session2.sessionId()));

            // Each session maintains its own conversation state
            var response1 = client.prompt(new PromptRequest(
                session1.sessionId(),
                List.of(new TextContent("Remember: my favorite number is 42"))));
            System.out.println("  Session 1 prompt sent (stop: " + response1.stopReason() + ")");

            var response2 = client.prompt(new PromptRequest(
                session2.sessionId(),
                List.of(new TextContent("Remember: my favorite color is blue"))));
            System.out.println("  Session 2 prompt sent (stop: " + response2.stopReason() + ")");

            System.out.println("\nSessions are independent workspaces!");
            System.out.println("Each maintains its own conversation history and context.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkGeminiApiKey() {
        if (System.getenv("GEMINI_API_KEY") == null) {
            System.err.println("ERROR: GEMINI_API_KEY not set. See module-01 for setup instructions.");
            System.exit(1);
        }
    }
}
